package dev.aiplayer.ai.tasks;

import dev.aiplayer.ai.OwnedBlocks;
import dev.aiplayer.ai.PhysicalMining;
import dev.aiplayer.ai.WorkNavigation;
import dev.aiplayer.entity.CompanionEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.state.property.Property;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
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

/** Shared physical executor for projects. Planning never teleports, grants
 * materials, force-loads chunks, or substitutes a computed path for arrival. */
public final class ProjectWork {
    private final CompanionEntity npc;
    private final PhysicalMining mining;
    private BlockPos goal, stance, boundary, placement;
    private Vec3d point, last;
    private String purpose="", status="Preparar trabalho";
    private int nextRoute, stagnantSince, placeTicks, radius;
    private record Stance(BlockPos feet,Vec3d point) {}

    public ProjectWork(CompanionEntity npc){this.npc=npc;mining=new PhysicalMining(npc);}
    public String status(){return status;}
    public String diagnostics(){
        Path path=npc.getNavigation().getCurrentPath();
        String sight="none";
        if(goal!=null && allowed(goal) && npc.squaredDistanceTo(Vec3d.ofCenter(goal))<=48*48){
            var hit=npc.getWorld().raycast(new RaycastContext(npc.getEyePos(),Vec3d.ofCenter(goal),
                RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,npc));
            sight=hit.getType()==HitResult.Type.MISS?"clear":hit.getBlockPos().toShortString();
        }
        return "work="+status+" goal="+goal+" stance="+stance+" point="+point+" purpose="+purpose
            +" path="+(path==null?"none":path.getCurrentNodeIndex()+"/"+path.getLength()+" end="+path.getEnd())
            +" idle="+npc.getNavigation().isIdle()+" onGround="+npc.isOnGround()+" pos="+npc.getPos()
            +" sight="+sight+" stalled="+(npc.age-stagnantSince)+" routeIn="+Math.max(0,nextRoute-npc.age);
    }
    /** Horizontal area around the original order; height is checked separately. */
    public void setBoundary(BlockPos origin,int radius){boundary=origin.toImmutable();this.radius=Math.max(1,Math.min(100,radius));resetRoute();}
    public void clearBoundary(){boundary=null;radius=0;resetRoute();}
    public void pause(){mining.reset();placement=null;placeTicks=0;resetRoute();npc.clearWorkingVisual();}
    public void cancel(){pause();boundary=null;radius=0;nextRoute=0;status="Trabalho cancelado";}
    private ServerWorld world(){return npc.getWorld() instanceof ServerWorld server?server:null;}
    private boolean validDestination(BlockPos pos){
        ServerWorld world=world();
        return world!=null && pos.getY()>=world.getBottomY() && pos.getY()<world.getTopY()-2
            && world.getWorldBorder().contains(pos) && insideBoundary(pos);
    }
    private boolean allowed(BlockPos pos){
        return validDestination(pos) && world().isChunkLoaded(pos);
    }
    private boolean insideBoundary(BlockPos pos){
        if(boundary==null)return true;
        long dx=(long)pos.getX()-boundary.getX(),dz=(long)pos.getZ()-boundary.getZ();
        return dx*dx+dz*dz<=(long)radius*radius;
    }
    private void resetRoute(){goal=null;stance=null;point=null;last=null;purpose="";npc.getNavigation().stop();}
    private boolean rules(){
        ServerWorld world=world();
        if(world!=null && world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)
                && world.getGameRules().getBoolean(GameRules.DO_TILE_DROPS))return true;
        status="As regras do mundo impedem este trabalho";return false;
    }
    public boolean protectedPosition(BlockPos pos){
        ServerWorld world=world();
        if(world==null || !allowed(pos))return true;
        OwnedBlocks ledger=OwnedBlocks.get(world);
        return ledger.protectedBlock(pos) || (ledger.claimed(pos) && !ledger.owned(pos,npc.getUuid()));
    }
    private boolean hazard(BlockPos pos){
        if(!allowed(pos))return true;
        BlockState state=npc.getWorld().getBlockState(pos);
        return state.isOf(Blocks.LAVA) || state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE)
            || state.isOf(Blocks.MAGMA_BLOCK) || state.isOf(Blocks.CACTUS) || state.isOf(Blocks.CAMPFIRE)
            || state.isOf(Blocks.SOUL_CAMPFIRE) || state.isOf(Blocks.POWDER_SNOW)
            || state.isOf(Blocks.WITHER_ROSE) || state.isOf(Blocks.SWEET_BERRY_BUSH);
    }
    private boolean standable(BlockPos feet){
        ServerWorld world=world();
        if(!allowed(feet) || !allowed(feet.up()) || !allowed(feet.down())
                || hazard(feet) || hazard(feet.up()) || hazard(feet.down())
                || !world.getFluidState(feet).isEmpty() || !world.getFluidState(feet.up()).isEmpty()
                || !WorkNavigation.safeWorkFooting(npc,feet))return false;
        double half=npc.getWidth()/2.0;
        return world.isSpaceEmpty(npc,new Box(feet.getX()+0.5-half,feet.getY()+0.001,feet.getZ()+0.5-half,
            feet.getX()+0.5+half,feet.getY()+npc.getHeight(),feet.getZ()+0.5+half));
    }
    private boolean inReach(BlockPos target,Vec3d eyes){
        if(!allowed(target))return false;
        Vec3d center=Vec3d.ofCenter(target);
        if(eyes.squaredDistanceTo(center)>3.5*3.5)return false;
        var hit=npc.getWorld().raycast(new RaycastContext(eyes,center,RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,npc));
        return hit.getType()==HitResult.Type.MISS || hit.getBlockPos().equals(target);
    }
    private Vec3d workPoint(BlockPos target,BlockPos feet){
        if(!standable(feet))return null;
        Vec3d center=Vec3d.ofBottomCenter(feet);
        double dx=Math.signum(target.getX()-feet.getX())*0.18,dz=Math.signum(target.getZ()-feet.getZ())*0.18;
        for(Vec3d at:new Vec3d[]{center,center.add(dx,0,dz),center.add(dx,0,0),center.add(0,0,dz)}){
            double half=npc.getWidth()/2.0;
            Box body=new Box(at.x-half,at.y+0.001,at.z-half,at.x+half,at.y+npc.getHeight(),at.z+half);
            Vec3d eyes=at.add(0,npc.getStandingEyeHeight(),0);
            if(!body.intersects(new Box(target)) && world().isSpaceEmpty(npc,body)
                    && inReach(target,eyes)
                    // Exact rays between two block corners can be clear only
                    // at a mathematically perfect center. Require enough sight
                    // margin for navigation's real fractional arrival point.
                    && inReach(target,eyes.add(0.035,0,0)) && inReach(target,eyes.add(-0.035,0,0))
                    && inReach(target,eyes.add(0,0,0.035)) && inReach(target,eyes.add(0,0,-0.035)))return at;
        }
        return null;
    }
    private boolean center(BlockPos feet,Vec3d at){
        if(at==null || !npc.isOnGround()
                || !BlockPos.ofFloored(npc.getX(),npc.getY()+0.5,npc.getZ()).equals(feet)
                || Math.abs(npc.getY()-at.y)>0.12 || !standable(feet))return false;
        double dx=at.x-npc.getX(),dz=at.z-npc.getZ(),distance=Math.sqrt(dx*dx+dz*dz);
        if(distance<0.012)return false;
        Box sweep=npc.getBoundingBox().stretch(dx,0,dz);
        for(BlockPos cell:BlockPos.iterate(BlockPos.ofFloored(sweep.minX,sweep.minY+0.001,sweep.minZ),
                BlockPos.ofFloored(sweep.maxX-0.001,sweep.maxY-0.001,sweep.maxZ-0.001))){
            if(!allowed(cell) || hazard(cell) || !world().getFluidState(cell).isEmpty())return false;
        }
        // Both centers are within this same supported feet cell. A grounded
        // actor may already overhang the edge of a raised floor; requiring
        // equal-height ground under that existing overhang rejects a safe
        // inward adjustment and leaves a one-node path unable to finish it.
        // standable(feet) validates the destination, while this complete swept
        // body still has to be collision-, fluid- and hazard-free.
        if(!world().isSpaceEmpty(npc,sweep))return false;
        npc.getNavigation().stop();npc.getMoveControl().moveTo(npc.getX(),npc.getY(),npc.getZ(),0);
        npc.setForwardSpeed(0);npc.setSidewaysSpeed(0);
        double motion=Math.min(0.08,Math.max(0.012,distance*0.4));
        npc.setVelocity(dx/distance*motion,npc.getVelocity().y,dz/distance*motion);
        return true;
    }
    private boolean retaining(BlockPos target,String action){
        if(!target.equals(goal) || !purpose.equals(action) || stance==null || !standable(stance))return false;
        if(action.equals("work") && center(stance,point))return true;
        if(npc.getNavigation().isIdle())return false;
        if(last==null || npc.getPos().squaredDistanceTo(last)>0.20){last=npc.getPos();stagnantSince=npc.age;}
        return npc.age-stagnantSince<60;
    }
    private boolean start(BlockPos target,String action,Stance candidate){
        if(action.equals("work") && center(candidate.feet(),candidate.point())){
            goal=target.toImmutable();purpose=action;stance=candidate.feet();point=candidate.point();last=npc.getPos();stagnantSince=npc.age;return true;
        }
        Path path=WorkNavigation.findPath(npc,candidate.feet(),0,
            dev.aiplayer.ai.navigation.NpcPathfinder.Mode.WORK,this::allowed);
        if(path==null || !WorkNavigation.surfacePath(npc,path,candidate.feet().getY()))return false;
        npc.getNavigation().stop();
        if(!npc.getNavigation().startMovingAlong(path,1.0))return false;
        goal=target.toImmutable();purpose=action;stance=candidate.feet().toImmutable();point=candidate.point();last=npc.getPos();stagnantSince=npc.age;
        return true;
    }
    public boolean approach(BlockPos target){
        if(!validDestination(target)){status="Alvo fora da área autorizada";return false;}
        // A remembered station/site may be outside the currently loaded area.
        // Travel through local terrain; only inspect/interact with it on arrival.
        if(!allowed(target)){moveTo(target);return false;}
        if(!npc.getBoundingBox().intersects(new Box(target)) && inReach(target,npc.getEyePos())){
            resetRoute();return true;
        }
        status="Chegar fisicamente ao local de trabalho";
        if(Math.abs(npc.getBlockX()-target.getX())>30 || Math.abs(npc.getBlockZ()-target.getZ())>30){
            moveTo(target);return false;
        }
        if(retaining(target,"work"))return false;
        if(npc.age<nextRoute)return false;
        nextRoute=npc.age+10;
        List<Stance> options=new ArrayList<>();
        for(int dy:new int[]{0,1,-1,2,-2,3,-3})for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++){
            BlockPos feet=new BlockPos(target.getX()+dx,npc.getBlockY()+dy,target.getZ()+dz);
            Vec3d at=workPoint(target,feet);if(at!=null)options.add(new Stance(feet,at));
        }
        options.sort(Comparator.comparingDouble(candidate->candidate.point().squaredDistanceTo(npc.getPos())));
        for(int i=0;i<Math.min(12,options.size());i++)if(start(target,"work",options.get(i)))return false;
        status="Aguardar caminho seguro até o trabalho";return false;
    }
    /** Routes in local loaded segments. The result means actual arrival at the
     * requested feet cell, never successful calculation of an intermediate path. */
    public boolean moveTo(BlockPos destination){
        if(!validDestination(destination)){status="Destino fora da área autorizada";return false;}
        if(Math.abs(npc.getY()-destination.getY())<0.25
                && npc.squaredDistanceTo(Vec3d.ofBottomCenter(destination))<0.65){resetRoute();return true;}
        // Navigation can finish within the requested cell while its fractional
        // position still falls just outside this executor's arrival radius.
        // There is no strictly closer cell to replan to in that case.
        if(center(destination,Vec3d.ofBottomCenter(destination)))return false;
        status="Caminhar por etapas até o destino";
        if(retaining(destination,"walk"))return false;
        if(npc.age<nextRoute)return false;nextRoute=npc.age+10;
        int dx=destination.getX()-npc.getBlockX(),dz=destination.getZ()-npc.getBlockZ();
        double length=Math.max(Math.abs(dx),Math.abs(dz));
        double factor=length>24?24.0/length:1.0;
        BlockPos toward=new BlockPos(npc.getBlockX()+(int)Math.round(dx*factor),
            factor==1?destination.getY():npc.getBlockY(),npc.getBlockZ()+(int)Math.round(dz*factor));
        List<Stance> choices=new ArrayList<>();
        for(int dy:new int[]{0,1,-1,2,-2,3,-3,4,-4})for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){
            BlockPos feet=toward.add(x,dy,z);
            if(!standable(feet) || feet.getSquaredDistance(destination)>=npc.getBlockPos().getSquaredDistance(destination)
                    || Math.max(Math.abs(feet.getX()-npc.getBlockX()),Math.abs(feet.getZ()-npc.getBlockZ()))>32)continue;
            choices.add(new Stance(feet,Vec3d.ofBottomCenter(feet)));
        }
        choices.sort(Comparator.comparingDouble(candidate->candidate.feet().getSquaredDistance(destination)));
        for(int i=0;i<Math.min(12,choices.size());i++)if(start(destination,"walk",choices.get(i)))return false;
        status="Aguardar uma etapa carregada e segura do caminho";return false;
    }
    public boolean replaceable(BlockPos pos){
        if(!allowed(pos))return false;
        BlockState state=world().getBlockState(pos);
        return state.isAir() || vegetation(state) || state.isOf(Blocks.SNOW);
    }
    private boolean vegetation(BlockState state){
        return state.isOf(Blocks.GRASS) || state.isOf(Blocks.TALL_GRASS) || state.isOf(Blocks.FERN)
            || state.isOf(Blocks.LARGE_FERN) || state.isOf(Blocks.DEAD_BUSH) || state.isOf(Blocks.VINE)
            || state.isIn(BlockTags.FLOWERS) || state.isIn(BlockTags.LEAVES);
    }
    private boolean natural(BlockState state){
        return vegetation(state) || state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.DIRT)
            || state.isOf(Blocks.SNOW) || state.isOf(Blocks.SNOW_BLOCK) || state.isOf(Blocks.GRAVEL)
            || state.isOf(Blocks.SAND) || state.isOf(Blocks.RED_SAND) || state.isOf(Blocks.CLAY) || state.isOf(Blocks.STONE)
            || state.isOf(Blocks.COBBLESTONE) || state.isOf(Blocks.DEEPSLATE) || state.isOf(Blocks.COBBLED_DEEPSLATE)
            || state.isOf(Blocks.GRANITE) || state.isOf(Blocks.DIORITE) || state.isOf(Blocks.ANDESITE)
            || state.isOf(Blocks.COAL_ORE) || state.isOf(Blocks.DEEPSLATE_COAL_ORE)
            || state.isOf(Blocks.IRON_ORE) || state.isOf(Blocks.DEEPSLATE_IRON_ORE);
    }
    public boolean clearNatural(BlockPos pos){
        if(!allowed(pos))return false;
        BlockState state=world().getBlockState(pos);
        if(state.isAir())return true;
        if(!replaceable(pos)){status="O obstáculo precisa de preparação de terreno";return false;}
        return safeMine(pos);
    }
    private boolean supportsActor(BlockPos pos){
        BlockState state=world().getBlockState(pos);var shape=state.getCollisionShape(world(),pos);
        if(shape.isEmpty())return false;
        Box block=shape.getBoundingBox().offset(pos.getX(),pos.getY(),pos.getZ());
        for(LivingEntity actor:world().getEntitiesByClass(LivingEntity.class,new Box(pos).expand(0.5,2,0.5),LivingEntity::isAlive)){
            Box body=actor.getBoundingBox();
            if(Math.abs(actor.getY()-block.maxY)<0.16 && body.maxX>block.minX && body.minX<block.maxX
                    && body.maxZ>block.minZ && body.minZ<block.maxZ)return true;
        }
        return false;
    }
    /** Walk off a one-block support before asking gravity to lower the body.
     * A path's usual arrival tolerance can stop with one edge still supported
     * by the block, so this short descent must finish its physical movement. */
    private boolean descendFromSupport(BlockPos support,double top){
        if(!npc.isOnGround())return false;
        BlockPos above=BlockPos.ofFloored(support.getX()+0.5,top,support.getZ()+0.5);
        for(Direction side:Direction.Type.HORIZONTAL){
            BlockPos landing=above.offset(side).down();
            if(!standable(landing))continue;
            double dx=landing.getX()+0.5-npc.getX(),dz=landing.getZ()+0.5-npc.getZ();
            double descent=landing.getY()-npc.getY();
            if(descent < -1.05 || descent > -0.8)continue;
            Box sideways=npc.getBoundingBox().stretch(dx,0,dz);
            Box downward=npc.getBoundingBox().offset(dx,descent,dz).stretch(0,-descent,0);
            if(!world().isSpaceEmpty(npc,sideways) || !world().isSpaceEmpty(npc,downward))continue;
            boolean safe=true;Box bounds=sideways.union(downward);
            for(BlockPos cell:BlockPos.iterate(BlockPos.ofFloored(bounds.minX,bounds.minY+0.001,bounds.minZ),
                    BlockPos.ofFloored(bounds.maxX-0.001,bounds.maxY-0.001,bounds.maxZ-0.001))){
                if(!allowed(cell) || hazard(cell) || !world().getFluidState(cell).isEmpty()){
                    safe=false;break;
                }
            }
            double distance=Math.sqrt(dx*dx+dz*dz);
            if(!safe || distance<0.1)continue;
            resetRoute();
            npc.getMoveControl().moveTo(landing.getX()+0.5,landing.getY(),landing.getZ()+0.5,1.05);
            npc.setVelocity(dx/distance*0.21,npc.getVelocity().y,dz/distance*0.21);
            return true;
        }
        return false;
    }
    public boolean safeMine(BlockPos pos){
        if(!rules() || !allowed(pos))return false;
        BlockState state=world().getBlockState(pos);
        if(state.isAir())return true;
        if(protectedPosition(pos) || world().getBlockEntity(pos)!=null || !natural(state)
                || !world().getFluidState(pos).isEmpty() || hazard(pos)
                || !allowed(pos.up()) || protectedPosition(pos.up()) || world().getBlockEntity(pos.up())!=null){
            mining.reset();status="Preservar bloco protegido, construção ou apoio ocupado";return false;
        }
        if(supportsActor(pos)){
            mining.reset();status="Esperar ou sair do apoio antes de preparar terreno";
            var shape=state.getCollisionShape(world(),pos);
            double top=shape.isEmpty()?Double.NaN:pos.getY()+shape.getMax(Direction.Axis.Y);
            Box support=shape.isEmpty()?new Box(pos):shape.getBoundingBox().offset(pos.getX(),pos.getY(),pos.getZ());
            Box body=npc.getBoundingBox();
            if(Math.abs(npc.getY()-top)<0.16 && body.maxX>support.minX && body.minX<support.maxX
                    && body.maxZ>support.minZ && body.minZ<support.maxZ){
                BlockPos feet=BlockPos.ofFloored(pos.getX()+0.5,top,pos.getZ()+0.5);
                // A one-block mound has no supported adjacent cell at its top
                // height. Leave it through an ordinary validated step down;
                // never remove the block while the actor is still on it.
                for(Direction side:Direction.Type.HORIZONTAL){
                    BlockPos away=feet.offset(side);
                    if(standable(away)){
                        Stance exit=new Stance(away,Vec3d.ofBottomCenter(away));
                        if(!center(away,exit.point()) && !retaining(away,"walk"))start(away,"walk",exit);
                        return false;
                    }
                }
                descendFromSupport(pos,top);
            }
            return false;
        }
        if(state.isToolRequired() && !state.isOf(Blocks.SNOW) && !state.isOf(Blocks.SNOW_BLOCK)){
            npc.equipMiningTool(state);
            if(!npc.getMainHandStack().isSuitableFor(state)){status="Preciso da ferramenta adequada para este terreno";return false;}
        }
        if(!approach(pos)){mining.reset();return false;}
        status="Preparar terreno com quebra e drops reais";
        return mining.tick(pos);
    }
    private int count(Item item){
        return npc.backpack.count(item)+(npc.getMainHandStack().isOf(item)?npc.getMainHandStack().getCount():0);
    }
    private void consume(Item item,int quantity){
        int remaining=quantity;
        for(int i=0;i<npc.backpack.size() && remaining>0;i++){
            ItemStack stack=npc.backpack.getStack(i);if(!stack.isOf(item))continue;
            int n=Math.min(remaining,stack.getCount());npc.backpack.removeStack(i,n);remaining-=n;
        }
        if(remaining>0 && npc.getMainHandStack().isOf(item))npc.getMainHandStack().decrement(remaining);
        npc.backpack.markDirty();
    }
    private boolean attached(BlockPos pos){
        for(Direction direction:Direction.values()){
            BlockPos neighbor=pos.offset(direction);
            if(allowed(neighbor) && !world().getBlockState(neighbor).getCollisionShape(world(),neighbor).isEmpty())return true;
        }
        return false;
    }
    private boolean free(BlockPos pos){
        return allowed(pos) && !protectedPosition(pos) && world().getBlockState(pos).isAir()
            && world().getBlockEntity(pos)==null && world().getFluidState(pos).isEmpty()
            && world().getEntitiesByClass(LivingEntity.class,new Box(pos),LivingEntity::isAlive).isEmpty();
    }
    private boolean delay(BlockPos pos){
        if(!pos.equals(placement)){placement=pos.toImmutable();placeTicks=0;}
        npc.markWorking();return ++placeTicks>=4;
    }
    private void placed(){placement=null;placeTicks=0;npc.swingHand(Hand.MAIN_HAND);npc.animateInteraction(CompanionEntity.PLACE,8);}
    /** Connections, furnace fire and door opening change naturally after
     * placement. Orientation, material and halves still have to match the plan. */
    private static boolean matchesPlacement(BlockState actual,BlockState intended){
        if(!actual.isOf(intended.getBlock()))return false;
        for(Property<?> property:intended.getProperties()){
            if(switch(property.getName()){
                case "lit","open","powered","shape","north","south","east","west","up","down","distance","persistent" -> true;
                default -> false;
            })continue;
            if(!actual.getEntries().get(property).equals(intended.getEntries().get(property)))return false;
        }
        return true;
    }
    public boolean place(BlockPos pos,BlockState state,Item material,int quantity){
        if(quantity<1 || material==null || state==null || state.isAir() || state.getBlock().asItem()!=material
                || !rules() || !allowed(pos))return false;
        if(OwnedBlocks.get(world()).owned(pos,npc.getUuid()) && world().getBlockState(pos).isOf(state.getBlock())){
            if(matchesPlacement(world().getBlockState(pos),state))return true;
            status="O bloco já colocado tem orientação ou metade diferente do projeto";return false;
        }
        if(protectedPosition(pos) || world().getBlockEntity(pos)!=null){status="Preservar construção existente";return false;}
        if(!world().getBlockState(pos).isAir()){
            if(!clearNatural(pos))return false;
        }
        if(count(material)<quantity){status="Aguardar material real para colocar o bloco";return false;}
        if(!attached(pos)){status="Aguardar apoio para colocar o bloco";return false;}
        if(!approach(pos))return false;
        if(!free(pos)){status="Aguardar passagem livre para colocar o bloco";return false;}
        if(!delay(pos))return false;
        // Recheck immediately before committing inventory and world state.
        if(!free(pos) || !attached(pos) || count(material)<quantity)return false;
        BlockState updated=state;
        for(Direction direction:Direction.values()){
            BlockPos neighbor=pos.offset(direction);
            if(allowed(neighbor))updated=updated.getStateForNeighborUpdate(direction,world().getBlockState(neighbor),world(),pos,neighbor);
        }
        if(!world().setBlockState(pos,updated,3))return false;
        consume(material,quantity);OwnedBlocks.get(world()).claim(pos,npc.getUuid());placed();return true;
    }
    /** Doors consume one real item for their two inseparable block states. */
    public boolean placeDoor(BlockPos bottom,BlockState lower,Item material){
        if(!(lower.getBlock() instanceof DoorBlock) || lower.getBlock().asItem()!=material
                || !rules() || !allowed(bottom) || !allowed(bottom.up()))return false;
        BlockState upper=lower.with(DoorBlock.HALF,DoubleBlockHalf.UPPER);
        lower=lower.with(DoorBlock.HALF,DoubleBlockHalf.LOWER);
        if(world().getBlockState(bottom).isOf(lower.getBlock()) && world().getBlockState(bottom.up()).isOf(lower.getBlock())
                && OwnedBlocks.get(world()).owned(bottom,npc.getUuid()) && OwnedBlocks.get(world()).owned(bottom.up(),npc.getUuid())){
            if(matchesPlacement(world().getBlockState(bottom),lower) && matchesPlacement(world().getBlockState(bottom.up()),upper))return true;
            status="A porta existente não corresponde à orientação ou às metades do projeto";return false;
        }
        if(protectedPosition(bottom) || protectedPosition(bottom.up())){status="Preservar espaço da porta protegido";return false;}
        if(!world().getBlockState(bottom).isAir() && !clearNatural(bottom))return false;
        if(!world().getBlockState(bottom.up()).isAir() && !clearNatural(bottom.up()))return false;
        if(count(material)<1 || !allowed(bottom.down())
                || !world().getBlockState(bottom.down()).isSideSolidFullSquare(world(),bottom.down(),Direction.UP))return false;
        if(!approach(bottom) || !inReach(bottom.up(),npc.getEyePos()))return false;
        if(!free(bottom) || !free(bottom.up()) || !delay(bottom))return false;
        if(!free(bottom) || !free(bottom.up()) || count(material)<1)return false;
        // Set both halves before neighbor notifications, then notify the real
        // world. A failed second write restores the empty first cell.
        if(!world().setBlockState(bottom,lower,2))return false;
        if(!world().setBlockState(bottom.up(),upper,2)){world().setBlockState(bottom,Blocks.AIR.getDefaultState(),2);return false;}
        consume(material,1);OwnedBlocks.get(world()).claim(bottom,npc.getUuid());OwnedBlocks.get(world()).claim(bottom.up(),npc.getUuid());
        world().updateNeighbors(bottom,lower.getBlock());world().updateNeighbors(bottom.up(),upper.getBlock());placed();return true;
    }
}
