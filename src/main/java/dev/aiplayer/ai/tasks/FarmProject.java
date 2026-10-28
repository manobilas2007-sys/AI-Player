package dev.aiplayer.ai.tasks;

import dev.aiplayer.ai.Mode;
import dev.aiplayer.ai.OwnedBlocks;
import dev.aiplayer.ai.WorkNavigation;
import dev.aiplayer.ai.brain.ResourcePlan;
import dev.aiplayer.entity.CompanionEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.block.FluidDrainable;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PickaxeItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Confirmed crop care and a survival-built starter farm. No remote crop
 * collection, generated water, imaginary seeds or furnace simulation is used. */
public final class FarmProject {
    private enum Phase { SURVEY, CLEAR, FOUNDATION, GROUND, WATER, PLANT, CARE, DONE }
    private static final int CARE_RADIUS=100,SCAN_COLUMNS=16,MAX_FIELDS=4096;
    private static final int SIZE=7;
    private final CompanionEntity npc;
    private final ProjectWork work;
    private final ProjectMaterials materials;
    private final dev.aiplayer.ai.ManualResourceSurvey waterSurvey;
    private final List<BlockPos> fields=new ArrayList<>();
    private final Set<Long> known=new HashSet<>();
    private final Map<Long,Item> cropSeeds=new HashMap<>();
    private final Map<Long,Integer> avoidedSources=new HashMap<>();
    private BlockPos origin,anchor,action,pendingHarvest,source,waterScanOrigin;
    private String dimension="",status="Sem plantação";
    private boolean active,create,basinPrepared;
    private Phase phase=Phase.DONE;
    private Item border=Items.OAK_LOG;
    private int cursor,completed,actionTicks,fieldCursor,scanRadius,scanEdge,waterRadius,waterEdge,waterY;
    private int nextScan,harvestSince,nextDropMove,planted;
    private int sourceSince,waterSearchLeg,basinCursor;
    private double sourceDistance=Double.MAX_VALUE;

