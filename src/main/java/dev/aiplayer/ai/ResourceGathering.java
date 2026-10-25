package dev.aiplayer.ai;

import dev.aiplayer.entity.CompanionEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.passive.RabbitEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.Comparator;
import java.util.List;

/**
 * Limited, chunk-safe resource tasks. Only loaded trees and non-owned farm animals are targets.
 * Combat/retreat/eating always preempt this controller in CompanionGoal.
 */
public final class ResourceGathering {
    public enum Kind { NONE, WOOD, FOOD, STONE }
    private final CompanionEntity npc;
    private Kind kind=Kind.NONE, lastAutoKind=Kind.NONE;
    private int desired, gathered, lastAction, nextSearch, noProgressTicks, idleCooldown;
    private boolean manual;
    private BlockPos origin, tree;
    private AnimalEntity animal;
    private BlockPos waypoint;
    private int waypointIndex, waypointStarted, huntStarted, nextPreyScan;
    private final java.util.Map<java.util.UUID,Integer> avoidedAnimals=new java.util.HashMap<>();
    private Vec3d lastLocation=Vec3d.ZERO;
    private int attackCooldown, pausedAt=-1;
    private final PhysicalMining mining;
    private final SurfaceStoneGathering stone;
    private final TemporaryPillar pillar;
    private final java.util.List<BlockPos> treeLogs = new java.util.ArrayList<>();
    private boolean cleaning;
    private int recoveryLootUntil;
    private net.minecraft.entity.ItemEntity lootTarget;
    private int lootStarted, nextAutoCheck, nextRoute, nextDropScan;
    private final java.util.Map<java.util.UUID,Integer> avoidedDrops=new java.util.HashMap<>();
    private record DeferredTree(BlockPos base,java.util.List<BlockPos> logs,long retry,long expires) {}
    private final java.util.List<DeferredTree> deferredTrees=new java.util.ArrayList<>();
    private final java.util.Map<BlockPos,Integer> avoidedTrees = new java.util.HashMap<>();

