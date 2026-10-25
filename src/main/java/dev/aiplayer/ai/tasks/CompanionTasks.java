package dev.aiplayer.ai.tasks;

import dev.aiplayer.ai.OwnedBlocks;
import dev.aiplayer.ai.PhysicalMining;
import dev.aiplayer.ai.ResourceGathering;
import dev.aiplayer.ai.WorkNavigation;
import dev.aiplayer.ai.brain.ResourcePlan;
import dev.aiplayer.ai.navigation.NpcPathfinder;
import dev.aiplayer.entity.CompanionEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Server-side processes for the NPC. Planning never grants resources or changes
 * the world remotely: every build, recipe, till, plant and harvest is executed
 * in reach, on the server thread, using the real inventory and ownership ledger. */
public final class CompanionTasks {
    public enum Kind { NONE, BUILD_SHELTER, FARM, EXPLORE }
    private static final int BUILD_DELAY=6, FARM_DELAY=12, CRAFT_DELAY=12;
    private static final int[][] EXPLORE_OFFSETS={{8,0},{6,6},{0,8},{-6,6},{-8,0},{-6,-6},{0,-8},{6,-6}};
    private final CompanionEntity npc;
    private final PhysicalMining mining;
    private Kind kind=Kind.NONE;
    private BlockPos anchor, actionTarget, pendingHarvest, routeTarget, routeAction;
    private Vec3d routePosition, routePoint;
    private String dimension="", status="Sem projeto";
    private int index, actionTicks, nextRoute, retryAt, routeFailures, explored;
    private int materialRetryAt, exploreRetryAt, harvestSince, routeSince;
    private boolean gatheringWood, builderPositioned;
    private Item plankSource;
    private final List<BlockPos> fields=new ArrayList<>();

    public CompanionTasks(CompanionEntity npc){this.npc=npc;mining=new PhysicalMining(npc);}
    public boolean active(){return kind!=Kind.NONE;}
    public Kind kind(){return kind;}
    public String status(){return status;}
    public String diagnostics(){
        Path path=npc.getNavigation().getCurrentPath();
        return "index="+index+" anchor="+anchor+" action="+actionTarget+" stance="+routeTarget
            +" point="+routePoint+" inside="+builderPositioned+" idle="+npc.getNavigation().isIdle()
            +" path="+(path==null?"none":path.getCurrentNodeIndex()+"/"+path.getLength()+" end="+path.getEnd())
            +" "+NpcPathfinder.diagnostics(npc);
    }
    public int progress(){return kind==Kind.EXPLORE?explored:index;}

    public void start(Kind requested){
        cancel();
        if(requested==Kind.NONE || !(npc.getWorld() instanceof ServerWorld))return;
        npc.gathering().cancel();
        kind=requested;dimension=npc.getWorld().getRegistryKey().getValue().toString();
        anchor=npc.getBlockPos().toImmutable();
        if(kind==Kind.BUILD_SHELTER)anchor=anchor.add(2,0,0);
        index=0;explored=0;retryAt=0;materialRetryAt=0;exploreRetryAt=0;
        status=switch(kind){case BUILD_SHELTER->"Planejar abrigo de madeira";case FARM->"Procurar solo irrigado";case EXPLORE->"Explorar oito locais carregados";default->"Sem projeto";};
    }
    public void cancel(){
        if(gatheringWood){npc.gathering().cancel();gatheringWood=false;}
        mining.reset();npc.getNavigation().stop();
        kind=Kind.NONE;anchor=null;fields.clear();actionTarget=null;pendingHarvest=null;actionTicks=0;plankSource=null;
        routeTarget=null;routeAction=null;routePosition=null;routePoint=null;builderPositioned=false;
        index=0;explored=0;routeFailures=0;status="Projeto cancelado";
    }
    public void pause(){
        mining.reset();
        if(gatheringWood)npc.gathering().pause();
        routeTarget=null;routeAction=null;routePosition=null;routePoint=null;
        npc.clearWorkingVisual();
    }
    public void tick(){
        if(!active() || npc.ownerId()==null || !(npc.getWorld() instanceof ServerWorld world))return;
        if(!dimension.equals(world.getRegistryKey().getValue().toString())){cancel();status="Projeto cancelado: dimensão alterada";return;}
        if(npc.menuOpen() || npc.isEating() || npc.getTarget()!=null){pause();return;}
        if(npc.age<retryAt)return;
        if(gatheringWood){
            if(npc.gathering().active()){
                status="Obter madeira para o projeto: "+npc.gathering().progress()+"/"+npc.gathering().goal();
                npc.gathering().resume();npc.gathering().tick();return;
            }
            if(npc.gathering().hasCleanup()){
                npc.gathering().cleanupTick();return;
            }
            gatheringWood=false;materialRetryAt=npc.age+200;
        }
        switch(kind){case BUILD_SHELTER->build(world);case FARM->farm(world);case EXPLORE->explore(world);default->{}}
    }

