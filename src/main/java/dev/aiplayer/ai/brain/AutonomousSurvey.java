package dev.aiplayer.ai.brain;

import dev.aiplayer.ai.Mode;
import dev.aiplayer.ai.OwnedBlocks;
import dev.aiplayer.ai.WorkNavigation;
import dev.aiplayer.ai.navigation.NpcPathfinder;
import dev.aiplayer.entity.CompanionEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Small, passive observations while accompanying the owner. This reads only
 * loaded, visible terrain; observing a resource never mines or grants it. */
public final class AutonomousSurvey {
    private static final int RADIUS=16,CELL_BUDGET=96,SIGHT_BUDGET=2;
    private static final List<BlockPos> OFFSETS=offsets();
    private static final int[][] LOOKOUTS={{8,0},{6,6},{0,8},{-6,6},{-8,0},{-6,-6},{0,-8},{6,-6}};
    private final CompanionEntity npc;
    private BlockPos center,destination;
    private Path ownedPath;
    private String dimension="";
    private int cursor,nextObserve,nextWalk,lookout,lastCells,lastSight;

    public AutonomousSurvey(CompanionEntity npc){this.npc=npc;}
    public int lastObservedCells(){return lastCells;}
    public int lastSightChecks(){return lastSight;}
    private static List<BlockPos> offsets(){
        List<BlockPos> cells=new ArrayList<>();
        for(int x=-RADIUS;x<=RADIUS;x++)for(int z=-RADIUS;z<=RADIUS;z++)for(int y=-6;y<=6;y++)
            if(x*x+y*y+z*z<=RADIUS*RADIUS)cells.add(new BlockPos(x,y,z));
        cells.sort(Comparator.comparingDouble(p->p.getSquaredDistance(BlockPos.ORIGIN)));
        return List.copyOf(cells);
    }
    public void observe(){
        lastCells=0;lastSight=0;
        if(!(npc.getWorld() instanceof ServerWorld world) || !world.getServer().isOnThread()
                || !npc.isAlive() || npc.ownerId()==null || npc.menuOpen() || npc.isEating()
                || npc.getTarget()!=null || npc.gathering().active() || npc.tasks().active()
                || npc.age<nextObserve)return;
        nextObserve=npc.age+5;
        String current=world.getRegistryKey().getValue().toString();
        if(!current.equals(dimension) || center==null || center.getSquaredDistance(npc.getBlockPos())>8*8){
            center=npc.getBlockPos().toImmutable();cursor=0;dimension=current;
        }
        var ownership=OwnedBlocks.get(world);
        while(lastCells<CELL_BUDGET && cursor<OFFSETS.size()){
            BlockPos pos=center.add(OFFSETS.get(cursor++));lastCells++;
            if(pos.getY()<=world.getBottomY() || pos.getY()>=world.getTopY()-1 || !world.isChunkLoaded(pos))continue;
            BlockState state=world.getBlockState(pos);
            String kind=kind(world,pos,state);
            if(kind==null || (ownership.claimed(pos) && !ownership.owned(pos,npc.getUuid())))continue;
            if(!kind.equals("crop") && !kind.equals("water") && ownership.protectedBlock(pos))continue;
            // Keep observations useful instead of filling all memory slots
            // with adjacent pieces of the same stone floor or crop row.
            if(npc.intelligence().memory().nearest(kind,pos,world.getTime(),8).isPresent())continue;
            if(lastSight>=SIGHT_BUDGET){cursor--;break;}
            lastSight++;
            if(!visible(world,pos,kind.equals("water")))continue;
            npc.intelligence().memory().remember(kind,pos,world.getTime(),2400);
        }
        if(cursor>=OFFSETS.size()){cursor=0;center=npc.getBlockPos().toImmutable();nextObserve=npc.age+40;}
    }
    private String kind(ServerWorld world,BlockPos pos,BlockState state){
        if(state.isOf(Blocks.IRON_ORE) || state.isOf(Blocks.DEEPSLATE_IRON_ORE))return "iron";
        if(state.isOf(Blocks.COAL_ORE) || state.isOf(Blocks.DEEPSLATE_COAL_ORE))return "coal";
        if(state.isOf(Blocks.SAND) || state.isOf(Blocks.RED_SAND))return "sand";
        if(state.isOf(Blocks.WATER) && world.getFluidState(pos).isStill())return "water";
        if(state.getBlock() instanceof CropBlock)return "crop";
        if(state.isIn(BlockTags.LOGS) && world.isChunkLoaded(pos.down())
                && world.getBlockState(pos.down()).isIn(BlockTags.DIRT))return "tree";
        if(state.isOf(Blocks.STONE) || state.isOf(Blocks.DEEPSLATE))return "stone";
        return null;
    }
    private boolean visible(ServerWorld world,BlockPos pos,boolean water){
        Vec3d eyes=npc.getEyePos(),target=Vec3d.ofCenter(pos);
        if(eyes.squaredDistanceTo(target)>RADIUS*RADIUS)return false;
        // A ray must not cause a read through an unloaded intermediate chunk.
        int steps=Math.max(1,(int)Math.ceil(eyes.distanceTo(target)/4));
        for(int i=0;i<=steps;i++)if(!world.isChunkLoaded(BlockPos.ofFloored(eyes.lerp(target,(double)i/steps))))return false;
        var hit=world.raycast(new RaycastContext(eyes,target,RaycastContext.ShapeType.COLLIDER,
            water?RaycastContext.FluidHandling.SOURCE_ONLY:RaycastContext.FluidHandling.NONE,npc));
        return hit.getType()==HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }
    /** Owns only a short idle route. The coordinator still controls following,
     * combat and work, and a moving owner immediately invalidates this stroll. */
    public boolean idleStep(){
        if(!(npc.getWorld() instanceof ServerWorld world) || !world.getServer().isOnThread())return false;
        var owner=npc.owner();
        if(owner==null || owner.isSpectator() || npc.mode()==Mode.WAIT || npc.menuOpen() || npc.isEating()
                || npc.getTarget()!=null || npc.gathering().active() || npc.gathering().hasCleanup()
                || npc.tasks().active() || npc.crafting().hasSession() || npc.getHealth()<20
                || !npc.autoSurvive || npc.squaredDistanceTo(owner)>6*6){abortOwnRoute();return false;}
        var nav=npc.getNavigation();
        if(destination!=null){
            if(Vec3d.ofBottomCenter(destination).squaredDistanceTo(owner.getPos())>12*12){abortOwnRoute();return false;}
            if(npc.squaredDistanceTo(Vec3d.ofBottomCenter(destination))<1.5){
                npc.intelligence().memory().remember("survey",destination,world.getTime(),1200);
                abortOwnRoute();nextWalk=npc.age+160;return true;
            }
            if(!nav.isIdle() && nav.getCurrentPath()==ownedPath)return true;
            abortOwnRoute();nextWalk=npc.age+80;
        }
        if(!nav.isIdle())return true;
        // The idle survey replaces random wandering while on cooldown too.
        if(npc.age<nextWalk)return true;
        nextWalk=npc.age+160;
        int searched=0;
        for(int n=0;n<LOOKOUTS.length && searched<4;n++){
            int[] delta=LOOKOUTS[lookout++%LOOKOUTS.length];
            BlockPos horizontal=owner.getBlockPos().add(delta[0],0,delta[1]);
            for(int dy:new int[]{0,1,-1,2,-2}){
                BlockPos feet=new BlockPos(horizontal.getX(),(int)Math.floor(npc.getY()+0.5)+dy,horizontal.getZ());
                if(!world.isChunkLoaded(feet) || !world.isChunkLoaded(feet.up()) || !world.isChunkLoaded(feet.down())
                        || !WorkNavigation.safeWorkFooting(npc,feet)
                        || !world.getBlockState(feet).getCollisionShape(world,feet).isEmpty()
                        || !world.getBlockState(feet.up()).getCollisionShape(world,feet.up()).isEmpty())continue;
                if(npc.intelligence().memory().nearest("survey",feet,world.getTime(),2).isPresent())continue;
                searched++;
                Vec3d ownerAt=owner.getPos();
                Path route=WorkNavigation.findPath(npc,feet,0,NpcPathfinder.Mode.SAFE,
                    p->Vec3d.ofBottomCenter(p).squaredDistanceTo(ownerAt)<=12*12);
                if(!WorkNavigation.surfacePath(npc,route,feet.getY()))break;
                nav.stop();
                if(nav.startMovingAlong(route,0.9)){
                    destination=feet.toImmutable();ownedPath=route;return true;
                }
                break;
            }
        }
        return true;
    }
    private void abortOwnRoute(){
        if(ownedPath!=null && npc.getNavigation().getCurrentPath()==ownedPath)npc.getNavigation().stop();
        destination=null;ownedPath=null;
    }
}
