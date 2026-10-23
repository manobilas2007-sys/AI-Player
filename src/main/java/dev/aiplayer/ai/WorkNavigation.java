package dev.aiplayer.ai;

import dev.aiplayer.entity.CompanionEntity;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.HitResult;
import net.minecraft.registry.tag.BlockTags;

/** Work destinations are standable positions beside the target, never the solid target itself. */
public final class WorkNavigation {
    private WorkNavigation() {}
    public static boolean safePath(CompanionEntity npc, Path path, int floorY) {
        if(path==null || !path.reachesTarget())return false;
        for(int i=0;i<path.getLength();i++) {
            var n=path.getNode(i);
            BlockPos pos=new BlockPos(n.x,n.y,n.z);
            if(n.y<floorY || !npc.getWorld().isChunkLoaded(pos))return false;
            if(i>0 && path.getNode(i-1).y-n.y>3)return false;
            if(hazard(npc,pos) || hazard(npc,pos.down()))return false;
        }
        return true;
    }
    /**
     * A work position must have real support and must not border two deep drops.
     * The exception for one exposed edge permits natural hillsides, while ruling
     * out the thin, unsupported test-arena ledges that used to dump the worker.
     */
    public static boolean safeWorkFooting(CompanionEntity npc,BlockPos feet){
        var world=npc.getWorld();
        if(!world.isChunkLoaded(feet) || !world.isChunkLoaded(feet.down())
                || !world.getBlockState(feet.down()).isSolidBlock(world,feet.down())
                || !world.getFluidState(feet).isEmpty() || !world.getFluidState(feet.up()).isEmpty())return false;
        int deepDrops=0;
        for(net.minecraft.util.math.Direction direction:net.minecraft.util.math.Direction.Type.HORIZONTAL){
            BlockPos side=feet.offset(direction);
            if(!world.isChunkLoaded(side) || !world.isChunkLoaded(side.down()))return false;
            if(!world.getBlockState(side.down()).isSolidBlock(world,side.down())){
                boolean nearGround=false;
                for(int depth=2;depth<=3;depth++){
                    BlockPos below=side.down(depth);
                    if(!world.isChunkLoaded(below))return false;
                    if(world.getBlockState(below).isSolidBlock(world,below)){nearGround=true;break;}
                }
                if(!nearGround && ++deepDrops>=2)return false;
            }
        }
        return true;
    }
    /** Follow terrain contours; reject thin ledges, void steps and cave detours. */
    public static boolean surfacePath(CompanionEntity npc,Path path,int destinationY){
        int reference=Math.min(npc.getBlockY(),destinationY);
        if(!safePath(npc,path,reference-3))return false;
        for(int i=0;i<path.getLength();i++){
            var node=path.getNode(i);BlockPos p=new BlockPos(node.x,node.y,node.z);
            if(!safeWorkFooting(npc,p) || hazard(npc,p.up()))return false;
            if(node.y<reference-1 && !npc.getWorld().isSkyVisible(p.up()))return false;
            // A work route must not intentionally walk off a two-block ledge.
            if(i>0 && path.getNode(i-1).y-node.y>1)return false;
        }
        return true;
    }
    private static boolean hazard(CompanionEntity npc,BlockPos pos){
        var s=npc.getWorld().getBlockState(pos);
        return s.isOf(net.minecraft.block.Blocks.LAVA) || s.isOf(net.minecraft.block.Blocks.FIRE)
            || s.isOf(net.minecraft.block.Blocks.SOUL_FIRE) || s.isOf(net.minecraft.block.Blocks.MAGMA_BLOCK)
            || s.isOf(net.minecraft.block.Blocks.CACTUS) || s.isOf(net.minecraft.block.Blocks.CAMPFIRE)
            || s.isOf(net.minecraft.block.Blocks.SOUL_CAMPFIRE) || s.isOf(net.minecraft.block.Blocks.POWDER_SNOW);
    }
    /** Leave an isolated perch using a real one-block descent and complete path. */
    public static boolean leaveIsolatedPerch(CompanionEntity npc){
        if(!npc.isOnGround())return false;
        BlockPos feet=npc.getBlockPos();
        var world=npc.getWorld();
        for(var dir:net.minecraft.util.math.Direction.Type.HORIZONTAL)
            if(world.getBlockState(feet.offset(dir).down()).isSolidBlock(world,feet.offset(dir).down()))return false;
        for(var dir:net.minecraft.util.math.Direction.Type.HORIZONTAL){
            BlockPos landing=feet.offset(dir).down();
            if(!safeWorkFooting(npc,landing) || hazard(npc,landing.down())
                    || !world.isSpaceEmpty(npc,npc.getBoundingBox().offset(
                        landing.getX()+0.5-npc.getX(),landing.getY()-npc.getY(),landing.getZ()+0.5-npc.getZ())))continue;
            Path path=npc.getNavigation().findPathTo(landing,0);
            if(surfacePath(npc,path,landing.getY()) && npc.getNavigation().startMovingAlong(path,1.0))return true;
        }
        return false;
    }
    public static boolean moveNear(CompanionEntity npc, BlockPos target, int groundY, double speed) {
        Path best=null;double score=Double.MAX_VALUE;
        // Search a small ring at the work surface. Reject partial routes and cave detours.
        for(int radius=1;radius<=2;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++) {
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            for(int dy=1;dy>=-1;dy--) {
                BlockPos p=new BlockPos(target.getX()+dx,groundY+dy,target.getZ()+dz);
                if(!npc.getWorld().isChunkLoaded(p) || !npc.getWorld().getFluidState(p).isEmpty()
                    || !npc.getWorld().getBlockState(p).getCollisionShape(npc.getWorld(),p).isEmpty()
                    || !npc.getWorld().getBlockState(p.up()).getCollisionShape(npc.getWorld(),p.up()).isEmpty()
                    || !npc.getWorld().getBlockState(p.down()).isSolidBlock(npc.getWorld(),p.down()))continue;
                Path path=npc.getNavigation().findPathTo(p,0);
                if(!surfacePath(npc,path,groundY))continue;
                double value=path.getLength()+p.getSquaredDistance(target)*0.1;
                if(value<score){score=value;best=path;}
            }
            if(best!=null)break;
        }
        if(best==null){npc.getNavigation().stop();return false;}
        return npc.getNavigation().startMovingAlong(best,speed);
    }
    public static boolean moveToDrop(CompanionEntity npc, net.minecraft.entity.ItemEntity drop, double speed) {
        // Search reachable contact positions when the item's exact cell is
        // blocked. Pickup still requires physical proximity and clear sight.
        BlockPos center=drop.getBlockPos();
        Path best=null;double bestScore=Double.MAX_VALUE;
        boolean waterRoute=npc.isTouchingWater() || !npc.getWorld().getFluidState(center).isEmpty();
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)for(int dy=-1;dy<=1;dy++){
            BlockPos feet=center.add(dx,dy,dz);
            Vec3d at=Vec3d.ofBottomCenter(feet);
            if(at.squaredDistanceTo(drop.getPos())>2.5)continue;
            var hit=npc.getWorld().raycast(new RaycastContext(at.add(0,npc.getStandingEyeHeight(),0),
                drop.getPos().add(0,0.1,0),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,npc));
            if(hit.getType()!=HitResult.Type.MISS)continue;
            Path path=npc.getNavigation().findPathTo(feet,0);
            if(!(waterRoute ? safePath(npc,path,Math.min(npc.getBlockY(),drop.getBlockY())-3)
                    : surfacePath(npc,path,feet.getY())))continue;
            var end=path.getEnd();
            if(end==null || end.x!=feet.getX() || end.y!=feet.getY() || end.z!=feet.getZ())continue;
            double score=path.getLength()+at.squaredDistanceTo(drop.getPos());
            if(score<bestScore){best=path;bestScore=score;}
        }
        return best!=null && npc.getNavigation().startMovingAlong(best,speed);
    }
    /** Short-range swimming rescue when a drop floats next to the shore.
     *  No teleport, fake pickup or path through solid terrain. */
    public static boolean swimToDrop(CompanionEntity npc,net.minecraft.entity.ItemEntity drop){
        var world=npc.getWorld();
        BlockPos target=drop.getBlockPos();
        // Minecraft's floating ItemEntity moves from the water cell to the
        // air cell immediately above it. A water-only test on its current
        // block makes the same nearby drop randomly unreachable after a few
        // ticks, depending on item buoyancy.
        boolean atWater=world.getFluidState(target).isIn(net.minecraft.registry.tag.FluidTags.WATER)
            || world.getFluidState(target.down()).isIn(net.minecraft.registry.tag.FluidTags.WATER);
        if(!world.isChunkLoaded(target) || !world.isChunkLoaded(target.down())
                || npc.squaredDistanceTo(drop)>7*7 || npc.getAir()<150 || !atWater
                || (!npc.isTouchingWater() && (npc.squaredDistanceTo(drop)>4*4
                    || Math.abs(npc.getY()-drop.getY())>1.8)))return false;
        // Raycast from the swimmer's torso as well as the eyes. At the
        // waterline an eye ray may clip the shore block even when the body
        // has a clear, physically swimmable approach to the floating item.
        Vec3d destination=drop.getPos().add(0,0.1,0);
        var eyes=world.raycast(new RaycastContext(npc.getEyePos(),destination,
            RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,npc));
        var torso=world.raycast(new RaycastContext(npc.getPos().add(0,Math.min(0.85,npc.getHeight()*0.55),0),destination,
            RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,npc));
        if(eyes.getType()!=HitResult.Type.MISS && torso.getType()!=HitResult.Type.MISS)return false;
        npc.getNavigation().stop();
        npc.getMoveControl().moveTo(drop.getX(),drop.getY(),drop.getZ(),1.15);
        npc.getJumpControl().setActive();
        return true;
    }
    public static BlockPos leafBetween(CompanionEntity npc, Vec3d end) {
        var hit=npc.getWorld().raycast(new RaycastContext(npc.getEyePos(),end,
            RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,npc));
        if(hit.getType()!=HitResult.Type.BLOCK)return null;
        BlockPos pos=hit.getBlockPos();
        return npc.getWorld().getBlockState(pos).isIn(BlockTags.LEAVES)?pos.toImmutable():null;
    }
}