    public ResourceGathering(CompanionEntity npc) {this.npc=npc;mining=new PhysicalMining(npc);stone=new SurfaceStoneGathering(npc);pillar=new TemporaryPillar(npc);}
    public boolean isManual(){return active() && manual;}
    public boolean canStartAutomatic(){return npc.age>=idleCooldown && npc.age>=nextAutoCheck;}
    public void pause(){
        if(pausedAt<0)pausedAt=npc.age;
        mining.reset();stone.pause();npc.clearWorkingVisual();
    }
    public void resume(){
        stone.resume();
        if(pausedAt<0)return;
        int delay=npc.age-pausedAt;
        lastAction+=delay;lootStarted+=delay;huntStarted+=delay;waypointStarted+=delay;
        nextSearch+=delay;nextRoute=0;pausedAt=-1;
    }
    public boolean collectLooseDrops(){return !npc.menuOpen() && npc.autoLoot && seekDrops();}
    public boolean cleanupTick(){
        resume();
        if(!cleaning)return false;
        if(pillar.active()){
            equipAxe();
            recoveryLootUntil=npc.age+400;
            if(pillar.cleanup(mining))return true;
            // Defer rather than monopolize decision arbitration when an
            // obstacle or combat temporarily blocks safe disassembly.
            if(pillar.active())return false;
        }
        // The real blocks drop down the side of a tower. After descending,
        // physically walk to them and let SurvivalController pick them up.
        if(npc.autoLoot && npc.age<recoveryLootUntil && nearbyRecoveredSupports())
            return seekDrops();
        cleaning=false;
        return false;
    }
    private boolean nearbyRecoveredSupports(){
        return !npc.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class,
            npc.getBoundingBox().expand(8),d -> d.isAlive()
                && d.getCommandTags().contains("aiplayer_recovered_support")
                && d.getCommandTags().contains("aiplayer_drop_"+npc.getUuid())
                && npc.backpack.canInsert(d.getStack())).isEmpty();
    }
    private void deferTree(int delay){
        if(tree==null || treeLogs.isEmpty())return;
        deferredTrees.removeIf(t->t.base().equals(tree));
        if(deferredTrees.size()>=8)deferredTrees.remove(0);
        long now=npc.getWorld().getTime();
        deferredTrees.add(new DeferredTree(tree,java.util.List.copyOf(treeLogs),now+delay,now+24000));
        npc.intelligence().memory().failed(tree,now);
    }
    private boolean resumeDeferredTree(){
        long now=npc.getWorld().getTime();
        deferredTrees.removeIf(t->t.expires()<=now);
        var iterator=deferredTrees.iterator();
        while(iterator.hasNext()){
            var task=iterator.next();
            if(task.retry()>now || task.base().getSquaredDistance(npc.getBlockPos())>24*24
                    || !safe(task.base()))continue;
            tree=task.base();treeLogs.clear();treeLogs.addAll(task.logs());iterator.remove();
            treeLogs.removeIf(p->!safe(p) || !npc.getWorld().getBlockState(p).isIn(BlockTags.LOGS)
                || (npc.getWorld() instanceof ServerWorld world && OwnedBlocks.get(world).protectedBlock(p)));
            if(!treeLogs.isEmpty()){nextSearch=0;return true;}
            tree=null;
        }
        return false;
    }
    public void navigationFailed(BlockPos destination){
        if(kind==Kind.STONE)stone.failed();
        if(tree!=null){
            deferTree(400);
            npc.intelligence().memory().failed(tree,npc.getWorld().getTime());
            avoidedTrees.put(tree,npc.age+400);
            tree=null;treeLogs.clear();cleaning=pillar.active();
        }
        waypoint=null;animal=null;nextRoute=0;mining.reset();
    }
    public NbtCompound taskSnapshot(){
        NbtCompound root=new NbtCompound();writeNbt(root);
        NbtCompound task=new NbtCompound();
        if(root.contains("GatherTask"))task.put("GatherTask",root.getCompound("GatherTask").copy());
        return task;
    }
    public void restoreTask(NbtCompound snapshot){
        if(kind==Kind.WOOD && active())deferTree(200);
        NbtCompound root=new NbtCompound();writeNbt(root);
        root.remove("GatherTask");
        if(snapshot.contains("GatherTask"))root.put("GatherTask",snapshot.getCompound("GatherTask").copy());
        readNbt(root);pausedAt=-1;
    }
    /** A committed physical action must finish before voluntary following can interrupt it. */
    public boolean canYieldToOwner() {
        return (!active() || (!manual && !mining.hasProgress() && !stone.committed() && !stone.inExcavation()))
            && !(pillar.active() && pillar.cleanupReady()) && !(cleaning && pillar.cleanupReady());
    }
    /** Lock a planned excavation against opportunistic follow/craft arbitration. */
    public boolean miningStone(){return kind==Kind.STONE && (stone.committed() || stone.inExcavation());}
    public boolean breakingBlock(){return mining.hasProgress() || (kind==Kind.STONE && stone.committed());}
    public boolean onConstructedSupport(){return pillar.isJumping() || (pillar.active() && pillar.owns(npc.getBlockPos().down()));}
    public boolean isNaturalTreeLog(BlockPos pos){
        return kind==Kind.WOOD && treeLogs.contains(pos) && !pillar.owns(pos);
    }
    public String pillarDiagnostics(){return pillar.diagnostics();}
    /** Diagnostics for real stone targeting, retained across executor ticks. */
    public String stoneDiagnostics(BlockPos p){return stone.diagnostics(p)+" scan="+stone.scanDiagnostics()
        +" job="+kind+" "+gathered+"/"+desired+" lastAction="+lastAction+" paused="+pausedAt+" idleCooldown="+idleCooldown;}
    public boolean active() {return kind!=Kind.NONE;}
    public boolean cleanupReady(){
        return cleaning && (pillar.active() ? pillar.cleanupReady()
            : npc.autoLoot && npc.age<recoveryLootUntil && nearbyRecoveredSupports());
    }
    /** An unrecovered material is still a cleanup task until it is picked up or unreachable. */
    public boolean hasCleanup() {return cleanupReady();}
    public Kind kind() {return kind;}
    public int progress() {return gathered;}
    public int goal() {return desired;}
    private void tell(String text) {if(manual && npc.owner()!=null)npc.owner().sendMessage(Text.literal("[AI Player] "+text),false);}
    public void start(Kind task,int count,boolean fromPlayer) {
        if (task==Kind.NONE) {cancel(); return;}
        if(fromPlayer)npc.intelligence().clearSuspended();
        pausedAt=-1;mining.reset();stone.reset();treeLogs.clear();cleaning=pillar.active();lootTarget=null;nextRoute=0;
        searching=false;kind=task; desired=Math.max(1,Math.min(264,count)); gathered=0;
        manual=fromPlayer; if(!fromPlayer)lastAutoKind=task; origin=npc.getBlockPos().toImmutable(); tree=null; animal=null;waypoint=null;waypointIndex=0;
        lastAction=npc.age; nextSearch=0; nextPreyScan=0; noProgressTicks=0;attackCooldown=0;
        npc.getNavigation().stop();
        if(kind==Kind.STONE && !SurfaceStoneGathering.caveAccess(npc,npc.getBlockPos())) {
            tell("Me leve até uma caverna pra eu pegar pedra, meu rei. Quando chegar lá, me peça de novo.");
            finish(false);idleCooldown=npc.age+1200;return;
        }
        tell("Bora, meu rei! Vou buscar "+desired+(kind==Kind.WOOD?" troncos de madeira.":kind==Kind.STONE?" pedregulhos na caverna.":" alimentos."));
    }
    public void cancel() {npc.intelligence().clearSuspended();if(active())tell("Tá certo, parei por aqui ("+gathered+"/"+desired+").");finish(false);}
    private void finish(boolean done) {
        // Keep the coordinates of an unfinished, originally natural canopy for
        // the next order. Cutting its lower trunk may leave upper logs floating.
        if(kind==Kind.WOOD && tree!=null && !treeLogs.isEmpty())deferTree(180);
        if(done)tell("Pronto, meu rei! Consegui "+gathered+"/"+desired+(kind==Kind.WOOD?" troncos.":kind==Kind.STONE?" pedregulhos.":" alimentos."));
        stone.reset();kind=Kind.NONE;tree=null;treeLogs.clear();animal=null;waypoint=null;mining.reset();cleaning=pillar.active();npc.getNavigation().stop();
        if(done)npc.intelligence().memory().succeeded("Coleta verificada: "+gathered+"/"+desired);
        idleCooldown=npc.age+(done?60:160);
    }
    /** Called by the movement goal only when there is no threat and the NPC is healthy. */
    public boolean tick() {
        if (cleanupReady()) {
            if(cleanupTick())return true;
            // Deferred cleanup is NOT a reason to starve a resumable job.
            // The support's coordinates and ownership stay persisted for a later attempt.
        }
        if(!active())return false;
        if(npc.menuOpen() || npc.isEating() || npc.getTarget()!=null)return true;
        if(attackCooldown>0)attackCooldown--;
        if(gathered>=desired){
            // Complete the requested count even if upper natural logs remain.
            // finish() remembers those logs for a future order, rather than
            // forcing an endless climb to strip the entire canopy.
            finish(true);return true;
        }
        if(origin!=null && npc.getBlockPos().getSquaredDistance(origin)>50*50) {
            tell("Fui longe demais da área de coleta, viu? Vou parar essa busca.");finish(false);return true;
        }
        if(npc.age-lastAction>850 && !(pillar.active() && cleaning && pillar.cleanupReady())) {
            tell("Oxe, não tô conseguindo avançar. Vou parar essa tarefa pra não ficar insistindo à toa.");
            finish(false);return true;
        }
        // Commit to one physical block, including leaves cleared to reach a drop.
        // Combat and explicit orders can still pause through the coordinator.
        if(mining.hasProgress()){
            BlockPos committed=mining.target();
            if(mining.reachable(committed)){
                if(mining.tick(committed)){treeLogs.remove(committed);lastAction=npc.age;}
                return true;
            }
            mining.reset();
        }
        // An active staircase/gallery owns its excavation route. Do not let
        // opportunistic drop navigation reset the step timer every few ticks.
        // Natural pickup still runs in SurvivalController as the NPC walks.
        if(kind==Kind.STONE && (stone.committed() || stone.inExcavation())){stoneTick();return true;}
        if(!pillar.active() && seekDrops())return true;
        if(kind==Kind.STONE){stoneTick();return true;}
        if(kind==Kind.WOOD) {
            if(dev.aiplayer.ai.brain.ResourcePlan.inspect(npc).full())return false;
            woodTick();return true;
        }
        return foodTick();
    }
    private void stoneTick(){
        if(!npc.autoLoot){tell("Ative a coleta de drops pra eu recolher os pedregulhos, meu rei.");finish(false);return;}
        switch(stone.tick()){
            case MINED, PROGRESSED -> lastAction=npc.age;
            case NEED_TOOL -> {
                // A manual stone order is NOT fulfilled, or cancelled, merely
                // because the prerequisite pickaxe has not been crafted yet.
                // Keep the requested count so CompanionIntelligence can run the
                // real bench recipe and resume mining with the same order.
                if(npc.autoCraft) {
                    mining.reset();lastAction=npc.age;nextSearch=npc.age+10;
                } else {
                    tell("Preciso de uma picareta em condições, sem Toque Suave, na mão. Ative fabricação/equipamento automático ou me entregue uma, viu?");
                    finish(false);
                }
            }
            case NEED_CAVE -> {
                tell("Me leve até uma caverna pra eu pegar pedra, meu rei. Quando chegar lá, me peça de novo.");
                finish(false);idleCooldown=npc.age+1200;
            }
            case EXHAUSTED -> {tell("Não achei pedra exposta com acesso seguro num raio de 32 blocos, meu rei.");if(!manual)npc.speech().notice(CompanionSpeech.Event.NO_STONE);finish(false);idleCooldown=npc.age+1200;}
            case RULE_BLOCKED -> {tell("As regras do mundo impedem quebrar blocos ou gerar drops, viu?");finish(false);idleCooldown=npc.age+1200;}
            default -> { }
        }
    }
    private int woodInBag() {
        int n=0;
        for(int i=0;i<npc.backpack.size();i++){ItemStack s=npc.backpack.getStack(i);if(s.isIn(ItemTags.LOGS))n+=s.getCount();}
        return n;
    }
    private int planksInBag() {
        int n=0;
        for(int i=0;i<npc.backpack.size();i++){ItemStack s=npc.backpack.getStack(i);if(s.isIn(ItemTags.PLANKS))n+=s.getCount();}
        return n;
    }
    private boolean foodItem(ItemStack item) {
        if(!item.isFood())return false;
        return !item.isOf(Items.ROTTEN_FLESH) && !item.isOf(Items.POISONOUS_POTATO)
            && !item.isOf(Items.SPIDER_EYE) && !item.isOf(Items.PUFFERFISH) && !item.isOf(Items.CHICKEN);
    }
    private int foodInBag() {
        int n=0;
        for(int i=0;i<npc.backpack.size();i++){ItemStack s=npc.backpack.getStack(i);if(foodItem(s))n+=s.getCount();}
        return n;
    }
    private static boolean isSoil(BlockState state) {
        return state.isIn(BlockTags.DIRT) || state.isOf(Blocks.GRASS_BLOCK)
             || state.isOf(Blocks.PODZOL) || state.isOf(Blocks.MYCELIUM);
    }
    private boolean safe(BlockPos p) {
        return npc.getWorld().isChunkLoaded(p) && p.getY()>npc.getWorld().getBottomY()+2
            && p.getY()<npc.getWorld().getTopY()-2;
    }
    private boolean treeBase(BlockPos p) {
        if(npc.getWorld() instanceof ServerWorld world && (OwnedBlocks.get(world).protectedBlock(p) || OwnedBlocks.get(world).claimed(p)))return false;
        if(!safe(p) || !safe(p.up(3)) || !npc.getWorld().getBlockState(p).isIn(BlockTags.LOGS)
                || !isSoil(npc.getWorld().getBlockState(p.down())))return false;
        // A vertical trunk, connected to its own leaf canopy; horizontal logs near leaves do not qualify.
        int height=0;
        while(height<32 && safe(p.up(height)) && npc.getWorld().getBlockState(p.up(height)).isIn(BlockTags.LOGS))height++;
        if(height<1)return false; // Permit a partially felled, rooted trunk with a real canopy.
        for(int dy=-2;dy<=2;dy++)for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++) {
            BlockPos leaf=p.up(height).add(dx,dy,dz);
            if(safe(leaf) && npc.getWorld().getBlockState(leaf).isIn(BlockTags.LEAVES))return true;
        }
        return false;
    }
    /** Revisit a natural tree we actually observed before the lower trunk was cut.
     * A floating crown is not treated as a new tree unless we know its rooted base.
     * This avoids mining arbitrary player builds made of logs and leaves. */
    private BlockPos rememberedRemnant(BlockPos base) {
        if(!safe(base))return null;
        for(int h=1;h<=24;h++)for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
            BlockPos p=base.add(dx,h,dz);
            if(!safe(p) || !npc.getWorld().getBlockState(p).isIn(BlockTags.LOGS))continue;
            if(npc.getWorld() instanceof ServerWorld w &&
                    (OwnedBlocks.get(w).protectedBlock(p) || OwnedBlocks.get(w).claimed(p)))continue;
            for(int y=-2;y<=3;y++)for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){
                BlockPos leaf=p.add(x,y,z);
                if(safe(leaf) && npc.getWorld().getBlockState(leaf).isIn(BlockTags.LEAVES))return p;
            }
        }
        return null;
    }
    private static final java.util.List<BlockPos> SEARCH_COLUMNS=java.util.stream.IntStream.rangeClosed(-32,32).boxed()
        .flatMap(x->java.util.stream.IntStream.rangeClosed(-32,32).mapToObj(z->new BlockPos(x,0,z)))
        .filter(p->p.getX()*p.getX()+p.getZ()*p.getZ()<=32*32)
        .sorted(Comparator.comparingDouble(p->p.getSquaredDistance(BlockPos.ORIGIN))).toList();
    private BlockPos searchCenter;
    private int searchColumn;
    private boolean searching;
    public String searchStatus(){return "column="+searchColumn+" pending="+searching+" center="+searchCenter;}
    private BlockPos findTree() {
        BlockPos center=npc.getBlockPos();
        var remembered=npc.intelligence().memory().nearest("tree",center,npc.getWorld().getTime(),36);
        if(remembered.isPresent() && avoidedTrees.getOrDefault(remembered.get(),0)<=npc.age
                && (treeBase(remembered.get()) || rememberedRemnant(remembered.get())!=null)){
            searching=false;return remembered.get();}
        if(!searching){searchCenter=center.toImmutable();searchColumn=0;searching=true;}
        // Budgeted, nearest-first scan: no skipped odd columns and no giant synchronous scan.
        for(int budget=0;budget<96 && searchColumn<SEARCH_COLUMNS.size();budget++,searchColumn++){
            BlockPos column=searchCenter.add(SEARCH_COLUMNS.get(searchColumn));
            for(int dy=-12;dy<=12;dy++){
                BlockPos p=column.up(dy);
                if((origin!=null && p.getSquaredDistance(origin)>50*50)
                    || avoidedTrees.getOrDefault(p,0)>npc.age
                    || npc.intelligence().memory().blocked(p,npc.getWorld().getTime()) || !treeBase(p))continue;
                npc.intelligence().memory().remember("tree",p,npc.getWorld().getTime(),12000);
                searching=false;return p;
            }
        }
        if(searchColumn>=SEARCH_COLUMNS.size())searching=false;
        return null;
    }
    private void equipAxe() {
        if(!npc.autoEquip)return;
        if(npc.getMainHandStack().getItem() instanceof AxeItem && !dev.aiplayer.ai.brain.ResourcePlan.worn(npc.getMainHandStack()))return;
        int best=-1;float speed=0;
        for(int i=0;i<npc.backpack.size();i++){
            ItemStack s=npc.backpack.getStack(i);
            if(s.getItem() instanceof AxeItem axe && s.getCount()==1 && !dev.aiplayer.ai.brain.ResourcePlan.worn(s)) {
                float value=axe.getMaterial().getMiningSpeedMultiplier();
                if(value>speed){speed=value;best=i;}
            }
        }
        if(best>=0) {
            ItemStack selected=npc.backpack.removeStack(best);
            ItemStack old=npc.getMainHandStack();
            npc.equipStack(EquipmentSlot.MAINHAND,selected);
            npc.backpack.setStack(best,old);
        }
    }
    private boolean travelSearchPoint() {
        if(waypoint!=null && npc.age-waypointStarted>100)waypoint=null;
        if(waypoint!=null && npc.squaredDistanceTo(Vec3d.ofCenter(waypoint))>4.5*4.5) {
            if(npc.age%30==0 && !WorkNavigation.moveNear(npc,waypoint,waypoint.getY(),1.15))waypoint=null;
            return true;
        }
        waypoint=null;
        // Bounded outward search (not a scan of unloaded chunks). A manual order reaches farther.
        int[][] choices={{12,0},{0,12},{-12,0},{0,-12},{20,10},{-20,10},{20,-10},{-20,-10},
            {30,0},{0,30},{-30,0},{0,-30},{34,22},{-34,22},{34,-22},{-34,-22}};
        int limit=manual?choices.length:4;
        while(waypointIndex<limit) {
            int[] delta=choices[waypointIndex++];
            BlockPos base=origin.add(delta[0],0,delta[1]);
            for(int dy=2;dy>=-3;dy--) {
                BlockPos p=base.up(dy);
                if(!safe(p) || !safe(p.up()) || !safe(p.down()))continue;
                if(!npc.getWorld().getBlockState(p).isAir() || !npc.getWorld().getBlockState(p.up()).isAir()
                    || !npc.getWorld().getBlockState(p.down()).isSolidBlock(npc.getWorld(),p.down()))continue;
                if(!WorkNavigation.moveNear(npc,p,p.getY(),1.15))continue;
                waypoint=p.toImmutable();waypointStarted=npc.age;lastAction=npc.age;return true;
            }
        }
        return false;
    }
    private void skipDrop() {skipDrop(300);}
    private void skipDrop(int retryDelay) {
        if(lootTarget!=null)avoidedDrops.put(lootTarget.getUuid(),npc.age+retryDelay);
        lootTarget=null;mining.reset();npc.getNavigation().stop();
    }
    private boolean clearLeafToward(Vec3d target) {
        BlockPos leaf=WorkNavigation.leafBetween(npc,target);
        if(leaf==null || !mining.reachable(leaf))return false;
        if(mining.tick(leaf))lastAction=npc.age;
        return true;
    }
    private boolean seekDrops() {
        if(!npc.autoLoot)return false;
        // A manual food order is a hunt when suitable prey is reachable. Do
        // not let an unrelated log or distant loose apple replace that order.
        // Contact pickup and our own physical loot still take precedence.
        boolean huntFirst=kind==Kind.FOOD && manual && preparePrey();
        if(huntFirst && lootTarget!=null && !huntingPickup(lootTarget)){
            lootTarget=null;nextRoute=0;npc.getNavigation().stop();
        }
        avoidedDrops.entrySet().removeIf(e -> e.getValue()<=npc.age);
        if(lootTarget!=null && (!lootTarget.isAlive() || npc.age-lootStarted>120
                || !npc.backpack.canInsert(lootTarget.getStack())))skipDrop();
        if(lootTarget==null && npc.age>=nextDropScan) {
            nextDropScan=npc.age+10;
            lootTarget=npc.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class,
                npc.getBoundingBox().expand(8),d -> d.isAlive() && !d.cannotPickup()
                    && !avoidedDrops.containsKey(d.getUuid()) && npc.backpack.canInsert(d.getStack())
                    && (!huntFirst || huntingPickup(d))
                    && (kind==Kind.NONE || (kind==Kind.WOOD && d.getStack().isIn(ItemTags.LOGS))
                        // Opportunistic wood salvage is allowed while foraging. Only actual food
                        // advances the FOOD goal; stone work stays restricted to cobblestone.
                        || (kind==Kind.FOOD && (foodItem(d.getStack()) || d.getStack().isIn(ItemTags.LOGS)))
                        || (kind==Kind.STONE && d.getStack().isOf(Items.COBBLESTONE))))
                .stream().min(Comparator.comparingDouble(d -> npc.squaredDistanceTo(d)
                    // Food takes precedence over optional logs if both exist nearby.
                    -(kind==Kind.FOOD && foodItem(d.getStack()) ? 200 : 0)
                    -(d.getCommandTags().contains("aiplayer_drop_"+npc.getUuid())?1000:0))).orElse(null);
            lootStarted=npc.age;
        }
        if(lootTarget==null)return false;
        if(lootTarget.getStack().isIn(ItemTags.LOGS)) {
            // Remove the actual leaf supporting the item, then let gravity bring it down.
            BlockPos support=lootTarget.getBlockPos().down();
            if(npc.getWorld().getBlockState(support).isIn(BlockTags.LEAVES) && mining.reachable(support)) {
                if(mining.tick(support))lastAction=npc.age;
                return true;
            }
            if(clearLeafToward(lootTarget.getPos()))return true;
        }
        if(lootTarget.getY()>npc.getY()+2.5) {skipDrop();return false;}
        mining.reset();
        if(npc.survival().canCollect(lootTarget))return true;
        // Vanilla land pathfinding often stops at the waterline. Swim deliberately
        // only for nearby floating drops, keeping the same physical pickup rules.
        if(WorkNavigation.swimToDrop(npc,lootTarget))return true;
        if(npc.age>=nextRoute) {
            nextRoute=npc.age+20;
            // Pathfinding can fail briefly while landing or while the drop is still falling.
            // Retry soon instead of forgetting reachable resources for fifteen seconds.
            if(!WorkNavigation.moveToDrop(npc,lootTarget,1.1)) {skipDrop(40);return false;}
        }
        return true;
    }
    private void captureTree() {
        treeLogs.clear();
        java.util.ArrayDeque<BlockPos> todo=new java.util.ArrayDeque<>();
        java.util.Set<BlockPos> seen=new java.util.HashSet<>();
        BlockPos seed=npc.getWorld().getBlockState(tree).isIn(BlockTags.LOGS)?tree:rememberedRemnant(tree);
        if(seed==null)return;
        todo.add(seed);seen.add(seed);
        while(!todo.isEmpty() && treeLogs.size()<160) {
            BlockPos p=todo.removeFirst();
            if(!safe(p) || pillar.owns(p) || !npc.getWorld().getBlockState(p).isIn(BlockTags.LOGS))continue;
            if(npc.getWorld() instanceof ServerWorld world && (OwnedBlocks.get(world).protectedBlock(p) || OwnedBlocks.get(world).claimed(p)))continue;
            treeLogs.add(p);
            for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++) {
                BlockPos q=p.add(x,y,z);
                if(q.getY()<tree.getY() || q.getY()>tree.getY()+30
                    || Math.abs(q.getX()-tree.getX())>6 || Math.abs(q.getZ()-tree.getZ())>6)continue;
                if(seen.add(q))todo.add(q);
            }
        }
    }
    private void woodTick() {
        if(npc.age < nextSearch)return;
        if(tree==null)resumeDeferredTree();
        if(tree==null || !safe(tree)){
            tree=findTree();nextSearch=npc.age+(searching?1:30);
            if(tree==null){
                if(searching)return;
                if(travelSearchPoint())return;
                if(npc.age-lastAction>160){if(!manual)npc.speech().notice(CompanionSpeech.Event.NO_TREE);tell("Meu rei, não achei árvore natural acessível nessa busca. Pode ter madeira mais adiante ou algum caminho bloqueado.");finish(false);}
                return;
            }
            captureTree();waypoint=null;
        }
        treeLogs.removeIf(p -> safe(p) && !npc.getWorld().getBlockState(p).isIn(BlockTags.LOGS));
        if(treeLogs.isEmpty()) {tree=null;cleaning=pillar.active();mining.reset();return;}
        equipAxe();
        if(pillar.isJumping()){
            if(!pillar.climb()) {deferTree(500);tree=null;treeLogs.clear();cleaning=pillar.active();}
            return;
        }
        BlockPos committed=mining.target();
        BlockPos cut=committed!=null && treeLogs.contains(committed) && mining.reachable(committed)?committed:treeLogs.stream().filter(mining::reachable)
            .min(Comparator.comparingDouble(p -> npc.getEyePos().squaredDistanceTo(Vec3d.ofCenter(p)))).orElse(null);
        if(cut!=null) {
            if(mining.tick(cut)){treeLogs.remove(cut);lastAction=npc.age;}
            return;
        }
        BlockPos lowest=treeLogs.stream().min(Comparator.comparingInt(BlockPos::getY)).orElse(tree);
        // Open the line of sight to the trunk at any height, not only directly overhead.
        if(clearLeafToward(Vec3d.ofCenter(lowest)))return;
        double horizontal=Math.pow(npc.getX()-lowest.getX()-0.5,2)+Math.pow(npc.getZ()-lowest.getZ()-0.5,2);
        if(lowest.getY()>npc.getY()+2 && horizontal<6.25) {
            // Clear only leaves immediately above the climbing body, with normal mining.
            for(int dy=2;dy<=3;dy++) {
                BlockPos overhead=npc.getBlockPos().up(dy);
                if(npc.getWorld().getBlockState(overhead).isIn(BlockTags.LEAVES) && mining.reachable(overhead)) {
                    if(mining.tick(overhead))lastAction=npc.age;
                    return;
                }
            }
            mining.reset();
            if(pillar.climb()){lastAction=npc.age;return;}
            deferTree(1200);avoidedTrees.put(tree,npc.age+1200);tree=null;treeLogs.clear();cleaning=pillar.active();return;
        }
        mining.reset();
        if(pillar.active()){
            // No remaining trunk is in reach. Recover the real support blocks
            // before starting another climb, but do not erase the tree objective.
            deferTree(450);avoidedTrees.put(tree,npc.age+450);
            tree=null;treeLogs.clear();cleaning=true;nextSearch=npc.age+35;
            return;
        }
        if(npc.age>=nextRoute) {
            nextRoute=npc.age+25;
            if(!WorkNavigation.moveNear(npc,lowest,tree.getY(),1.18)) {
                npc.speech().notice(CompanionSpeech.Event.ROUTE);
                deferTree(400);avoidedTrees.put(tree,npc.age+400);tree=null;treeLogs.clear();
                noProgressTicks=0;nextSearch=npc.age+10;return;
            }
        }
        if(npc.age%40==0)checkStuck();
    }
    private void checkStuck() {
        Vec3d now=npc.getPos();
        if(now.squaredDistanceTo(lastLocation)<0.18)noProgressTicks+=40;else noProgressTicks=0;
        lastLocation=now;
        if(noProgressTicks>=160){deferTree(1200);if(tree!=null)avoidedTrees.put(tree,npc.age+1200);treeLogs.clear();tree=null;noProgressTicks=0;nextSearch=npc.age+25;}
    }
    private boolean prey(AnimalEntity a) {
        return a.isAlive() && !a.isBaby() && !a.hasCustomName() && !a.isLeashed()
            && !a.isPersistent() && !a.isInLove() && !a.hasPassengers() && !a.hasVehicle()
            && !(a instanceof PigEntity pig && pig.isSaddled())
            && (a instanceof CowEntity || a instanceof PigEntity
                || a instanceof SheepEntity || a instanceof RabbitEntity);
    }
    private boolean huntingPickup(net.minecraft.entity.ItemEntity drop){
        return drop.getCommandTags().contains("aiplayer_drop_"+npc.getUuid())
            || npc.survival().canCollect(drop);
    }
    /** Entity observation alone is not proof of reachability. Reuse the
     * bounded terrain planner before committing a manual hunting priority. */
    private boolean preparePrey(){
        if(animal!=null && prey(animal) && npc.squaredDistanceTo(animal)<=25*25)return true;
        animal=null;
        if(npc.age<nextPreyScan)return false;
        nextPreyScan=npc.age+20;
        avoidedAnimals.entrySet().removeIf(e->e.getValue()<=npc.age);
        var candidates=npc.getWorld().getEntitiesByClass(AnimalEntity.class,npc.getBoundingBox().expand(14),
                a->prey(a) && !avoidedAnimals.containsKey(a.getUuid()) && npc.canSee(a))
            .stream().sorted(Comparator.comparingDouble(npc::squaredDistanceTo)).limit(6).toList();
        for(AnimalEntity candidate:candidates){
            var route=WorkNavigation.findPath(npc,candidate.getBlockPos(),1,
                dev.aiplayer.ai.navigation.NpcPathfinder.Mode.SAFE);
            if(!WorkNavigation.safePath(npc,route,Math.min(npc.getBlockY(),candidate.getBlockY())-2))continue;
            animal=candidate;huntStarted=npc.age;nextRoute=0;return true;
        }
        return false;
    }
    private boolean foodTick() {
        // A failed scan does not own movement: following and strolling may run meanwhile.
        if(npc.age<nextSearch)return false;
        avoidedAnimals.entrySet().removeIf(e -> e.getValue()<=npc.age);
        if(animal!=null && npc.age-huntStarted>100) {
            avoidedAnimals.put(animal.getUuid(),npc.age+400);
            animal=null;npc.getNavigation().stop();
        }
        if(animal==null || !prey(animal) || animal.squaredDistanceTo(npc)>25*25) {
            if(!preparePrey()) {
                if(npc.age-lastAction>160){if(!manual)npc.speech().notice(CompanionSpeech.Event.NO_FOOD);tell("Não achei animal adequado pra conseguir comida por aqui, viu?");finish(false);}
                nextSearch=npc.age+30;return false;
            }
        }
        if(npc.autoEquip)npc.optimizeEquipment();
        if(npc.squaredDistanceTo(animal)>2.5*2.5 || !npc.canSee(animal)) {
            if(npc.age>=nextRoute) {
                nextRoute=npc.age+15;
                var route=WorkNavigation.findPath(npc,animal.getBlockPos(),1,
                    dev.aiplayer.ai.navigation.NpcPathfinder.Mode.SAFE);
                if(!WorkNavigation.safePath(npc,route,Math.min(npc.getBlockY(),animal.getBlockY())-2)) {
                    avoidedAnimals.put(animal.getUuid(),npc.age+400);
                    animal=null;return false;
                }
                npc.getNavigation().stop();
                if(!npc.getNavigation().startMovingAlong(route,1.23)){
                    avoidedAnimals.put(animal.getUuid(),npc.age+100);animal=null;return false;
                }
            }
            return true;
        }
        npc.getNavigation().stop();
        npc.getLookControl().lookAt(animal,30,30);
        if(attackCooldown==0){
            npc.tryAttack(animal);
            attackCooldown=20;lastAction=npc.age;huntStarted=npc.age;
        }
        if(!animal.isAlive()) {animal=null;nextSearch=npc.age+10;}
        return true;
    }
    /** Called after SurvivalController picks up a legitimate food drop. */
    public void onCollected(ItemStack added) {
        if((kind==Kind.FOOD && foodItem(added)) || (kind==Kind.WOOD && added.isIn(ItemTags.LOGS)) || (kind==Kind.STONE && added.isOf(Items.COBBLESTONE))) {
            gathered+=added.getCount();lastAction=npc.age;
        }
    }
    public void writeNbt(NbtCompound nbt) {
        pillar.writeNbt(nbt);
        net.minecraft.nbt.NbtList deferred=new net.minecraft.nbt.NbtList();
        for(var t:deferredTrees){
            NbtCompound data=new NbtCompound();data.putLong("Base",t.base().asLong());data.putLong("Retry",t.retry());data.putLong("Expires",t.expires());
            data.putLongArray("Logs",t.logs().stream().mapToLong(BlockPos::asLong).toArray());deferred.add(data);
        }
        nbt.put("DeferredTrees",deferred);
        if(!active())return;
        NbtCompound data=new NbtCompound();
        data.putString("Kind",kind.name());data.putInt("Desired",desired);data.putInt("Gathered",gathered);
        data.putBoolean("Manual",manual);
        if(origin!=null){data.putInt("X",origin.getX());data.putInt("Y",origin.getY());data.putInt("Z",origin.getZ());}
        if(tree!=null)data.putLong("Tree",tree.asLong());
        data.putLongArray("Logs",treeLogs.stream().mapToLong(BlockPos::asLong).toArray());
        nbt.put("GatherTask",data);
    }
    public void readNbt(NbtCompound nbt) {
        // On load or nested-job restoration, routes and item entities from the old
        // activity must never bleed into the restored task. Only durable goals,
        // ownership and deferred trees are read from NBT.
        mining.reset();stone.reset();pausedAt=-1;kind=Kind.NONE;tree=null;animal=null;
        lootTarget=null;lootStarted=0;nextDropScan=0;nextRoute=0;nextSearch=0;nextPreyScan=0;
        waypoint=null;waypointIndex=0;idleCooldown=0;searching=false;searchCenter=null;searchColumn=0;
        lastLocation=npc.getPos();noProgressTicks=0;attackCooldown=0;
        treeLogs.clear();pillar.readNbt(nbt);cleaning=pillar.active();
        recoveryLootUntil=npc.age+400;
        deferredTrees.clear();
        var deferred=nbt.getList("DeferredTrees",net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
        for(int i=0;i<Math.min(8,deferred.size());i++){
            var data=deferred.getCompound(i);java.util.List<BlockPos> logs=new java.util.ArrayList<>();
            for(long pos:data.getLongArray("Logs"))if(logs.size()<160)logs.add(BlockPos.fromLong(pos));
            deferredTrees.add(new DeferredTree(BlockPos.fromLong(data.getLong("Base")),logs,data.getLong("Retry"),data.getLong("Expires")));
        }
        if(!nbt.contains("GatherTask"))return;
        NbtCompound data=nbt.getCompound("GatherTask");
        try {kind=Kind.valueOf(data.getString("Kind"));}catch(IllegalArgumentException ex){kind=Kind.NONE;}
        desired=Math.max(1,Math.min(264,data.getInt("Desired")));gathered=Math.max(0,data.getInt("Gathered"));
        manual=data.getBoolean("Manual");origin=new BlockPos(data.getInt("X"),data.getInt("Y"),data.getInt("Z"));
        if(data.contains("Tree"))tree=BlockPos.fromLong(data.getLong("Tree"));
        for(long pos:data.getLongArray("Logs"))if(treeLogs.size()<160)treeLogs.add(BlockPos.fromLong(pos));
        lastAction=npc.age;
    }
}
