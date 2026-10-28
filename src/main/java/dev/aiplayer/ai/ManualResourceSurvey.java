package dev.aiplayer.ai;

import dev.aiplayer.ai.tasks.ProjectWork;
import dev.aiplayer.entity.CompanionEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/** Physical outward search for an explicit gathering order. Every route is a
 * local segment; the companion's moving chunk tickets supply its next terrain. */
public final class ManualResourceSurvey {
    private static final java.util.List<BlockPos> CAVE_COLUMNS=java.util.stream.IntStream.rangeClosed(-16,16).boxed()
        .flatMap(x->java.util.stream.IntStream.rangeClosed(-16,16).mapToObj(z->new BlockPos(x,0,z)))
        .sorted(java.util.Comparator.comparingDouble(p->p.getSquaredDistance(BlockPos.ORIGIN))).toList();
    private final CompanionEntity npc;
    private final ProjectWork work;
    private BlockPos waypoint, scanCenter;
    private Vec3d last;
    private int direction, leg=1, step, turns, since, nextRoute, scan;
    private boolean active, caveTarget, paused;
    public ManualResourceSurvey(CompanionEntity npc){this.npc=npc;work=new ProjectWork(npc);}
    public boolean active(){return active;}
    public void pause(){if(active && !paused)work.pause();paused=true;last=null;}
    public void stop(){pause();active=false;paused=false;waypoint=null;scanCenter=null;scan=0;}
    public void reset(){stop();direction=0;leg=1;step=0;turns=0;nextRoute=0;}
    private void advance(){
        waypoint=null;caveTarget=false;work.pause();
        if(++step>=leg){step=0;direction=(direction+1)%4;if(++turns%2==0)leg++;}
    }
    private boolean standable(ServerWorld world,BlockPos feet){
        return world.isChunkLoaded(feet) && world.isChunkLoaded(feet.up())
            && world.getWorldBorder().contains(feet) && WorkNavigation.safeWorkFooting(npc,feet)
            && world.getFluidState(feet).isEmpty() && world.getFluidState(feet.up()).isEmpty()
            && world.isSpaceEmpty(npc,new Box(feet).stretch(0,0.8,0));
    }
    private boolean visible(ServerWorld world,BlockPos feet){
        Vec3d end=Vec3d.ofBottomCenter(feet).add(0,1,0),start=npc.getEyePos();
        int steps=Math.max(1,(int)Math.ceil(start.distanceTo(end)/4));
        for(int i=0;i<=steps;i++)if(!world.isChunkLoaded(BlockPos.ofFloored(start.lerp(end,(double)i/steps))))return false;
        return world.raycast(new RaycastContext(start,end,RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,npc)).getType()==HitResult.Type.MISS;
    }
    public BlockPos observedCave(ServerWorld world){
        if(scanCenter==null || scanCenter.getSquaredDistance(npc.getBlockPos())>8*8){scanCenter=npc.getBlockPos();scan=0;}
        // Nearby visible standing cells, with a fixed per-tick observation budget.
        for(int budget=0;budget<96 && scan<CAVE_COLUMNS.size()*13;budget++,scan++){
            int column=scan/13,dy=scan%13==0?0:(scan%13%2==1?-(scan%13+1)/2:scan%13/2);
            BlockPos feet=scanCenter.add(CAVE_COLUMNS.get(column)).up(dy);
            if(feet.getSquaredDistance(npc.getBlockPos())<4 || !standable(world,feet)
                    || npc.intelligence().memory().blocked(feet,world.getTime())
                    || !SurfaceStoneGathering.caveAccess(npc,feet) || !visible(world,feet))continue;
            scan++;return feet.toImmutable();
        }
        if(scan>=CAVE_COLUMNS.size()*13){scan=0;scanCenter=npc.getBlockPos();}
        return null;
    }
    public boolean tick(boolean seekCave){
        if(!(npc.getWorld() instanceof ServerWorld world))return false;
        active=true;paused=false;
        if(seekCave && !caveTarget){
            BlockPos entrance=observedCave(world);
            if(entrance!=null){work.pause();waypoint=entrance;caveTarget=true;last=null;}
        }
        if(waypoint!=null){
            if(last==null || npc.getPos().squaredDistanceTo(last)>0.25){last=npc.getPos();since=npc.age;}
            if(work.moveTo(waypoint))advance();
            else if(npc.age-since>180){
                npc.intelligence().memory().failed(waypoint,world.getTime());advance();
            }else return true;
        }
        if(npc.age<nextRoute)return true;
        nextRoute=npc.age+10;
        int[][] headings={{1,0},{0,1},{-1,0},{0,-1}};int[] h=headings[direction];
        for(int distance:new int[]{24,16,8})for(int side:new int[]{0,3,-3,6,-6}){
            BlockPos column=npc.getBlockPos().add(h[0]*distance-h[1]*side,0,h[1]*distance+h[0]*side);
            for(int dy:new int[]{0,1,-1,2,-2,3,-3,4,-4,5,-5,6,-6}){
                BlockPos feet=column.up(dy);
                if(!standable(world,feet) || npc.intelligence().memory().blocked(feet,world.getTime()))continue;
                waypoint=feet.toImmutable();since=npc.age;last=npc.getPos();work.moveTo(waypoint);return true;
            }
        }
        advance();return true;
    }
    public void writeNbt(NbtCompound n){
        n.putInt("SurveyDirection",direction);n.putInt("SurveyLeg",leg);n.putInt("SurveyStep",step);n.putInt("SurveyTurns",turns);
    }
    public void readNbt(NbtCompound n){
        reset();direction=Math.floorMod(n.getInt("SurveyDirection"),4);leg=Math.max(1,Math.min(100000,n.getInt("SurveyLeg")));
        step=Math.max(0,Math.min(leg-1,n.getInt("SurveyStep")));turns=Math.max(0,n.getInt("SurveyTurns"));
    }
}
