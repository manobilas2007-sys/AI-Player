package dev.aiplayer.ai;

import dev.aiplayer.entity.CompanionEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import java.util.*;

/**
 * A bounded physical excavation. Stairs always advance one horizontal block for
 * each block descended, preserving a solid floor and a walkable return path.
 * Galleries leave the floor intact and open a two-wide, two-high passage.
 * No direct inventory insertion, teleporting, or synthetic drops.
 */
public final class SafeExcavation {
    public enum Shape { STAIR, GALLERY }
    public enum Result { BUSY, PROGRESSED, STONE_MINED, FINISHED, BLOCKED }
    private final CompanionEntity npc;
    private final PhysicalMining miner;
    private Shape shape;
    private BlockPos origin;
    private Direction direction, side;
    private int depth, slot, stepStarted, lastRoute, emptySteps, pausedAt=-1;
    private boolean foundStone;
    private int initialTargetDepth=1;
    private static final int MAX_STAIR=10, MAX_GALLERY=12;

    public SafeExcavation(CompanionEntity npc,PhysicalMining miner){this.npc=npc;this.miner=miner;}
    public boolean active(){return shape!=null;}
    public Shape shape(){return shape;}
    public void stop(){shape=null;origin=null;miner.reset();npc.getNavigation().stop();}
    public void pause(){if(pausedAt<0)pausedAt=npc.age;miner.reset();}
    public void resume(){if(pausedAt>=0){stepStarted+=npc.age-pausedAt;pausedAt=-1;}}
    private boolean loaded(BlockPos pos){return npc.getWorld().isChunkLoaded(pos)
        && pos.getY()>npc.getWorld().getBottomY()+3 && pos.getY()<npc.getWorld().getTopY()-3;}
    private boolean solid(BlockPos p){return loaded(p) && npc.getWorld().getBlockState(p).isSolidBlock(npc.getWorld(),p);}
    /** Natural hard rock may obstruct a stone gallery. Mine it physically with the
     * pickaxe, but only vanilla STONE advances the cobblestone objective. */
    public static boolean isExcavatableMaterial(BlockState s){return s.isAir() || s.isOf(Blocks.STONE)
        || s.isOf(Blocks.ANDESITE) || s.isOf(Blocks.DIORITE) || s.isOf(Blocks.GRANITE)
        || s.isOf(Blocks.TUFF) || s.isOf(Blocks.DEEPSLATE)
        || s.isOf(Blocks.DIRT) || s.isOf(Blocks.GRASS_BLOCK) || s.isOf(Blocks.COARSE_DIRT)
        || s.isOf(Blocks.PODZOL) || s.isOf(Blocks.ROOTED_DIRT)
        || s.isOf(Blocks.SNOW) || s.isOf(Blocks.SNOW_BLOCK);}
    private static boolean allowed(BlockState s){return isExcavatableMaterial(s);}
    private boolean clearable(BlockPos p){
        if(!(npc.getWorld() instanceof ServerWorld w) || !loaded(p))return false;
        BlockState s=w.getBlockState(p);
        if(!allowed(s) || !w.getFluidState(p).isEmpty() || !w.getFluidState(p.up()).isEmpty()
                || w.getBlockState(p.up()).getBlock() instanceof FallingBlock)return false;
        if(s.isAir())return true;
        if(w.getBlockEntity(p)!=null || OwnedBlocks.get(w).protectedBlock(p)
                || OwnedBlocks.get(w).claimed(p))return false;
        for(Direction d:Direction.values()){
            BlockPos neighbor=p.offset(d);
            if(!loaded(neighbor) || !w.getFluidState(neighbor).isEmpty())return false;
        }
        // Never remove the current footing, including the supporting block
        // immediately next to a partially overhanging player hitbox.
        return !new Box(p).intersects(npc.getBoundingBox().expand(0.02,0,0.02))
                && !p.equals(npc.getBlockPos().down())
                && w.getEntitiesByClass(LivingEntity.class,new Box(p), e->e!=npc && e.isAlive()).isEmpty();
    }
    private BlockPos floorAt(int step, int lane){
        BlockPos p=origin.offset(direction,step);
        if(shape==Shape.GALLERY && lane>0)p=p.offset(side);
        return shape==Shape.STAIR?p.down(step+1):p.down();
    }
    private BlockPos cell(int step,int s){
        BlockPos p=origin.offset(direction,step);
        if(shape==Shape.STAIR)return s==0?p.down(step-1):p.down(step);
        if(s>=2)p=p.offset(side);
        return p.up((s%2==0)?1:0);
    }
    private BlockPos foot(int step){return shape==Shape.STAIR?origin.offset(direction,step).down(step):origin.offset(direction,step);}
    private int slots(){return shape==Shape.STAIR?2:4;}
    private boolean safeStep(int step){
        if(!solid(floorAt(step,0)) || (shape==Shape.GALLERY && !solid(floorAt(step,1))))return false;
        BlockPos p=foot(step);
        if(!loaded(p) || !loaded(p.up()) || !WorkNavigation.safeWorkFooting(npc,p))return false;
        for(int s=0;s<slots();s++)if(!clearable(cell(step,s)))return false;
        return true;
    }
    private boolean begin(Shape mode,BlockPos base,Direction face,Direction secondary){
        if(active() || face.getAxis().isVertical() || !npc.isOnGround() || !loaded(base)
                || !solid(base.down()))return false;
        shape=mode;origin=base.toImmutable();direction=face;side=secondary;
        depth=1;slot=0;stepStarted=npc.age;lastRoute=0;emptySteps=0;pausedAt=-1;foundStone=false;initialTargetDepth=1;
        if(!safeStep(1)){shape=null;origin=null;return false;}
        return true;
    }
    private record GalleryApproach(Direction face, Direction lane, int distance) {}
    /** Preflight the actual corridor without mutating the running executor. */
    private GalleryApproach galleryApproach(BlockPos base, BlockPos stone) {
        int dx=stone.getX()-base.getX(),dz=stone.getZ()-base.getZ();
        int distance=Math.max(Math.abs(dx),Math.abs(dz));
        if(distance<1 || distance>4 || stone.getY()<base.getY()
                || stone.getY()>base.getY()+1 || !solid(base.down()))return null;
        Direction face=Math.abs(dx)>=Math.abs(dz)?(dx>0?Direction.EAST:Direction.WEST)
                :(dz>0?Direction.SOUTH:Direction.NORTH);
        int lateral=face.getAxis()==Direction.Axis.X?dz:dx;
        if(Math.abs(lateral)>1)return null;
        Direction[] lanes=lateral==0
            ? new Direction[]{face.rotateYCounterclockwise(),face.rotateYClockwise()}
            : new Direction[]{face.getAxis()==Direction.Axis.X
                ? (lateral>0?Direction.SOUTH:Direction.NORTH)
                : (lateral>0?Direction.EAST:Direction.WEST)};
        for(Direction lane:lanes){
            boolean safe=true;
            for(int step=1;step<=distance && safe;step++){
                BlockPos foot=base.offset(face,step);
                if(!solid(foot.down()) || !solid(foot.offset(lane).down())
                        || !WorkNavigation.safeWorkFooting(npc,foot)){safe=false;break;}
                for(BlockPos cell:List.of(foot,foot.up(),foot.offset(lane),foot.offset(lane).up()))
                    if(!clearable(cell)){safe=false;break;}
            }
            if(safe)return new GalleryApproach(face,lane,distance);
        }
        return null;
    }
    public boolean canOpenGalleryFrom(BlockPos base,BlockPos stone){return galleryApproach(base,stone)!=null;}
    /** Revalidate on arrival, since the world may have changed. */
    public boolean beginGallery(BlockPos stone){
        if(active() || !npc.isOnGround())return false;
        BlockPos base=npc.getBlockPos();
        GalleryApproach approach=galleryApproach(base,stone);
        if(approach==null || !begin(Shape.GALLERY,base,approach.face(),approach.lane()))return false;
        initialTargetDepth=approach.distance();
        return true;
    }
    /** Prefer a supported natural descent; never drill a one-block vertical shaft. */
    public boolean beginStair(){
        if(active() || !npc.isOnGround() || npc.isTouchingWater())return false;
        BlockPos base=npc.getBlockPos();
        Direction face=npc.getHorizontalFacing();
        for(int i=0;i<4;i++,face=face.rotateYClockwise()){
            if(begin(Shape.STAIR,base,face,null))return true;
        }
        return false;
    }
    public Result tick(){
        if(!active())return Result.FINISHED;
        resume();
        if(npc.age-stepStarted>180){stop();return Result.BLOCKED;}
        if(depth>(shape==Shape.STAIR?MAX_STAIR:MAX_GALLERY)) {stop();return Result.FINISHED;}
        if(!safeStep(depth)){stop();return Result.BLOCKED;}
        while(slot<slots()){
            BlockPos p=cell(depth,slot);
            if(npc.getWorld().getBlockState(p).isAir()){slot++;continue;}
            if(!miner.reachable(p)){
                if(npc.age-stepStarted>80){stop();return Result.BLOCKED;}
                return Result.BUSY;
            }
            boolean stone=npc.getWorld().getBlockState(p).isOf(Blocks.STONE);
            if(miner.tick(p)){
                slot++;stepStarted=npc.age;
                if(stone){emptySteps=0;if(shape==Shape.STAIR)foundStone=true;}
                return stone?Result.STONE_MINED:Result.PROGRESSED;
            }
            return Result.BUSY;
        }
        BlockPos destination=foot(depth);
        if(npc.getPos().squaredDistanceTo(Vec3d.ofBottomCenter(destination))<0.65
                && Math.abs(npc.getY()-destination.getY())<0.75){
            if(shape==Shape.STAIR && foundStone){
                // At the first natural stone layer, switch from descending steps
                // to a two-high/two-wide horizontal gallery. The stairs behind
                // remain intact and can be walked back up.
                origin=destination.toImmutable();shape=Shape.GALLERY;
                side=direction.rotateYClockwise();depth=1;slot=0;
                stepStarted=npc.age;lastRoute=0;emptySteps=0;foundStone=false;initialTargetDepth=1;
                if(!safeStep(1)){stop();return Result.FINISHED;}
                return Result.PROGRESSED;
            }
            // Allow short natural diorite/granite seams without changing tunnel
            // direction. Stop after four consecutive stone-free columns so we
            // do not drill endlessly through irrelevant material.
            if(shape==Shape.GALLERY && depth>=initialTargetDepth && ++emptySteps>=4){stop();return Result.FINISHED;}
            depth++;slot=0;stepStarted=npc.age;lastRoute=0;
            return Result.PROGRESSED;
        }
        if(npc.age>=lastRoute+18){
            lastRoute=npc.age;
            // The destination has a verified real floor. Path to the newly
            // excavated cell, and use the normal motion controller if vanilla
            // cannot negotiate the one-step downward stair.
            Path route=WorkNavigation.findPath(npc,destination,0,dev.aiplayer.ai.navigation.NpcPathfinder.Mode.CAVE);
            if(route!=null && route.reachesTarget())npc.getNavigation().startMovingAlong(route,1.05);
            else if(npc.getBlockPos().getManhattanDistance(destination)<=3)
                npc.getMoveControl().moveTo(destination.getX()+0.5,destination.getY(),destination.getZ()+0.5,0.9);
        }
        return Result.BUSY;
    }
}
