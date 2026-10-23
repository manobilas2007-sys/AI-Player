package dev.aiplayer.ai;

import dev.aiplayer.entity.CompanionEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import java.util.LinkedHashMap;
import java.util.Map;

/** Real materials, real placed bench, visible work delay, retrieval after use. */
public final class StarterCrafting {
    private final CompanionEntity npc;
    private record Recipe(Item result,int number,Map<Item,Integer> ingredients,boolean bench) {}
    private Recipe pending;
    private BlockPos benchPos;
    private boolean ownBench;
    private int timer;
    private final PhysicalMining mining;
    private boolean reclaiming;
    private int recoveryTicks, blockedTicks, retryAt, benchFailures, nextDeferredCheck;
    // Lost reachability never destroys ownership: remember and revisit the real bench.
    private final java.util.List<BlockPos> deferredBenches=new java.util.ArrayList<>();

    public StarterCrafting(CompanionEntity npc){this.npc=npc;this.mining=new PhysicalMining(npc);}
    public boolean hasBench(){return ownBench && benchPos!=null;}
    public boolean hasSession(){return pending!=null || reclaiming;}
    /** A bench recovery is optional while the next stone recipe lacks material.
     * Ownership remains recorded; mining may temporarily take priority. */
    public boolean isReclaiming(){return reclaiming && pending==null;}
    /** A nearby owned table can obstruct the only passage to a stone face.
     * Clearing it is real, physical work, not a fabricated resource. */
    public boolean nearbyBenchObstruction(){
        return isReclaiming() && ownBench && benchPos!=null
            && npc.getWorld().isChunkLoaded(benchPos)
            && npc.getWorld().getBlockState(benchPos).isOf(Blocks.CRAFTING_TABLE)
            && npc.squaredDistanceTo(benchPos.getX()+0.5,benchPos.getY()+0.5,benchPos.getZ()+0.5)<8;
    }
    private boolean deferredBenchNear(){
        if(npc.age<nextDeferredCheck)return false;
        return deferredBenches.stream().anyMatch(pos -> npc.getWorld().isChunkLoaded(pos)
            && npc.squaredDistanceTo(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5)<=16);
    }
    public boolean canBegin(){return npc.age>=retryAt && !hasSession() && (choose()!=null || deferredBenchNear());}
    /** True only when a real stone tool recipe is affordable. Unrelated plank,
     * stick or bench recipes must not interrupt a committed mining route. */
    public boolean canCraftStoneUpgradeNow(){
        if(!npc.autoCraft || npc.age<retryAt)return false;
        return tryTools(Items.COBBLESTONE,2,Items.STONE_PICKAXE,Items.STONE_AXE,Items.STONE_SWORD)!=null;
    }
    public String description(){return reclaiming?"Recuperar bancada":pending==null?"Conferir ingredientes":Registries.ITEM.getId(pending.result).toString();}
    public String diagnostics(){
        return "bench="+benchPos+" own="+ownBench+" recovery="+recoveryTicks+" blocked="+blockedTicks
            +" retry="+retryAt+" mining="+mining.target()+" drops="+(benchPos==null?"none":
                npc.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class,
                    new net.minecraft.util.math.Box(benchPos).expand(3),d->d.isAlive() && d.getStack().isOf(Items.CRAFTING_TABLE))
                .stream().map(d->d.getPos()+" collect="+npc.survival().canCollect(d)).toList());
    }
    public void pause(){mining.reset();npc.clearWorkingVisual();}
    public boolean isWorking(){return npc.age>=retryAt && (pending!=null || reclaiming);}
    public boolean needsBasicTools(){return best(PickaxeItem.class)==0 || best(AxeItem.class)==0 || best(SwordItem.class)==0;}
    /** A missing/broken axe must be replaced even after the original wood
     * order completes. Only claim priority when there are actual resources
     * to progress toward a replacement; empty-handed wood bootstrap can
     * still gather its first logs without an axe. */
    public boolean needsAxeReplacement(){
        if(!npc.autoCraft || !npc.autoEquip || dev.aiplayer.ai.brain.ResourcePlan.usable(npc,AxeItem.class))return false;
        if(count(Items.COBBLESTONE)>=3 || count(Items.IRON_INGOT)>=3 || count(Items.DIAMOND)>=3)return true;
        if(planks()!=null)return true;
        return planksRecipe()!=null;
    }
    private int count(Item item) {
        int total=0;
        for(int i=0;i<npc.backpack.size();i++)if(npc.backpack.getStack(i).isOf(item))total+=npc.backpack.getStack(i).getCount();
        return total;
    }
    private boolean contains(Recipe r){
        for(var ingredient:r.ingredients.entrySet())if(count(ingredient.getKey())<ingredient.getValue())return false;
        return true;
    }
    private boolean use(Item item,int qty) {
        if(count(item)<qty)return false;
        int needed=qty;
        for(int i=0;i<npc.backpack.size() && needed>0;i++){
            ItemStack stack=npc.backpack.getStack(i);
            if(stack.isOf(item)){int n=Math.min(needed,stack.getCount());npc.backpack.removeStack(i,n);needed-=n;}
        }
        return true;
    }
    private boolean produce(Recipe r) {
        if(!contains(r) || !npc.backpack.canInsert(new ItemStack(r.result,r.number)))return false;
        for(var ingredient:r.ingredients.entrySet())use(ingredient.getKey(),ingredient.getValue());
        ItemStack overflow=npc.backpack.addStack(new ItemStack(r.result,r.number));
        if(!overflow.isEmpty())npc.dropStack(overflow);
        // Retire worn axes only AFTER the replacement recipe consumed materials
        // and produced its actual output. Never delete the last tool beforehand.
        if(r.result instanceof AxeItem) {
            for(int i=0;i<npc.backpack.size();i++) {
                ItemStack old=npc.backpack.getStack(i);
                if(old.getItem() instanceof AxeItem && dev.aiplayer.ai.brain.ResourcePlan.worn(old))
                    npc.backpack.setStack(i,ItemStack.EMPTY);
            }
            ItemStack held=npc.getMainHandStack();
            if(held.getItem() instanceof AxeItem && dev.aiplayer.ai.brain.ResourcePlan.worn(held))
                npc.equipStack(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
            if(npc.gathering().kind()==ResourceGathering.Kind.WOOD)
                npc.equipMiningTool(net.minecraft.block.Blocks.OAK_LOG.getDefaultState());
        }
        npc.backpack.markDirty();
        return true;
    }
    private Item planks() {
        for(Item item:new Item[]{Items.OAK_PLANKS,Items.SPRUCE_PLANKS,Items.BIRCH_PLANKS,Items.JUNGLE_PLANKS,
                Items.ACACIA_PLANKS,Items.DARK_OAK_PLANKS,Items.MANGROVE_PLANKS,Items.CHERRY_PLANKS})if(count(item)>0)return item;
        return null;
    }
    private Recipe planksRecipe(){
        Item[][] variants={{Items.OAK_LOG,Items.OAK_PLANKS},{Items.SPRUCE_LOG,Items.SPRUCE_PLANKS},
            {Items.BIRCH_LOG,Items.BIRCH_PLANKS},{Items.JUNGLE_LOG,Items.JUNGLE_PLANKS},
            {Items.ACACIA_LOG,Items.ACACIA_PLANKS},{Items.DARK_OAK_LOG,Items.DARK_OAK_PLANKS},
            {Items.MANGROVE_LOG,Items.MANGROVE_PLANKS},{Items.CHERRY_LOG,Items.CHERRY_PLANKS}};
        for(Item[] pair:variants)if(count(pair[0])>0)return new Recipe(pair[1],4,Map.of(pair[0],1),false);
        return null;
    }
    private int score(Item it) {
        if(it==Items.NETHERITE_PICKAXE||it==Items.NETHERITE_AXE||it==Items.NETHERITE_SWORD)return 5;
        if(it==Items.DIAMOND_PICKAXE||it==Items.DIAMOND_AXE||it==Items.DIAMOND_SWORD)return 4;
        if(it==Items.IRON_PICKAXE||it==Items.IRON_AXE||it==Items.IRON_SWORD)return 3;
        if(it==Items.STONE_PICKAXE||it==Items.STONE_AXE||it==Items.STONE_SWORD)return 2;
        if(it==Items.WOODEN_PICKAXE||it==Items.WOODEN_AXE||it==Items.WOODEN_SWORD || it==Items.GOLDEN_PICKAXE || it==Items.GOLDEN_AXE || it==Items.GOLDEN_SWORD)return 1;
        return 0;
    }
    private int best(Class<? extends Item> toolType) {
        int best=0;
        ItemStack main=npc.getMainHandStack();
        if(toolType.isInstance(main.getItem()) && !dev.aiplayer.ai.brain.ResourcePlan.worn(main))best=score(main.getItem());
        for(int i=0;i<npc.backpack.size();i++){
            ItemStack s=npc.backpack.getStack(i);
            if(toolType.isInstance(s.getItem()) && !dev.aiplayer.ai.brain.ResourcePlan.worn(s))best=Math.max(best,score(s.getItem()));
        }
        return best;
    }
    private Recipe recipe(Item result,Item material,int count, int sticks) {
        return new Recipe(result,1,Map.of(material,count,Items.STICK,sticks),true);
    }
    private Recipe tryTools(Item material,int level,Item pick,Item axe,Item sword){
        if(count(Items.STICK)<2)return null;
        if(best(PickaxeItem.class)<level && count(material)>=3)return recipe(pick,material,3,2);
        if(best(AxeItem.class)<level && count(material)>=3)return recipe(axe,material,3,2);
        if(best(SwordItem.class)<level && count(material)>=2)return recipe(sword,material,2,1);
        return null;
    }
    private int armorRank(EquipmentSlot slot) {
        int best=armorRankOf(npc.getEquippedStack(slot),slot);
        for(int i=0;i<npc.backpack.size();i++)best=Math.max(best,armorRankOf(npc.backpack.getStack(i),slot));
        return best;
    }
    private int armorRankOf(ItemStack stack,EquipmentSlot slot) {
        if(!(stack.getItem() instanceof ArmorItem armor) || armor.getSlotType()!=slot)return 0;
        ArmorMaterial m=armor.getMaterial();
        if(m==ArmorMaterials.NETHERITE)return 5;
        if(m==ArmorMaterials.DIAMOND)return 4;
        if(m==ArmorMaterials.IRON)return 3;
        if(m==ArmorMaterials.CHAIN || m==ArmorMaterials.GOLD)return 2;
        return 1;
    }
    private Recipe armor(Item material,int level,Item head,Item chest,Item legs,Item feet){
        if(armorRank(EquipmentSlot.CHEST)<level && count(material)>=8)return new Recipe(chest,1,Map.of(material,8),true);
        if(armorRank(EquipmentSlot.LEGS)<level && count(material)>=7)return new Recipe(legs,1,Map.of(material,7),true);
        if(armorRank(EquipmentSlot.HEAD)<level && count(material)>=5)return new Recipe(head,1,Map.of(material,5),true);
        if(armorRank(EquipmentSlot.FEET)<level && count(material)>=4)return new Recipe(feet,1,Map.of(material,4),true);
        return null;
    }
    private Recipe choose() {
        Item plank=planks();
        if(plank==null) {Recipe wood=planksRecipe();if(wood!=null)return wood;}
        // Ensure a reliable wood reserve for bench + sticks before expensive iron recipes.
        if(plank!=null && count(plank)<4) {Recipe wood=planksRecipe();if(wood!=null)return wood;}
        if(plank!=null && count(Items.STICK)<4 && count(plank)>=2)return new Recipe(Items.STICK,4,Map.of(plank,2),false);
        if(plank!=null && count(Items.CRAFTING_TABLE)==0 && locateBench()==null && count(plank)>=4)
            return new Recipe(Items.CRAFTING_TABLE,1,Map.of(plank,4),false);
        if(npc.gathering().kind()==ResourceGathering.Kind.STONE && npc.autoEquip && !SurfaceStoneGathering.hasPick(npc)
                && plank!=null && count(plank)>=3 && count(Items.STICK)>=2)
            return recipe(Items.WOODEN_PICKAXE,plank,3,2);
        // Preserve axe maintenance across WOOD -> NONE. A newly finished
        // FallingTree order must not make the crafting planner forget that
        // its last axe broke, or let an optional sword/pick recipe go first.
        if(needsAxeReplacement() && best(AxeItem.class)==0 && count(Items.STICK)>=2) {
            if(count(Items.DIAMOND)>=3)return recipe(Items.DIAMOND_AXE,Items.DIAMOND,3,2);
            if(count(Items.IRON_INGOT)>=3)return recipe(Items.IRON_AXE,Items.IRON_INGOT,3,2);
            if(count(Items.COBBLESTONE)>=3)return recipe(Items.STONE_AXE,Items.COBBLESTONE,3,2);
            if(plank!=null && count(plank)>=3)return recipe(Items.WOODEN_AXE,plank,3,2);
        }
        Recipe r=tryTools(Items.DIAMOND,4,Items.DIAMOND_PICKAXE,Items.DIAMOND_AXE,Items.DIAMOND_SWORD);
        if(r!=null)return r;
        r=tryTools(Items.IRON_INGOT,3,Items.IRON_PICKAXE,Items.IRON_AXE,Items.IRON_SWORD);
        if(r!=null)return r;
        r=tryTools(Items.COBBLESTONE,2,Items.STONE_PICKAXE,Items.STONE_AXE,Items.STONE_SWORD);
        if(r!=null)return r;
        r=plank==null?null:tryTools(plank,1,Items.WOODEN_PICKAXE,Items.WOODEN_AXE,Items.WOODEN_SWORD);
        if(r!=null)return r;
        r=armor(Items.DIAMOND,4,Items.DIAMOND_HELMET,Items.DIAMOND_CHESTPLATE,Items.DIAMOND_LEGGINGS,Items.DIAMOND_BOOTS);
        if(r!=null)return r;
        return armor(Items.IRON_INGOT,3,Items.IRON_HELMET,Items.IRON_CHESTPLATE,Items.IRON_LEGGINGS,Items.IRON_BOOTS);
    }
    private BlockPos locateBench() {
        BlockPos c=npc.getBlockPos();
        for(BlockPos p:BlockPos.iterate(c.add(-3,-1,-3),c.add(3,2,3)))
            if(npc.getWorld().isChunkLoaded(p) && npc.getWorld().getBlockState(p).isOf(Blocks.CRAFTING_TABLE)
                    && mining.reachable(p))return p.toImmutable();
        return null;
    }
    private BlockPos putBench() {
        if(count(Items.CRAFTING_TABLE)<1)return null;
        for(Direction dir:Direction.Type.HORIZONTAL){
            BlockPos p=npc.getBlockPos().offset(dir);
            if(!npc.getWorld().getGameRules().getBoolean(net.minecraft.world.GameRules.DO_MOB_GRIEFING)
                    || !npc.getWorld().getOtherEntities(null,new net.minecraft.util.math.Box(p)).isEmpty()
                    || !npc.getWorld().isChunkLoaded(p)
                    || !(npc.getWorld().getBlockState(p).isAir()
                        || (npc.getWorld().getBlockState(p).isOf(Blocks.SNOW)
                            && npc.getWorld().getBlockState(p).get(net.minecraft.block.SnowBlock.LAYERS)==1))
                    || !npc.getWorld().getBlockState(p.down()).isSolidBlock(npc.getWorld(),p.down()))continue;
            if(npc.getWorld().setBlockState(p,Blocks.CRAFTING_TABLE.getDefaultState(),3)){
                if(npc.getWorld() instanceof net.minecraft.server.world.ServerWorld server)OwnedBlocks.get(server).claim(p,npc.getUuid());
                use(Items.CRAFTING_TABLE,1);ownBench=true;npc.animateInteraction(CompanionEntity.PLACE,8);
                npc.intelligence().memory().remember("bench",p,npc.getWorld().getTime(),24000);return p.toImmutable();
            }
        }
        return null;
    }
    private void blockedWork() {
        if(++blockedTicks<80)return;
        blockedTicks=0;retryAt=npc.age+100;
        npc.speech().notice(CompanionSpeech.Event.BENCH);
        mining.reset();npc.getNavigation().stop();npc.clearWorkingVisual();
        if(++benchFailures>=3) deferBench();
    }
    private void deferBench(){
        // The real block/drop is still in the world. Do not insert a fake bench.
        if(ownBench && benchPos!=null && !deferredBenches.contains(benchPos)) {
            if(deferredBenches.size()>=12)deferredBenches.remove(0);
            deferredBenches.add(benchPos.toImmutable());
        }
        pending=null;benchPos=null;ownBench=false;reclaiming=false;
        recoveryTicks=0;blockedTicks=0;benchFailures=0;
        nextDeferredCheck=npc.age+240;
    }
    private void revisitBench(){
        if(npc.age<nextDeferredCheck || hasSession() || deferredBenches.isEmpty())return;
        nextDeferredCheck=npc.age+200;
        for(int i=0;i<deferredBenches.size();i++) {
            BlockPos pos=deferredBenches.get(i);
            if(!npc.getWorld().isChunkLoaded(pos) || npc.squaredDistanceTo(
                    pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5)>16)continue;
            if(npc.getWorld() instanceof net.minecraft.server.world.ServerWorld w &&
                !OwnedBlocks.get(w).owned(pos,npc.getUuid())){deferredBenches.remove(i);return;}
            // Only dedicate another recovery session if the bench block/drop is reachable.
            if(mining.reachable(pos) || npc.getWorld().getBlockState(pos).isAir()) {
                deferredBenches.remove(i);benchPos=pos;ownBench=true;reclaiming=true;
                recoveryTicks=0;blockedTicks=0;benchFailures=0;
            }
            return;
        }
    }
    private void reclaimBench() {
        if(!ownBench || benchPos==null) {reclaiming=false;return;}
        if(!npc.getWorld().isChunkLoaded(benchPos)){blockedWork();return;}
        if(npc.getWorld() instanceof net.minecraft.server.world.ServerWorld server && OwnedBlocks.get(server).protectedBlock(benchPos)){
            ownBench=false;benchPos=null;reclaiming=false;return;
        }
        if(!npc.getWorld().getBlockState(benchPos).isOf(Blocks.CRAFTING_TABLE)) {
            mining.reset();
            var drops=npc.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class,
                new net.minecraft.util.math.Box(benchPos).expand(3),d -> d.isAlive()
                    && d.getStack().isOf(Items.CRAFTING_TABLE));
            if(!drops.isEmpty()) {
                if(!npc.autoLoot || !npc.backpack.canInsert(drops.get(0).getStack())) {
                    blockedWork();return;
                }
                if(npc.survival().canCollect(drops.get(0))) {recoveryTicks=0;return;}
                if(++recoveryTicks>=100) {blockedWork();return;}
                if(!WorkNavigation.swimToDrop(npc,drops.get(0)) && npc.age%10==0)
                    WorkNavigation.moveToDrop(npc,drops.get(0),1);
                return;
            }
            ownBench=false;benchPos=null;reclaiming=false;recoveryTicks=0;benchFailures=0;return;
        }
        if(!npc.getWorld().getGameRules().getBoolean(net.minecraft.world.GameRules.DO_MOB_GRIEFING)) {blockedWork();return;}
        if(!mining.reachable(benchPos)) {
            blockedWork();
            if(npc.age%20==0)WorkNavigation.moveNear(npc,benchPos,benchPos.getY(),1);
            return;
        }
        blockedTicks=0;
        mining.tick(benchPos);
    }
    public void tick() {
        if(npc.ownerId()==null || npc.age<retryAt)return;
        if(npc.menuOpen() || npc.isEating() || npc.getTarget()!=null){mining.reset();return;}
        // If the real owned bench is still standing and a stone recipe became
        // affordable while mining, use the bench BEFORE dismantling it. A
        // previously scheduled bench recovery is not a recipe prerequisite.
        if(reclaiming && (canCraftStoneUpgradeNow() || needsAxeReplacement()) && ownBench && benchPos!=null
                && npc.getWorld().isChunkLoaded(benchPos)
                && npc.getWorld().getBlockState(benchPos).isOf(Blocks.CRAFTING_TABLE)) {
            reclaiming=false;mining.reset();recoveryTicks=0;blockedTicks=0;
        }
        if(reclaiming){reclaimBench();return;}
        // Revisit a stranded bench only when we no longer have an active recipe.
        if(pending==null)revisitBench();
        if(reclaiming){reclaimBench();return;}
        if(!npc.autoCraft && pending==null)return;
        if(pending==null){
            // The central coordinator already throttles planning; start the chosen recipe immediately.
            Recipe r=choose();if(r==null || !contains(r))return;
            if(r.bench){
                if(benchPos==null || !npc.getWorld().getBlockState(benchPos).isOf(Blocks.CRAFTING_TABLE)) {
                    benchPos=locateBench();ownBench=false;
                    if(benchPos==null)benchPos=putBench();
                }
                if(benchPos==null){
                    if(!WorkNavigation.leaveIsolatedPerch(npc))blockedWork();
                    return;
                }
            }
            pending=r; timer=r.bench?34:12;
            npc.getNavigation().stop();return;
        }
        if(pending.bench && (benchPos==null || !npc.getWorld().isChunkLoaded(benchPos)
                || !npc.getWorld().getBlockState(benchPos).isOf(Blocks.CRAFTING_TABLE))) {
            pending=null;reclaiming=ownBench;return;
        }
        if(pending.bench && !mining.reachable(benchPos)) {
            blockedWork();
            if(npc.age%20==0)WorkNavigation.moveNear(npc,benchPos,benchPos.getY(),1);
            return;
        }
        npc.getNavigation().stop();
        blockedTicks=0;
        npc.clearWorkingVisual();
        if(npc.interactionVisual()!=CompanionEntity.PLACE)npc.animateInteraction(CompanionEntity.CRAFT,3);
        if(--timer>0)return;
        if(!pending.bench || (benchPos!=null && npc.getWorld().isChunkLoaded(benchPos)
                && npc.getWorld().getBlockState(benchPos).isOf(Blocks.CRAFTING_TABLE)
                && npc.squaredDistanceTo(benchPos.getX()+0.5,benchPos.getY()+0.5,benchPos.getZ()+0.5)<25)){
            if(!produce(pending)){pending=null;reclaiming=ownBench;retryAt=npc.age+60;return;}
            npc.intelligence().memory().succeeded("Fabricado: "+Registries.ITEM.getId(pending.result));
        }
        Recipe next=npc.autoCraft?choose():null;
        if(next!=null && contains(next) && npc.backpack.canInsert(new ItemStack(next.result,next.number))
                && (!next.bench || (benchPos!=null && npc.getWorld().getBlockState(benchPos).isOf(Blocks.CRAFTING_TABLE)))) {
            pending=next;timer=next.bench?34:12;
        } else {pending=null;timer=0;reclaiming=ownBench;}

    }
    public void writeNbt(NbtCompound root) {
        if(ownBench && benchPos!=null)root.putLong("OwnedBench",benchPos.asLong());
        if(!deferredBenches.isEmpty())root.putLongArray("DeferredOwnedBenches",deferredBenches.stream().mapToLong(BlockPos::asLong).toArray());
        if(pending==null)return;
        NbtCompound n=new NbtCompound();
        n.putString("Result",Registries.ITEM.getId(pending.result).toString());n.putInt("Output",pending.number);
        n.putBoolean("Bench",pending.bench);n.putBoolean("OwnBench",ownBench);n.putInt("Timer",timer);
        if(benchPos!=null){n.putInt("X",benchPos.getX());n.putInt("Y",benchPos.getY());n.putInt("Z",benchPos.getZ());}
        NbtList ingredients=new NbtList();
        for(var ing:pending.ingredients.entrySet()){
            NbtCompound c=new NbtCompound();c.putString("Item",Registries.ITEM.getId(ing.getKey()).toString());
            c.putInt("Count",ing.getValue());ingredients.add(c);
        }
        n.put("Ingredients",ingredients);root.put("CraftSession",n);
    }
    public void readNbt(NbtCompound root) {
        // Recipes consume ingredients atomically at completion. Re-plan after reload;
        // ownership must survive even if the saved recipe is absent or invalid.
        pending=null;benchPos=null;ownBench=false;timer=0;reclaiming=false;recoveryTicks=0;
        benchFailures=0;blockedTicks=0;deferredBenches.clear();
        for(long pos:root.getLongArray("DeferredOwnedBenches"))if(deferredBenches.size()<12)deferredBenches.add(BlockPos.fromLong(pos));
        nextDeferredCheck=npc.age+200;
        if(root.contains("OwnedBench")) {
            benchPos=BlockPos.fromLong(root.getLong("OwnedBench"));ownBench=true;
        } else if(root.contains("CraftSession")) {
            NbtCompound n=root.getCompound("CraftSession");
            if(n.getBoolean("OwnBench") && n.contains("X") && n.contains("Y") && n.contains("Z")) {
                benchPos=new BlockPos(n.getInt("X"),n.getInt("Y"),n.getInt("Z"));ownBench=true;
            }
        }
        reclaiming=ownBench;
        // Re-plan from real inventory; accept saved duration only if the next recipe is identical.
        if(root.contains("CraftSession")) {
            NbtCompound saved=root.getCompound("CraftSession");
            Recipe next=choose();
            if(next!=null && contains(next) && Registries.ITEM.getId(next.result).toString().equals(saved.getString("Result"))
                    && (!next.bench || (benchPos!=null && npc.getWorld().isChunkLoaded(benchPos)
                    && npc.getWorld().getBlockState(benchPos).isOf(Blocks.CRAFTING_TABLE)))) {
                pending=next;timer=Math.max(1,Math.min(next.bench?34:12,saved.getInt("Timer")));reclaiming=false;
            }
        }
    }
}