    private boolean rules(ServerWorld world){
        if(world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)
                && world.getGameRules().getBoolean(GameRules.DO_TILE_DROPS))return true;
        status="As regras do mundo impedem executar o projeto";return false;
    }
    private boolean protectedPosition(ServerWorld world,BlockPos pos){
        var ownership=OwnedBlocks.get(world);
        return ownership.protectedBlock(pos) || (ownership.claimed(pos) && !ownership.owned(pos,npc.getUuid()));
    }
    private boolean inReach(BlockPos pos,Vec3d eyes){
        Vec3d center=Vec3d.ofCenter(pos);
        if(eyes.squaredDistanceTo(center)>3.5*3.5)return false;
        var hit=npc.getWorld().raycast(new RaycastContext(eyes,center,
            RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,npc));
        return hit.getType()==HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }
    private boolean standable(ServerWorld world,BlockPos feet){
        return world.isChunkLoaded(feet) && world.isChunkLoaded(feet.up())
            && WorkNavigation.safeWorkFooting(npc,feet)
            && world.isSpaceEmpty(npc,new Box(feet.getX()+0.2,feet.getY()+0.01,feet.getZ()+0.2,
                feet.getX()+0.8,feet.getY()+1.8,feet.getZ()+0.8));
    }
    private record Stance(BlockPos feet,Vec3d point) {}
    private boolean workHazard(ServerWorld world,BlockPos pos){
        BlockState state=world.getBlockState(pos);
        return state.isOf(Blocks.LAVA) || state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE)
            || state.isOf(Blocks.MAGMA_BLOCK) || state.isOf(Blocks.CACTUS) || state.isOf(Blocks.CAMPFIRE)
            || state.isOf(Blocks.SOUL_CAMPFIRE) || state.isOf(Blocks.POWDER_SNOW)
            || state.isOf(Blocks.WITHER_ROSE) || state.isOf(Blocks.SWEET_BERRY_BUSH);
    }
    private Vec3d stancePoint(ServerWorld world,BlockPos target,BlockPos feet){
        if(!standable(world,feet))return null;
        Vec3d center=Vec3d.ofBottomCenter(feet);
        double dx=Math.signum(target.getX()-feet.getX())*0.18;
        double dz=Math.signum(target.getZ()-feet.getZ())*0.18;
        for(Vec3d point:new Vec3d[]{center,center.add(dx,0,dz),center.add(dx,0,0),center.add(0,0,dz)}){
            double half=npc.getWidth()/2.0;
            Box body=new Box(point.x-half,point.y+0.01,point.z-half,
                point.x+half,point.y+npc.getHeight(),point.z+half);
            Vec3d eyes=point.add(0,npc.getStandingEyeHeight(),0);
            if(body.intersects(new Box(target)) || !world.isSpaceEmpty(npc,body) || !inReach(target,eyes))continue;
            // Roof corners can offer only an exact diagonal ray between two
            // already placed blocks. Require room for the real moving worker,
            // rather than choosing a mathematically visible, unusable center.
            if(kind==Kind.BUILD_SHELTER && target.getY()==anchor.getY()+3
                    && (!inReach(target,eyes.add(0.035,0,0)) || !inReach(target,eyes.add(-0.035,0,0))
                        || !inReach(target,eyes.add(0,0,0.035)) || !inReach(target,eyes.add(0,0,-0.035))))continue;
            return point;
        }
        return null;
    }
    /** Finish the last fraction of a validated stance using ordinary collision
     * and velocity. Navigation's 0.45-block arrival tolerance is too broad for
     * construction sight lines; this never skips a route or changes height. */
    private boolean centerOnStance(ServerWorld world,BlockPos feet,Vec3d point){
        BlockPos actual=BlockPos.ofFloored(npc.getX(),npc.getY()+0.5,npc.getZ());
        if(point==null || !npc.isOnGround() || !actual.equals(feet)
                || Math.abs(npc.getY()-point.y)>0.12 || !standable(world,feet))return false;
        double dx=point.x-npc.getX(),dz=point.z-npc.getZ();
        double distance=Math.sqrt(dx*dx+dz*dz);
        if(distance<0.01)return false;
        Box swept=npc.getBoundingBox().stretch(dx,0,dz);
        if(!world.isSpaceEmpty(npc,swept))return false;
        for(BlockPos cell:BlockPos.iterate(BlockPos.ofFloored(swept.minX,swept.minY+0.001,swept.minZ),
                BlockPos.ofFloored(swept.maxX-0.001,swept.maxY-0.001,swept.maxZ-0.001))){
            BlockPos footing=new BlockPos(cell.getX(),feet.getY(),cell.getZ());
            if(!world.isChunkLoaded(cell) || !world.getFluidState(cell).isEmpty()
                    || workHazard(world,cell) || workHazard(world,footing.down())
                    || !WorkNavigation.safeWorkFooting(npc,footing))return false;
        }
        npc.getNavigation().stop();
        npc.getMoveControl().moveTo(npc.getX(),npc.getY(),npc.getZ(),0);
        npc.setForwardSpeed(0);npc.setSidewaysSpeed(0);
        double motion=Math.min(0.08,Math.max(0.012,distance*0.4));
        npc.setVelocity(dx/distance*motion,npc.getVelocity().y,dz/distance*motion);
        return true;
    }
    private boolean approach(ServerWorld world,BlockPos target){
        // Never place a block through the worker's body. Move to a real stance
        // before construction or before tilling the soil under the future crop.
        if(!npc.getBoundingBox().intersects(new Box(target)) && inReach(target,npc.getEyePos())){
            npc.getNavigation().stop();routeFailures=0;routeTarget=null;routeAction=null;routePoint=null;return true;
        }
        // A functioning route owns movement until it reaches a useful stance.
        // Replacing it every few ticks can strand the worker at floor edges.
        if(routeTarget!=null && target.equals(routeAction) && standable(world,routeTarget)){
            if(centerOnStance(world,routeTarget,routePoint))return false;
            if(!npc.getNavigation().isIdle()){
                if(routePosition==null || npc.getPos().squaredDistanceTo(routePosition)>0.25){
                    routePosition=npc.getPos();routeSince=npc.age;
                }
                if(npc.age-routeSince<50)return false;
            }
        }
        routeTarget=null;routeAction=null;routePoint=null;
        if(npc.age<nextRoute)return false;
        nextRoute=npc.age+12;
        List<Stance> options=new ArrayList<>();
        for(int dy:new int[]{0,1,-1})for(int radius=0;radius<=3;radius++)
            for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
                if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
                BlockPos feet=new BlockPos(target.getX()+dx,npc.getBlockY()+dy,target.getZ()+dz);
                if(kind==Kind.BUILD_SHELTER && index>=25 && !interiorStance(feet))continue;
                Vec3d point=stancePoint(world,target,feet);
                if(point!=null)options.add(new Stance(feet,point));
            }
        options.sort(Comparator.comparingDouble(p->p.feet().getSquaredDistance(npc.getBlockPos())));
        for(int i=0;i<Math.min(8,options.size());i++){
            Stance stance=options.get(i);
            if(centerOnStance(world,stance.feet(),stance.point())){
                routeTarget=stance.feet().toImmutable();routeAction=target.toImmutable();
                routePoint=stance.point();routePosition=npc.getPos();routeSince=npc.age;return false;
            }
            Path path=WorkNavigation.findPath(npc,stance.feet(),0);
            if(path!=null && WorkNavigation.surfacePath(npc,path,stance.feet().getY())){
                // Vanilla retains a finished equivalent path instead of
                // replacing it. Explicitly discard it for a deliberate retry.
                npc.getNavigation().stop();
                if(!npc.getNavigation().startMovingAlong(path,1.0))continue;
                routeTarget=stance.feet().toImmutable();routeAction=target.toImmutable();routePoint=stance.point();
                routePosition=npc.getPos();routeSince=npc.age;return false;
            }
        }
        routeFailures++;
        if(routeFailures>=4){retryAt=npc.age+60;routeFailures=0;status="Aguardar caminho seguro para o projeto";}
        return false;
    }
    private boolean delayed(BlockPos target,int ticks){
        if(actionTarget==null || !actionTarget.equals(target)){actionTarget=target.toImmutable();actionTicks=0;}
        npc.markWorking();npc.getLookControl().lookAt(target.getX()+0.5,target.getY()+0.5,target.getZ()+0.5,30,30);
        return ++actionTicks>=ticks;
    }
    private void actionDone(){actionTarget=null;actionTicks=0;plankSource=null;routeTarget=null;routeAction=null;routePoint=null;}

    private boolean interiorStance(BlockPos feet){
        return feet.getY()==anchor.getY()+1 && feet.getX()>=anchor.getX()+1 && feet.getX()<=anchor.getX()+3
            && feet.getZ()>=anchor.getZ()+1 && feet.getZ()<=anchor.getZ()+3;
    }
    private boolean enterShelter(ServerWorld world){
        BlockPos center=anchor.add(2,1,2);
        if(builderPositioned && interiorStance(npc.getBlockPos()))return true;
        builderPositioned=false;
        if(npc.squaredDistanceTo(Vec3d.ofBottomCenter(center))<=0.6*0.6){
            builderPositioned=true;npc.getNavigation().stop();routeTarget=null;routeAction=null;routePoint=null;return true;
        }
        status="Entrar no abrigo antes de colocar teto e paredes";
        if(routeTarget!=null && routeTarget.equals(center) && !npc.getNavigation().isIdle()){
            if(routePosition==null || npc.getPos().squaredDistanceTo(routePosition)>0.25){
                routePosition=npc.getPos();routeSince=npc.age;
            }
            if(npc.age-routeSince<50)return false;
        }
        if(npc.age<nextRoute)return false;
        nextRoute=npc.age+12;
        if(!standable(world,center))return false;
        Path path=WorkNavigation.findPath(npc,center,0);
        if(path!=null && WorkNavigation.surfacePath(npc,path,center.getY())){
            npc.getNavigation().stop();
            if(npc.getNavigation().startMovingAlong(path,1.0)){
                routeTarget=center;routeAction=null;routePoint=null;routePosition=npc.getPos();routeSince=npc.age;
            }
        }
        return false;
    }

    /** Build from the inside: floor, four real corner supports, roof, then the
     * remaining walls. The worker never closes its own route before roofing. */
    private List<BlockPos> blueprint(){
        List<BlockPos> blocks=new ArrayList<>(80);
        for(int x=0;x<5;x++)for(int z=0;z<5;z++)blocks.add(anchor.add(x,0,z));
        for(int x:new int[]{0,4})for(int z:new int[]{0,4})for(int y=1;y<=2;y++)blocks.add(anchor.add(x,y,z));
        // Cap the four posts before either neighboring roof edge closes its
        // sight line, then roof the outer rim and work inward.
        for(int x:new int[]{0,4})for(int z:new int[]{0,4})blocks.add(anchor.add(x,3,z));
        for(int radius=2;radius>=0;radius--)for(int x=0;x<5;x++)for(int z=0;z<5;z++)
            if(Math.max(Math.abs(x-2),Math.abs(z-2))==radius
                    && !((x==0 || x==4) && (z==0 || z==4)))blocks.add(anchor.add(x,3,z));
        for(int y=1;y<=2;y++)for(int x=0;x<5;x++)for(int z=0;z<5;z++)
            if((x==0 || x==4 || z==0 || z==4) && !(x==2 && z==0)
                    && !((x==0 || x==4) && (z==0 || z==4)))blocks.add(anchor.add(x,y,z));
        return blocks;
    }
    private int plankSlot(){
        for(int i=0;i<npc.backpack.size();i++)if(npc.backpack.getStack(i).isIn(ItemTags.PLANKS)
            && npc.backpack.getStack(i).getItem() instanceof BlockItem)return i;
        return -1;
    }
    private Item planksFor(Item item){
        Identifier id=Registries.ITEM.getId(item);
        if(!id.getNamespace().equals("minecraft"))return null;
        String path=id.getPath();if(path.startsWith("stripped_"))path=path.substring(9);
        for(String suffix:new String[]{"_log","_wood","_stem","_hyphae"})if(path.endsWith(suffix)){
            Item planks=Registries.ITEM.get(new Identifier("minecraft",path.substring(0,path.length()-suffix.length())+"_planks"));
            return planks!=Items.AIR?planks:null;
        }
        return null;
    }
    private boolean craftPlanks(){
        if(!npc.autoCraft){status="Ative fabricação automática ou entregue tábuas";return false;}
        int slot=-1;Item output=null;
        for(int i=0;i<npc.backpack.size();i++){
            ItemStack stack=npc.backpack.getStack(i);
            if(stack.isIn(ItemTags.LOGS) && (output=planksFor(stack.getItem()))!=null){slot=i;break;}
        }
        if(slot<0)return false;
        Item source=npc.backpack.getStack(slot).getItem();
        if(plankSource!=source){plankSource=source;actionTarget=null;actionTicks=0;}
        status="Converter tronco em quatro tábuas";
        npc.getNavigation().stop();npc.markWorking();npc.animateInteraction(CompanionEntity.CRAFT,3);
        if(++actionTicks<CRAFT_DELAY)return true;
        SimpleInventory capacity=new SimpleInventory(npc.backpack.size());
        for(int i=0;i<npc.backpack.size();i++)capacity.setStack(i,npc.backpack.getStack(i).copy());
        capacity.removeStack(slot,1);
        if(!capacity.addStack(new ItemStack(output,4)).isEmpty()){
            status="Mochila sem espaço para as tábuas";actionTicks=0;return true;
        }
        npc.backpack.removeStack(slot,1);npc.backpack.addStack(new ItemStack(output,4));npc.backpack.markDirty();
        actionDone();return true;
    }
    private void build(ServerWorld world){
        if(!rules(world))return;
        List<BlockPos> plan=blueprint();
        while(index<plan.size()){
            BlockPos pos=plan.get(index);
            if(!world.isChunkLoaded(pos)){status="Aguardar terreno carregado para o abrigo";return;}
            if(OwnedBlocks.get(world).owned(pos,npc.getUuid()) && world.getBlockState(pos).isIn(BlockTags.PLANKS)){index++;continue;}
            break;
        }
        if(index>=plan.size()){kind=Kind.NONE;status="Abrigo concluído: 80 blocos reais";npc.intelligence().memory().succeeded(status);return;}
        if(index>=25 && !enterShelter(world))return;
        BlockPos pos=plan.get(index);
        if(protectedPosition(world,pos) || !world.getBlockState(pos).isAir() || world.getBlockEntity(pos)!=null){
            status="Abrigo bloqueado: preservar bloco existente em "+pos.toShortString();return;
        }
        int slot=plankSlot();
        if(slot<0){
            if(craftPlanks())return;
            if(!npc.autoCraft || npc.age<materialRetryAt){status="Aguardar tábuas ou troncos para continuar o abrigo";return;}
            int logs=Math.min(32,(plan.size()-index+3)/4+4);
            npc.gathering().start(ResourceGathering.Kind.WOOD,logs,true);gatheringWood=npc.gathering().active();
            materialRetryAt=npc.age+200;status="Obter madeira para construir o abrigo";return;
        }
        if(!approach(world,pos))return;
        if(!world.getEntitiesByClass(LivingEntity.class,new Box(pos),LivingEntity::isAlive).isEmpty()){
            status="Aguardar passagem livre para colocar bloco";return;
        }
        boolean attached=false;
        for(Direction d:Direction.values()){
            BlockPos neighbor=pos.offset(d);
            if(world.isChunkLoaded(neighbor) && world.getBlockState(neighbor).isSolidBlock(world,neighbor)){attached=true;break;}
        }
        if(!attached){status="Aguardar apoio sólido para o abrigo";return;}
        status="Construir abrigo: "+index+"/"+plan.size();
        if(!delayed(pos,BUILD_DELAY))return;
        ItemStack stack=npc.backpack.getStack(slot);
        if(!(stack.getItem() instanceof BlockItem block) || !stack.isIn(ItemTags.PLANKS)){actionDone();return;}
        if(world.setBlockState(pos,block.getBlock().getDefaultState(),3)){
            npc.backpack.removeStack(slot,1);npc.backpack.markDirty();OwnedBlocks.get(world).claim(pos,npc.getUuid());
            npc.animateInteraction(CompanionEntity.PLACE,8);npc.swingHand(Hand.MAIN_HAND);index++;
        }
        actionDone();
    }

    private boolean waterNearby(ServerWorld world,BlockPos soil){
        for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++)for(int dy=0;dy<=1;dy++){
            BlockPos water=soil.add(dx,dy,dz);
            if(world.isChunkLoaded(water) && world.getFluidState(water).isIn(FluidTags.WATER))return true;
        }
        return false;
    }
    private boolean farmSoil(ServerWorld world,BlockPos soil){
        if(!world.isChunkLoaded(soil) || !world.isChunkLoaded(soil.up()) || protectedPosition(world,soil)
                || protectedPosition(world,soil.up()) || !waterNearby(world,soil))return false;
        BlockState ground=world.getBlockState(soil),plant=world.getBlockState(soil.up());
        return (ground.isOf(Blocks.DIRT) || ground.isOf(Blocks.GRASS_BLOCK)
                || (ground.isOf(Blocks.FARMLAND) && OwnedBlocks.get(world).owned(soil,npc.getUuid())))
            && (plant.isAir() || (plant.isOf(Blocks.WHEAT) && OwnedBlocks.get(world).owned(soil.up(),npc.getUuid())));
    }
    private void findFields(ServerWorld world){
        List<BlockPos> choices=new ArrayList<>();
        for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++){
            BlockPos pos=anchor.down().add(dx,0,dz);
            if(farmSoil(world,pos))choices.add(pos);
        }
        choices.sort(Comparator.comparingDouble(p->p.getSquaredDistance(npc.getBlockPos())));
        fields.clear();fields.addAll(choices.subList(0,Math.min(9,choices.size())));
    }
    private boolean equipHoe(){
        if(npc.getMainHandStack().getItem() instanceof HoeItem && !ResourcePlan.worn(npc.getMainHandStack()))return true;
        if(!npc.autoEquip)return false;
        for(int i=0;i<npc.backpack.size();i++){
            ItemStack stack=npc.backpack.getStack(i);
            if(stack.getItem() instanceof HoeItem && !ResourcePlan.worn(stack)){
                ItemStack held=npc.getMainHandStack();npc.equipStack(EquipmentSlot.MAINHAND,npc.backpack.removeStack(i));
                npc.backpack.setStack(i,held);npc.backpack.markDirty();npc.animateInteraction(CompanionEntity.EQUIP,5);return true;
            }
        }
        return false;
    }
    private int itemSlot(Item item){for(int i=0;i<npc.backpack.size();i++)if(npc.backpack.getStack(i).isOf(item))return i;return -1;}
    private void farm(ServerWorld world){
        if(!rules(world))return;
        if(pendingHarvest!=null){
            List<ItemEntity> drops=world.getEntitiesByClass(ItemEntity.class,new Box(pendingHarvest).expand(3),
                item->item.isAlive() && item.getCommandTags().contains("aiplayer_drop_"+npc.getUuid())
                    && (item.getStack().isOf(Items.WHEAT) || item.getStack().isOf(Items.WHEAT_SEEDS)));
            if(!drops.isEmpty()){
                ItemEntity drop=drops.stream().min(Comparator.comparingDouble(npc::squaredDistanceTo)).orElseThrow();
                if(!npc.autoLoot || !npc.backpack.canInsert(drop.getStack())){
                    status="Ative coleta de drops e libere espaço para a colheita";return;
                }
                status="Recolher fisicamente trigo e sementes antes de replantar";
                if(npc.age-harvestSince>160){
                    harvestSince=npc.age;retryAt=npc.age+60;npc.getNavigation().stop();return;
                }
                if(!npc.survival().canCollect(drop) && npc.age>=nextRoute){
                    nextRoute=npc.age+10;WorkNavigation.moveToDrop(npc,drop,1.0);
                }
                return;
            }
            pendingHarvest=null;
        }
        if(fields.isEmpty())findFields(world);
        if(fields.isEmpty()){status="Preciso de solo natural e água a até quatro blocos";retryAt=npc.age+40;return;}
        for(int attempts=0;attempts<fields.size();attempts++){
            if(index>=fields.size())index=0;
            BlockPos soil=fields.get(index),crop=soil.up();
            if(!farmSoil(world,soil)){index++;actionDone();continue;}
            BlockState plant=world.getBlockState(crop),ground=world.getBlockState(soil);
            if(plant.isOf(Blocks.WHEAT)){
                if(!((CropBlock)Blocks.WHEAT).isMature(plant)){index++;continue;}
                if(!approach(world,crop))return;
                status="Colher trigo maduro e recolher os drops";
                if(mining.tick(crop)){pendingHarvest=crop.toImmutable();harvestSince=npc.age;actionDone();}
                return;
            }
            if(itemSlot(Items.WHEAT_SEEDS)<0){status="Preciso de sementes reais de trigo";return;}
            if(!approach(world,crop))return;
            if(!ground.isOf(Blocks.FARMLAND)){
                if(!equipHoe()){
                    boolean wood=false;
                    for(int slot=0;slot<npc.backpack.size();slot++){
                        ItemStack material=npc.backpack.getStack(slot);
                        if(material.isIn(ItemTags.LOGS) || material.isIn(ItemTags.PLANKS)){wood=true;break;}
                    }
                    if(npc.autoCraft && !wood && npc.age>=materialRetryAt){
                        npc.gathering().start(ResourceGathering.Kind.WOOD,6,true);
                        gatheringWood=npc.gathering().active();materialRetryAt=npc.age+200;
                    }
                    status=npc.autoCraft?"Preparar madeira e fabricar uma enxada":"Preciso de uma enxada utilizável";return;
                }
                status="Preparar solo irrigado";
                if(!delayed(soil,FARM_DELAY))return;
                if(world.setBlockState(soil,Blocks.FARMLAND.getDefaultState().with(FarmlandBlock.MOISTURE,7),3)){
                    OwnedBlocks.get(world).claim(soil,npc.getUuid());
                    npc.getMainHandStack().damage(1,npc,e->e.sendToolBreakStatus(Hand.MAIN_HAND));
                    npc.swingHand(Hand.MAIN_HAND);
                }
                actionDone();return;
            }
            status="Plantar semente de trigo";
            if(!delayed(crop,FARM_DELAY))return;
            int seeds=itemSlot(Items.WHEAT_SEEDS);
            if(seeds>=0 && world.getBlockState(crop).isAir() && world.setBlockState(crop,Blocks.WHEAT.getDefaultState(),3)){
                npc.backpack.removeStack(seeds,1);npc.backpack.markDirty();
                OwnedBlocks.get(world).claim(crop,npc.getUuid());npc.animateInteraction(CompanionEntity.PLACE,8);index++;
            }
            actionDone();return;
        }
        status="Aguardar trigo amadurecer";retryAt=npc.age+20;
    }

    private void observe(ServerWorld world){
        npc.intelligence().memory().remember("explored",npc.getBlockPos(),world.getTime(),24000);
        int observed=0;
        BlockPos center=npc.getBlockPos();
        for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++)for(int dy=-1;dy<=3;dy++){
            if(observed>=12)return;
            BlockPos pos=center.add(dx,dy,dz);
            if(!world.isChunkLoaded(pos) || !inReach(pos,npc.getEyePos()) || protectedPosition(world,pos))continue;
            BlockState state=world.getBlockState(pos);String resource=null;
            if(state.isIn(BlockTags.LOGS))resource="tree";
            else if(state.isOf(Blocks.STONE))resource="surface_stone";
            else if(state.isOf(Blocks.WHEAT))resource="farm";
            if(resource!=null){npc.intelligence().memory().remember(resource,pos,world.getTime(),12000);observed++;}
        }
    }
    private BlockPos explorationDestination(ServerWorld world,int[] offset){
        BlockPos column=anchor.add(offset[0],0,offset[1]);
        for(int dy:new int[]{0,1,-1,2,-2,3,-3}){
            BlockPos feet=column.up(dy);
            if(standable(world,feet))return feet;
        }
        return null;
    }
    private void explore(ServerWorld world){
        if(index>=EXPLORE_OFFSETS.length){
            kind=Kind.NONE;status="Exploração encerrada: "+explored+"/8 locais visitados";
            npc.getNavigation().stop();npc.intelligence().memory().succeeded(status);return;
        }
        BlockPos destination=explorationDestination(world,EXPLORE_OFFSETS[index]);
        if(destination==null){
            index++;actionDone();status="Preservar área descarregada ou sem piso seguro";return;
        }
        if(npc.squaredDistanceTo(Vec3d.ofBottomCenter(destination))<1.4*1.4){
            observe(world);explored++;index++;routeFailures=0;exploreRetryAt=0;
            npc.getNavigation().stop();return;
        }
        if(npc.age<nextRoute)return;
        nextRoute=npc.age+16;
        Path path=WorkNavigation.findPath(npc,destination,0);
        if(path!=null && WorkNavigation.surfacePath(npc,path,destination.getY()) && npc.getNavigation().startMovingAlong(path,1.0)){
            status="Explorar local "+(index+1)+"/8";
            if(exploreRetryAt==0)exploreRetryAt=npc.age+240;
            else if(npc.age>=exploreRetryAt){index++;exploreRetryAt=0;}
        }else if(++routeFailures>=3){index++;routeFailures=0;exploreRetryAt=0;status="Local sem rota segura; procurar outro";}
    }

    public void writeNbt(NbtCompound root){
        if(!active())return;
        NbtCompound n=new NbtCompound();n.putString("Kind",kind.name());n.putString("Dimension",dimension);
        if(anchor!=null)n.putLong("Anchor",anchor.asLong());n.putInt("Index",index);n.putInt("Explored",explored);
        n.putBoolean("GatheringWood",gatheringWood);n.putString("Status",status);
        n.putInt("BlueprintVersion",3);
        if(pendingHarvest!=null)n.putLong("PendingHarvest",pendingHarvest.asLong());
        n.putLongArray("Fields",fields.stream().mapToLong(BlockPos::asLong).toArray());
        root.put("CompanionProject",n);
    }
    public void readNbt(NbtCompound root){
        kind=Kind.NONE;anchor=null;pendingHarvest=null;fields.clear();actionDone();gatheringWood=false;
        routePosition=null;builderPositioned=false;mining.reset();
        if(!root.contains("CompanionProject"))return;
        NbtCompound n=root.getCompound("CompanionProject");
        try{kind=Kind.valueOf(n.getString("Kind"));}catch(IllegalArgumentException ignored){return;}
        dimension=n.getString("Dimension");
        if(kind==Kind.NONE || !n.contains("Anchor") || !dimension.equals(npc.getWorld().getRegistryKey().getValue().toString())){
            kind=Kind.NONE;status="Projeto não restaurado nesta dimensão";return;
        }
        anchor=BlockPos.fromLong(n.getLong("Anchor"));
        if(anchor.getSquaredDistance(npc.getBlockPos())>48*48){kind=Kind.NONE;status="Projeto distante; aguardar nova ordem";return;}
        index=Math.max(0,Math.min(kind==Kind.BUILD_SHELTER?80:kind==Kind.EXPLORE?8:9,n.getInt("Index")));
        // Older in-progress blueprints used a different placement order. Scan
        // them again; already owned blocks are retained and consume no items.
        if(kind==Kind.BUILD_SHELTER && n.getInt("BlueprintVersion")!=3)index=0;
        explored=Math.max(0,Math.min(8,n.getInt("Explored")));gatheringWood=n.getBoolean("GatheringWood")
            && (kind==Kind.BUILD_SHELTER || kind==Kind.FARM);
        status=n.getString("Status");
        for(long packed:n.getLongArray("Fields")){
            BlockPos field=BlockPos.fromLong(packed);
            if(fields.size()<9 && field.getSquaredDistance(anchor)<=8*8 && Math.abs(field.getY()-anchor.getY())<=2)fields.add(field);
        }
        if(kind==Kind.FARM && n.contains("PendingHarvest")){
            BlockPos harvest=BlockPos.fromLong(n.getLong("PendingHarvest"));
            if(harvest.getSquaredDistance(anchor)<=12*12){pendingHarvest=harvest;harvestSince=npc.age;}
        }
        nextRoute=0;retryAt=0;materialRetryAt=0;exploreRetryAt=0;routeFailures=0;
    }
}