    public FarmProject(CompanionEntity npc,ProjectWork work,ProjectMaterials materials){
        this.npc=npc;this.work=work;this.materials=materials;
        waterSurvey=new dev.aiplayer.ai.ManualResourceSurvey(npc);
    }
    public boolean active(){return active;}
    public String status(){return status;}
    public int progress(){return create?completed:planted;}
    public String diagnostics(){return "phase="+phase+" cursor="+cursor+" basin="+basinCursor+"/"+basinPrepared+" field="+fieldCursor
        +" anchor="+anchor+" source="+source+" origin="+origin+" "+status;}
    public BlockPos origin(){return origin;}
    public int knownFields(){return fields.size();}
    public void start(boolean create,BlockPos orderOrigin){
        cancel();this.create=create;origin=orderOrigin.toImmutable();anchor=origin.add(2,-1,0);
        dimension=npc.getWorld().getRegistryKey().getValue().toString();active=true;
        phase=create?Phase.SURVEY:Phase.CARE;
        if(create){
            int best=0;HouseBlueprint.Palette selected=HouseBlueprint.palette("oak");
            for(HouseBlueprint.Palette palette:HouseBlueprint.palettes()){
                int count=materials.count(palette.log());
                if(count>best){best=count;selected=palette;}
            }
            border=selected.log();materials.selectWood(selected.log(),selected.planks());
            stationsOutsideFarm();
            work.clearBoundary();status="Examinar terreno para uma plantação irrigada";
        }else{
            work.setBoundary(origin,CARE_RADIUS);status="Cuidar das plantações existentes até 100 blocos desta ordem";
        }
    }
    public void pause(){waterSurvey.pause();work.pause();materials.pause();action=null;actionTicks=0;}
    private void stationsOutsideFarm(){
        materials.preferWorkbench(anchor.add(-2,1,-3));
        materials.preferFurnace(anchor.add(-2,1,-1));
        materials.keepOut(new Box(anchor.getX(),npc.getWorld().getBottomY(),anchor.getZ(),
            anchor.getX()+SIZE,npc.getWorld().getTopY(),anchor.getZ()+SIZE));
    }
    public void cancel(){
        waterSurvey.reset();active=false;phase=Phase.DONE;origin=null;anchor=null;fields.clear();known.clear();cropSeeds.clear();avoidedSources.clear();
        action=null;pendingHarvest=null;source=null;waterScanOrigin=null;cursor=0;completed=0;fieldCursor=0;
        actionTicks=0;scanRadius=0;scanEdge=0;waterRadius=0;waterEdge=0;waterY=0;nextScan=0;planted=0;
        sourceSince=0;sourceDistance=Double.MAX_VALUE;waterSearchLeg=0;basinCursor=0;basinPrepared=false;
        status="Plantação cancelada";
    }
    private boolean rules(ServerWorld world){
        if(world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)
                && world.getGameRules().getBoolean(GameRules.DO_TILE_DROPS))return true;
        status="As regras do mundo impedem cuidar ou preparar a plantação";return false;
    }
    private boolean within(BlockPos pos){
        if(origin==null)return false;
        long dx=(long)pos.getX()-origin.getX(),dz=(long)pos.getZ()-origin.getZ();
        return dx*dx+dz*dz<=CARE_RADIUS*CARE_RADIUS;
    }
    private boolean foreign(ServerWorld world,BlockPos pos){
        var ledger=OwnedBlocks.get(world);
        return ledger.claimed(pos) && !ledger.owned(pos,npc.getUuid());
    }
    private boolean careField(ServerWorld world,BlockPos soil){
        if(!within(soil.up()) || !world.isChunkLoaded(soil) || !world.isChunkLoaded(soil.up())
                || foreign(world,soil) || foreign(world,soil.up()) || world.getBlockEntity(soil)!=null
                || world.getBlockEntity(soil.up())!=null || !world.getBlockState(soil).isOf(Blocks.FARMLAND))return false;
        BlockState crop=world.getBlockState(soil.up());
        return crop.isAir() || supportedCrop(crop);
    }
    private boolean supportedCrop(BlockState state){
        return state.isOf(Blocks.WHEAT) || state.isOf(Blocks.CARROTS)
            || state.isOf(Blocks.POTATOES) || state.isOf(Blocks.BEETROOTS);
    }
    private Item seed(BlockState crop){
        return crop.isOf(Blocks.CARROTS)?Items.CARROT:crop.isOf(Blocks.POTATOES)?Items.POTATO
            :crop.isOf(Blocks.BEETROOTS)?Items.BEETROOT_SEEDS:Items.WHEAT_SEEDS;
    }
    private BlockState plantedState(Item item){
        return item==Items.CARROT?Blocks.CARROTS.getDefaultState():item==Items.POTATO?Blocks.POTATOES.getDefaultState()
            :item==Items.BEETROOT_SEEDS?Blocks.BEETROOTS.getDefaultState():Blocks.WHEAT.getDefaultState();
    }
    private Item availableSeed(){
        for(Item item:new Item[]{Items.WHEAT_SEEDS,Items.CARROT,Items.POTATO,Items.BEETROOT_SEEDS})
            if(materials.count(item)>0)return item;
        return null;
    }
    private void rememberField(ServerWorld world,BlockPos soil){
        if(!careField(world,soil))return;
        BlockPos immutable=soil.toImmutable();
        if(known.add(immutable.asLong()) && fields.size()<MAX_FIELDS)fields.add(immutable);
        BlockState crop=world.getBlockState(soil.up());
        if(supportedCrop(crop))cropSeeds.put(immutable.asLong(),seed(crop));
    }
    /** Each tick observes at most 16 columns and 8 candidate soils per column.
     * Rings visit nearby fields first and eventually cover the original radius. */
    private void scan(ServerWorld world){
        if(npc.age<nextScan || fields.size()>=MAX_FIELDS)return;
        if(scanRadius>CARE_RADIUS){scanRadius=0;scanEdge=0;nextScan=npc.age+100;return;}
        for(int n=0;n<SCAN_COLUMNS && scanRadius<=CARE_RADIUS;n++){
            int[] delta=perimeter(scanRadius,scanEdge);
            if(++scanEdge>=Math.max(1,scanRadius*8)){scanEdge=0;scanRadius++;}
            if(delta[0]*delta[0]+delta[1]*delta[1]>CARE_RADIUS*CARE_RADIUS)continue;
            BlockPos horizontal=new BlockPos(origin.getX()+delta[0],origin.getY(),origin.getZ()+delta[1]);
            if(!world.isChunkLoaded(horizontal))continue;
            for(int dy=-4;dy<=2;dy++)rememberField(world,horizontal.up(dy));
            int top=world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,horizontal.getX(),horizontal.getZ())-1;
            if(Math.abs(top-origin.getY())>4 && top>world.getBottomY() && top<world.getTopY()-1)
                rememberField(world,new BlockPos(horizontal.getX(),top,horizontal.getZ()));
        }
    }
    private static int[] perimeter(int radius,int edge){
        if(radius==0)return new int[]{0,0};
        int side=radius*2,e=edge%(side*4);
        if(e<side)return new int[]{-radius+e,-radius};e-=side;
        if(e<side)return new int[]{radius,-radius+e};e-=side;
        if(e<side)return new int[]{radius-e,radius};e-=side;
        return new int[]{-radius,radius-e};
    }
    private boolean delay(BlockPos pos,int ticks){
        if(!pos.equals(action)){action=pos.toImmutable();actionTicks=0;}
        npc.markWorking();npc.getLookControl().lookAt(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5,30,30);
        return ++actionTicks>=ticks;
    }
    private void doneAction(){action=null;actionTicks=0;}
    private boolean consume(Item item){
        for(int slot=0;slot<npc.backpack.size();slot++)if(npc.backpack.getStack(slot).isOf(item)){
            npc.backpack.removeStack(slot,1);npc.backpack.markDirty();return true;
        }
        if(npc.getMainHandStack().isOf(item)){npc.getMainHandStack().decrement(1);return true;}
        return false;
    }
    private boolean capacity(List<ItemStack> drops){
        SimpleInventory test=new SimpleInventory(npc.backpack.size());
        for(int i=0;i<npc.backpack.size();i++)test.setStack(i,npc.backpack.getStack(i).copy());
        for(ItemStack drop:drops)if(!test.addStack(drop.copy()).isEmpty())return false;
        return true;
    }
    private boolean collectHarvest(ServerWorld world){
        if(pendingHarvest==null)return false;
        List<ItemEntity> drops=world.getEntitiesByClass(ItemEntity.class,new Box(pendingHarvest).expand(4),
            drop->drop.isAlive() && drop.getCommandTags().contains("aiplayer_drop_"+npc.getUuid()));
        if(drops.isEmpty()){pendingHarvest=null;return false;}
        if(!npc.autoLoot){status="Ative a coleta de drops para recolher a colheita";return true;}
        ItemEntity nearest=drops.stream().min(java.util.Comparator.comparingDouble(npc::squaredDistanceTo)).orElseThrow();
        if(!npc.backpack.canInsert(nearest.getStack())){status="Libere espaço para recolher a colheita";return true;}
        status="Recolher fisicamente os produtos antes de replantar";
        if(npc.age-harvestSince>240){harvestSince=npc.age;work.pause();return true;}
        if(!npc.survival().canCollect(nearest) && npc.age>=nextDropMove){
            nextDropMove=npc.age+8;WorkNavigation.moveToDrop(npc,nearest,1.0);
        }
        return true;
    }
    private void plant(ServerWorld world,BlockPos soil,boolean requireSeed){
        BlockPos pos=soil.up();
        Item seed=cropSeeds.get(soil.asLong());if(seed==null)seed=availableSeed();
        if(seed==null || materials.count(seed)==0){
            status=requireSeed?"Preciso de sementes reais para este canteiro":"Canteiro preparado sem sementes disponíveis";
            doneAction();fieldCursor++;return;
        }
        if(!work.approach(pos))return;
        if(!world.getBlockState(pos).isAir() || !delay(pos,8))return;
        if(!careField(world,soil) || materials.count(seed)==0)return;
        if(world.setBlockState(pos,plantedState(seed),3)){
            consume(seed);cropSeeds.put(soil.asLong(),seed);planted++;fieldCursor++;
            if(create)OwnedBlocks.get(world).claim(pos,npc.getUuid());
            npc.swingHand(Hand.MAIN_HAND);npc.animateInteraction(CompanionEntity.PLACE,6);
        }
        doneAction();
    }
    private void tend(ServerWorld world){
        scan(world);
        if(collectHarvest(world))return;
        if(fields.isEmpty()){status="Procurar canteiros e plantações existentes na área confirmada";return;}
        for(int n=0;n<Math.min(32,fields.size());n++){
            if(fieldCursor>=fields.size())fieldCursor=0;
            BlockPos soil=fields.get(fieldCursor),pos=soil.up();
            if(!careField(world,soil)){fieldCursor++;doneAction();continue;}
            BlockState crop=world.getBlockState(pos);
            if(crop.isAir()){plant(world,soil,true);return;}
            cropSeeds.put(soil.asLong(),seed(crop));
            if(!((CropBlock)crop.getBlock()).isMature(crop)){fieldCursor++;doneAction();continue;}
            if(!npc.autoLoot){status="Ative coleta de drops antes de colher";return;}
            if(!work.approach(pos))return;
            if(!delay(pos,10))return;
            if(!careField(world,soil) || !world.getBlockState(pos).equals(crop))return;
            List<ItemStack> drops=Block.getDroppedStacks(crop,world,pos,null,npc,npc.getMainHandStack());
            if(!capacity(drops)){status="Libere espaço antes da colheita";return;}
            if(world.breakBlock(pos,false,npc)){
                for(ItemStack stack:drops){
                    ItemEntity drop=new ItemEntity(world,pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5,stack);
                    drop.setToDefaultPickupDelay();drop.addCommandTag("aiplayer_drop_"+npc.getUuid());world.spawnEntity(drop);
                }
                pendingHarvest=pos.toImmutable();harvestSince=npc.age;npc.swingHand(Hand.MAIN_HAND);
            }
            doneAction();return;
        }
        status="Cuidar da plantação: aguardar crescimento e replantar espaços livres";
    }

    private BlockPos cell(int index){return anchor.add(index/SIZE,0,index%SIZE);}
    private boolean edge(BlockPos pos){
        int x=pos.getX()-anchor.getX(),z=pos.getZ()-anchor.getZ();return x==0||x==SIZE-1||z==0||z==SIZE-1;
    }
    private boolean channel(BlockPos pos){int z=pos.getZ()-anchor.getZ();return pos.getX()==anchor.getX()+3 && z>=2 && z<=4;}
    private List<BlockPos> channels(){return List.of(anchor.add(3,0,2),anchor.add(3,0,3),anchor.add(3,0,4));}
    private boolean natural(BlockState state){
        return state.isAir() || state.isIn(BlockTags.DIRT) || state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES)
            || state.isIn(BlockTags.FLOWERS) || state.isOf(Blocks.GRASS) || state.isOf(Blocks.TALL_GRASS)
            || state.isOf(Blocks.FERN) || state.isOf(Blocks.LARGE_FERN) || state.isOf(Blocks.SNOW)
            || state.isOf(Blocks.SNOW_BLOCK) || state.isOf(Blocks.STONE) || state.isOf(Blocks.GRAVEL)
            || state.isOf(Blocks.SAND) || state.isOf(Blocks.FARMLAND);
    }
    private boolean editAllowed(ServerWorld world,BlockPos pos){
        return world.isChunkLoaded(pos) && !foreign(world,pos) && !OwnedBlocks.get(world).protectedBlock(pos)
            && world.getBlockEntity(pos)==null;
    }
    private void survey(ServerWorld world){
        for(int i=0;i<SIZE*SIZE;i++){
            BlockPos ground=cell(i);
            for(int y=-1;y<=3;y++){
                BlockPos p=ground.up(y);
                if(!editAllowed(world,p) || !natural(world.getBlockState(p)) || !world.getFluidState(p).isEmpty()){
                    status="Escolha outro local: preservar construção, água ou terreno protegido";return;
                }
            }
            boolean support=false;
            for(int depth=1;depth<=4;depth++){
                BlockPos below=ground.down(depth);
                if(!world.isChunkLoaded(below))break;
                if(world.getBlockState(below).isSolidBlock(world,below)){support=true;break;}
            }
            if(!support){status="Este desnível precisa de outro local para a plantação";return;}
        }
        phase=Phase.CLEAR;cursor=0;status="Limpar vegetação, neve e desníveis naturais";
    }
    private void clear(ServerWorld world){
        if(cursor>=SIZE*SIZE*3){phase=Phase.FOUNDATION;cursor=0;return;}
        int layer=3-cursor/(SIZE*SIZE);BlockPos pos=cell(cursor%(SIZE*SIZE)).up(layer);
        if(world.getBlockState(pos).isAir()){cursor++;return;}
        if(!prepareMining(world.getBlockState(pos)))return;
        if(work.safeMine(pos))cursor++;else status=work.status();
    }
    /** Terrain preparation has its own tool prerequisite, before the bucket
     * dependency tree starts. A worn tool does not count as a replacement. */
    private boolean prepareMining(BlockState state){
        if(!state.isToolRequired() || state.isOf(Blocks.SNOW) || state.isOf(Blocks.SNOW_BLOCK))return true;
        ItemStack held=npc.getMainHandStack();
        if(held.getItem() instanceof PickaxeItem && !ResourcePlan.worn(held) && held.isSuitableFor(state))return true;
        boolean suitable=false;
        for(int i=0;i<npc.backpack.size();i++){
            ItemStack stack=npc.backpack.getStack(i);
            if(stack.getItem() instanceof PickaxeItem && !ResourcePlan.worn(stack) && stack.isSuitableFor(state)){
                suitable=true;break;
            }
        }
        if(suitable){
            if(npc.autoEquip)return true;
            status="Equipe a picareta utilizável antes de preparar este terreno";return false;
        }
        // ensure counts actual stacks, so request one additional pick if the
        // only wooden picks are worn. Keep those stacks until the new recipe
        // has really consumed ingredients and produced its replacement.
        if(!materials.ensure(Items.WOODEN_PICKAXE,materials.count(Items.WOODEN_PICKAXE)+1))status=materials.status();
        else status=npc.autoEquip?"Preparar picareta para remover o obstáculo natural":"Equipe a picareta fabricada para preparar o terreno";
        return false;
    }
    private void foundation(ServerWorld world){
        if(cursor>=SIZE*SIZE){phase=Phase.GROUND;cursor=0;return;}
        BlockPos ground=cell(cursor);
        // Existing natural ground supports the surface even over a cave.
        // Only an actual hole needs paid fill. Water cells get their basin
        // checked after excavation, when the NPC can physically reach it.
        if(world.getBlockState(ground).isSolidBlock(world,ground)
                || world.getBlockState(ground).isOf(Blocks.FARMLAND)){cursor++;return;}
        if(supportBelow(world,ground))cursor++;
    }
    private boolean supportBelow(ServerWorld world,BlockPos ground){
        int support=0;
        for(int depth=1;depth<=4;depth++){
            BlockPos below=ground.down(depth);
            if(!world.isChunkLoaded(below)){status="Aguardar terreno carregado";return false;}
            if(world.getBlockState(below).isSolidBlock(world,below)){support=depth;break;}
            if(!world.getBlockState(below).isAir()){
                status="Preservar apoio que não pode ser aterrado";return false;
            }
        }
        if(support==0){status="Desnível sem apoio seguro para a plantação";return false;}
        if(support==1)return true;
        BlockPos missing=ground.down(support-1);
        if(!materials.ensure(Items.DIRT,1)){status=materials.status();return false;}
        if(work.place(missing,Blocks.DIRT.getDefaultState(),Items.DIRT,1))completed++;else status=work.status();
        return false;
    }
    private void ground(ServerWorld world){
        if(cursor>=SIZE*SIZE){phase=Phase.WATER;cursor=0;fields.clear();known.clear();return;}
        BlockPos pos=cell(cursor);BlockState state=world.getBlockState(pos);
        Item required=edge(pos)?border:Items.DIRT;
        BlockState wanted=edge(pos)?((net.minecraft.item.BlockItem)border).getBlock().getDefaultState():Blocks.DIRT.getDefaultState();
        if(!edge(pos) && !channel(pos) && (state.isIn(BlockTags.DIRT) || state.isOf(Blocks.FARMLAND))){cursor++;return;}
        // Keep the channel dry until all of its holes and paid floor supports
        // have been prepared together, before the first bucket is poured.
        if(channel(pos)){cursor++;return;}
        if(state.isOf(wanted.getBlock()) && OwnedBlocks.get(world).owned(pos,npc.getUuid())){cursor++;return;}
        if(!state.isAir()){
            if(!prepareMining(state))return;
            if(!work.safeMine(pos)){status=work.status();return;}
        }
        if(!materials.ensure(required,1)){status=materials.status();return;}
        if(work.place(pos,wanted,required,1)){cursor++;completed++;}else status=work.status();
    }
    private boolean stillWater(ServerWorld world,BlockPos pos){
        return pos!=null && world.isChunkLoaded(pos) && world.getBlockState(pos).isOf(Blocks.WATER)
            && world.getFluidState(pos).isStill();
    }
    private boolean sourceAllowed(ServerWorld world,BlockPos pos){
        return stillWater(world,pos) && avoidedSources.getOrDefault(pos.asLong(),0)<=npc.age
            && !foreign(world,pos) && !OwnedBlocks.get(world).protectedBlock(pos)
            && !(pos.getX()>=anchor.getX() && pos.getX()<anchor.getX()+SIZE
                && pos.getZ()>=anchor.getZ() && pos.getZ()<anchor.getZ()+SIZE);
    }
    private boolean visibleWater(ServerWorld world,BlockPos pos){
        Vec3d start=npc.getEyePos(),end=new Vec3d(pos.getX()+0.5,pos.getY()+0.999,pos.getZ()+0.5);
        int steps=Math.max(1,(int)Math.ceil(start.distanceTo(end)/4));
        for(int i=0;i<=steps;i++)if(!world.isChunkLoaded(BlockPos.ofFloored(start.lerp(end,(double)i/steps))))return false;
        var hit=world.raycast(new RaycastContext(start,end,RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.SOURCE_ONLY,npc));
        return hit.getType()==HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }
    private void useSource(BlockPos pos){waterSurvey.stop();source=pos.toImmutable();sourceSince=npc.age;sourceDistance=Double.MAX_VALUE;}
    private void findWater(ServerWorld world){
        if(source!=null && !world.isChunkLoaded(source)){work.approach(source);return;}
        if(sourceAllowed(world,source))return;
        source=null;
        var remembered=npc.intelligence().memory().nearest("water",npc.getBlockPos(),world.getTime(),48);
        if(remembered.isPresent() && sourceAllowed(world,remembered.get()) && visibleWater(world,remembered.get())){useSource(remembered.get());return;}
        if(waterScanOrigin==null || waterScanOrigin.getSquaredDistance(npc.getBlockPos())>16*16){
            waterScanOrigin=npc.getBlockPos().toImmutable();waterRadius=0;waterEdge=0;waterY=0;
        }
        for(int n=0;n<384 && waterRadius<=64;n++){
            int[] delta=perimeter(waterRadius,waterEdge);
            BlockPos p=waterScanOrigin.add(delta[0],waterY-12,delta[1]);
            if(++waterY>=25){waterY=0;if(++waterEdge>=Math.max(1,8*waterRadius)){waterEdge=0;waterRadius++;}}
            if(sourceAllowed(world,p) && visibleWater(world,p)){useSource(p);return;}
        }
        status="Procurar uma fonte natural de água carregada para encher o balde";
        if(waterRadius>32 || waterSurvey.active()){
            waterSurvey.tick(false);
            if(waterRadius>64){waterScanOrigin=null;waterRadius=0;}
        }
    }

    /** Prepare the whole dry channel first. Pouring into one completed cell
     * before excavating the next would let water enter an unpaid floor gap. */
    private boolean prepareBasins(ServerWorld world){
        List<BlockPos> canal=channels();
        boolean waterPresent=false,missingFloor=false;
        for(BlockPos pos:canal){
            if(!editAllowed(world,pos) || !world.isChunkLoaded(pos.down())){
                status="Preservar terreno protegido durante o preparo do canal";return false;
            }
            waterPresent|=!world.getFluidState(pos).isEmpty();
            missingFloor|=!world.getBlockState(pos.down()).isSolidBlock(world,pos.down());
        }
        if(waterPresent && missingFloor){
            // A partially flooded older save cannot be excavated safely:
            // preserve its real water instead of deleting it or filling fluid.
            status="O canal antigo precisa de apoio seco antes de continuar a irrigação";return false;
        }
        if(basinCursor>=canal.size()){
            basinPrepared=true;status="Bacia inteira preparada para receber água";return true;
        }
        BlockPos pos=canal.get(basinCursor);BlockState state=world.getBlockState(pos);
        if(!state.isAir() && !state.isOf(Blocks.WATER)){
            if(!prepareMining(state))return false;
            if(!work.safeMine(pos))status=work.status();
            return false;
        }
        if(!supportBelow(world,pos))return false;
        basinCursor++;doneAction();return false;
    }
    private void water(ServerWorld world){
        if(!basinPrepared && !prepareBasins(world))return;
        if(cursor>=channels().size()){
            phase=Phase.PLANT;cursor=0;fieldCursor=0;
            for(int i=0;i<SIZE*SIZE;i++){BlockPos p=cell(i);if(!edge(p)&&!channel(p))fields.add(p);}
            return;
        }
        BlockPos pos=channels().get(cursor);
        if(stillWater(world,pos) && OwnedBlocks.get(world).owned(pos,npc.getUuid())){cursor++;return;}
        if(world.isChunkLoaded(pos) && (!editAllowed(world,pos) || !world.getBlockState(pos.down()).isSolidBlock(world,pos.down()))){
            basinPrepared=false;basinCursor=0;status="Conferir novamente o apoio do canal";return;
        }
        if(world.isChunkLoaded(pos) && !world.getBlockState(pos).isAir() && !world.getBlockState(pos).isOf(Blocks.WATER)){
            basinPrepared=false;basinCursor=0;status="Preparar novamente o canal antes de buscar água";return;
        }
        if(materials.count(Items.WATER_BUCKET)==0){
            if(!materials.ensure(Items.BUCKET,1)){status=materials.status();return;}
            findWater(world);
            if(source==null)return;
            double distance=npc.squaredDistanceTo(Vec3d.ofCenter(source));
            if(distance<sourceDistance-0.5){sourceDistance=distance;sourceSince=npc.age;}
            if(npc.age-sourceSince>240){
                if(avoidedSources.size()>=64)avoidedSources.clear();
                avoidedSources.put(source.asLong(),npc.age+600);source=null;work.pause();return;
            }
            if(!work.approach(source))return;
            if(!delay(source,8) || !sourceAllowed(world,source))return;
            BlockState state=world.getBlockState(source);
            if(!(state.getBlock() instanceof FluidDrainable drainable))return;
            if(materials.count(Items.BUCKET)<1)return;
            consume(Items.BUCKET);
            ItemStack water=drainable.tryDrainFluid(world,source,state);
            if(!water.isOf(Items.WATER_BUCKET)){
                ItemStack remainder=npc.backpack.addStack(new ItemStack(Items.BUCKET));
                if(!remainder.isEmpty())npc.dropStack(remainder);return;
            }
            ItemStack remainder=npc.backpack.addStack(water);if(!remainder.isEmpty())npc.dropStack(remainder);
            source=null;doneAction();status="Levar o mesmo balde cheio até o canal";return;
        }
        if(!editAllowed(world,pos) || !world.getBlockState(pos.down()).isSolidBlock(world,pos.down())){
            status="Preservar terreno e apoio antes de colocar água";return;
        }
        for(net.minecraft.util.math.Direction side:net.minecraft.util.math.Direction.Type.HORIZONTAL){
            BlockPos neighbor=pos.offset(side);
            if(!world.isChunkLoaded(neighbor)
                    || !(world.getBlockState(neighbor).isSolidBlock(world,neighbor)
                        || world.getBlockState(neighbor).isOf(Blocks.FARMLAND)
                        || (channel(neighbor) && editAllowed(world,neighbor)
                            && (world.getBlockState(neighbor).isAir() || world.getBlockState(neighbor).isOf(Blocks.WATER))
                            && world.getBlockState(neighbor.down()).isSolidBlock(world,neighbor.down())))){
                status="Reparar as laterais do canal antes de colocar água";return;
            }
        }
        if(!work.approach(pos) || !delay(pos,8))return;
        if(materials.count(Items.WATER_BUCKET)==0)return;
        if(world.setBlockState(pos,Blocks.WATER.getDefaultState(),3)){
            consume(Items.WATER_BUCKET);ItemStack remainder=npc.backpack.addStack(new ItemStack(Items.BUCKET));
            if(!remainder.isEmpty())npc.dropStack(remainder);
            OwnedBlocks.get(world).claim(pos,npc.getUuid());cursor++;completed++;npc.swingHand(Hand.MAIN_HAND);
        }
        doneAction();
    }
    private boolean hoe(){
        if(npc.getMainHandStack().getItem() instanceof HoeItem && !ResourcePlan.worn(npc.getMainHandStack()))return true;
        if(npc.autoEquip)for(int i=0;i<npc.backpack.size();i++){
            ItemStack s=npc.backpack.getStack(i);
            if(s.getItem() instanceof HoeItem && !ResourcePlan.worn(s)){
                ItemStack old=npc.getMainHandStack();npc.equipStack(EquipmentSlot.MAINHAND,npc.backpack.removeStack(i));
                npc.backpack.setStack(i,old);npc.backpack.markDirty();return true;
            }
        }
        if(!materials.ensure(Items.WOODEN_HOE,1))status=materials.status();
        else status="Equipe uma enxada utilizável para preparar os canteiros";
        return false;
    }
    private void finishPlanting(ServerWorld world){
        if(fieldCursor>=fields.size()){
            active=false;phase=Phase.DONE;
            status="Plantação irrigada pronta: "+planted+" canteiros semeados; vou voltar a seguir você";
            work.pause();npc.setMode(Mode.FOLLOW);return;
        }
        BlockPos soil=fields.get(fieldCursor),crop=soil.up();
        if(foreign(world,soil) || foreign(world,crop) || !world.isChunkLoaded(crop)){status="Preservar outro proprietário";return;}
        BlockState ground=world.getBlockState(soil);
        if(!ground.isOf(Blocks.FARMLAND)){
            if(!hoe())return;
            if(!work.approach(crop) || !delay(soil,8))return;
            if(!world.getBlockState(crop).isAir() || !world.getBlockState(soil).isIn(BlockTags.DIRT))return;
            if(world.setBlockState(soil,Blocks.FARMLAND.getDefaultState().with(FarmlandBlock.MOISTURE,7),3)){
                npc.getMainHandStack().damage(1,npc,e->e.sendToolBreakStatus(Hand.MAIN_HAND));
                OwnedBlocks.get(world).claim(soil,npc.getUuid());npc.swingHand(Hand.MAIN_HAND);
            }
            doneAction();return;
        }
        if(!world.getBlockState(crop).isAir()){fieldCursor++;return;}
        plant(world,soil,false);
    }
    public void tick(){
        if(!active || origin==null || !(npc.getWorld() instanceof ServerWorld world))return;
        if(!dimension.equals(world.getRegistryKey().getValue().toString())){cancel();return;}
        if(npc.menuOpen() || npc.isEating() || npc.getTarget()!=null){pause();return;}
        if(!rules(world))return;
        if(create && !materials.resumePending()){status=materials.status();return;}
        boolean fetchingWater=create && phase==Phase.WATER && basinPrepared
            && cursor<channels().size() && materials.count(Items.WATER_BUCKET)==0;
        if(create && !fetchingWater && (!world.isChunkLoaded(anchor) || !world.isChunkLoaded(anchor.add(SIZE-1,0,SIZE-1)))){
            status="Voltar à plantação para retomar o trabalho";work.approach(anchor);return;
        }
        if(create)switch(phase){
            case SURVEY->survey(world);case CLEAR->clear(world);case FOUNDATION->foundation(world);
            case GROUND->ground(world);case WATER->water(world);case PLANT->finishPlanting(world);default->{ }
        }else tend(world);
    }
    public void writeNbt(NbtCompound root){
        NbtCompound n=new NbtCompound();n.putInt("Version",1);n.putBoolean("Active",active);n.putBoolean("Create",create);
        n.putString("Dimension",dimension);n.putString("Phase",phase.name());n.putString("Status",status);
        if(origin!=null)n.putLong("Origin",origin.asLong());if(anchor!=null)n.putLong("Anchor",anchor.asLong());
        n.putInt("Cursor",cursor);n.putInt("Completed",completed);n.putInt("Planted",planted);
        NbtCompound waterSearch=new NbtCompound();waterSurvey.writeNbt(waterSearch);n.put("WaterSurvey",waterSearch);
        n.putBoolean("BasinPrepared",basinPrepared);n.putInt("BasinCursor",basinCursor);
        n.putString("Border",Registries.ITEM.getId(border).toString());
        n.putLongArray("Fields",fields.stream().mapToLong(BlockPos::asLong).toArray());
        NbtList seeds=new NbtList();
        cropSeeds.forEach((position,item)->{
            NbtCompound entry=new NbtCompound();entry.putLong("Pos",position);
            entry.putString("Item",Registries.ITEM.getId(item).toString());seeds.add(entry);
        });n.put("CropSeeds",seeds);
        if(pendingHarvest!=null)n.putLong("PendingHarvest",pendingHarvest.asLong());
        n.putInt("ScanRadius",scanRadius);n.putInt("ScanEdge",scanEdge);root.put("FarmProject",n);
    }
    public void readNbt(NbtCompound root){
        cancel();if(!root.contains("FarmProject"))return;
        NbtCompound n=root.getCompound("FarmProject");
        if(n.getInt("Version")!=1 || !n.getBoolean("Active") || !n.contains("Origin"))return;
        dimension=n.getString("Dimension");
        if(!dimension.equals(npc.getWorld().getRegistryKey().getValue().toString()))return;
        origin=BlockPos.fromLong(n.getLong("Origin"));anchor=n.contains("Anchor")?BlockPos.fromLong(n.getLong("Anchor")):origin.add(2,-1,0);
        if(!npc.getWorld().getWorldBorder().contains(origin) || npc.getWorld().isOutOfHeightLimit(origin)
                || !npc.getWorld().getWorldBorder().contains(anchor) || npc.getWorld().isOutOfHeightLimit(anchor)){
            origin=null;return;
        }
        create=n.getBoolean("Create");
        try{phase=Phase.valueOf(n.getString("Phase"));}catch(IllegalArgumentException bad){return;}
        cursor=Math.max(0,Math.min(SIZE*SIZE*3,n.getInt("Cursor")));completed=Math.max(0,n.getInt("Completed"));planted=Math.max(0,n.getInt("Planted"));
        waterSurvey.readNbt(n.getCompound("WaterSurvey"));
        basinPrepared=n.getBoolean("BasinPrepared");basinCursor=Math.max(0,Math.min(channels().size(),n.getInt("BasinCursor")));
        Identifier id=Identifier.tryParse(n.getString("Border"));
        if(id!=null){
            Item item=Registries.ITEM.get(id);
            for(HouseBlueprint.Palette palette:HouseBlueprint.palettes())if(palette.log()==item){
                border=item;materials.selectWood(palette.log(),palette.planks());break;
            }
        }
        for(long packed:n.getLongArray("Fields")){
            BlockPos field=BlockPos.fromLong(packed);
            if(fields.size()<MAX_FIELDS && within(field.up()) && known.add(packed))fields.add(field);
        }
        NbtList seeds=n.getList("CropSeeds",NbtElement.COMPOUND_TYPE);
        for(int i=0;i<Math.min(MAX_FIELDS,seeds.size());i++){
            NbtCompound entry=seeds.getCompound(i);long position=entry.getLong("Pos");
            Identifier item=Identifier.tryParse(entry.getString("Item"));
            if(known.contains(position) && item!=null){
                Item seed=Registries.ITEM.get(item);
                if(seed==Items.WHEAT_SEEDS || seed==Items.CARROT || seed==Items.POTATO || seed==Items.BEETROOT_SEEDS)
                    cropSeeds.put(position,seed);
            }
        }
        if(n.contains("PendingHarvest")){
            BlockPos pending=BlockPos.fromLong(n.getLong("PendingHarvest"));if(within(pending))pendingHarvest=pending;
        }
        scanRadius=Math.max(0,Math.min(CARE_RADIUS,n.getInt("ScanRadius")));scanEdge=Math.max(0,n.getInt("ScanEdge"));
        status=n.getString("Status");active=phase!=Phase.DONE;harvestSince=npc.age;
        if(!create)work.setBoundary(origin,CARE_RADIUS);else {work.clearBoundary();stationsOutsideFarm();}
    }
}
