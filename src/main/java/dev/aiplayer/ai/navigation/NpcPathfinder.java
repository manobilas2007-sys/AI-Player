package dev.aiplayer.ai.navigation;

import dev.aiplayer.ai.SurfaceStoneGathering;
import dev.aiplayer.ai.WorkNavigation;
import dev.aiplayer.baritone.*;
import dev.aiplayer.entity.CompanionEntity;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.ai.pathing.PathNode;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import java.util.*;
import java.util.function.Predicate;

/** Loaded-world adapter for the embedded Baritone search engine. Search reads
 * terrain only: the NPC's existing executors own walking, mining and placement. */
public final class NpcPathfinder {
    public enum Mode { SAFE, WORK, CAVE, WATER }
    public record Stats(String status,int expandedNodes,int routeNodes,boolean cacheHit) {}
    private record Cached(ServerWorld world,BlockPos start,BlockPos target,int distance,Mode mode,
                          long at,List<Step> steps) {}
    private record Budget(ServerWorld world,long at,int used) {}
    private static final Map<CompanionEntity,Cached> CACHE=new WeakHashMap<>();
    private static final Map<CompanionEntity,Stats> STATS=new WeakHashMap<>();
    private static final Map<CompanionEntity,Budget> BUDGETS=new WeakHashMap<>();
    private static final int MAX_NODES=4096,MAX_DISTANCE=48,MAX_NPCS=128,PER_TICK_NODES=8192;
    private NpcPathfinder() {}

    public static Path findPath(CompanionEntity npc,BlockPos target,int distance,Mode mode,
                                Predicate<BlockPos> allowed){
        Objects.requireNonNull(mode,"mode");Objects.requireNonNull(allowed,"allowed");
        if(!(npc.getWorld() instanceof ServerWorld world) || target==null
                || !world.getServer().isOnThread())return null;
        // Match vanilla's feet sampling on farmland and other fractional floors.
        BlockPos start=BlockPos.ofFloored(npc.getX(),npc.getY()+0.5,npc.getZ());target=target.toImmutable();
        int tolerance=Math.max(0,Math.min(MAX_DISTANCE,distance));
        if(!world.isChunkLoaded(start) || !world.isChunkLoaded(target)
                || Math.max(Math.max(Math.abs(start.getX()-target.getX()),Math.abs(start.getY()-target.getY())),
                    Math.abs(start.getZ()-target.getZ()))>MAX_DISTANCE+tolerance){
            record(npc,new Stats("LIMIT",0,0,false));return null;
        }
        Terrain terrain=new Terrain(npc,world,mode,allowed);
        if(!terrain.permitted(start)){
            record(npc,new Stats("NO_PATH",0,0,false));return null;
        }
        long now=world.getTime();Cached cached;
        synchronized(CACHE){cached=CACHE.get(npc);}
        if(cached!=null && cached.world()==world && cached.start().equals(start)
                && cached.target().equals(target) && cached.distance()==tolerance && cached.mode()==mode
                && now>=cached.at() && now-cached.at()<=10 && terrain.validRoute(start,cached.steps())){
            record(npc,new Stats("COMPLETE",0,cached.steps().size()+1,true));
            return path(world,start,target,cached.steps());
        }
        // Do not spend a search on an exact goal that cannot hold the NPC.
        if(tolerance==0 && !terrain.standable(target)){
            record(npc,new Stats("NO_PATH",0,0,false));return null;
        }
        Position from=position(start),to=position(target);
        Goal goal=tolerance==0?new GoalBlock(to):new GoalNear(to,tolerance);
        int remaining=remaining(npc,world,now);
        if(remaining<=0){record(npc,new Stats("BUDGET",0,0,false));return null;}
        SearchResult result=new AStarPathFinder().search(from,goal,terrain::neighbors,
            new SearchLimits(Math.min(MAX_NODES,remaining),MAX_DISTANCE));
        charge(npc,world,now,result.expandedNodes());
        record(npc,new Stats(result.status().name(),result.expandedNodes(),result.steps().size()+1,false));
        if(!result.complete())return null;
        synchronized(CACHE){
            if(CACHE.size()>=MAX_NPCS)CACHE.remove(CACHE.keySet().iterator().next());
            CACHE.put(npc,new Cached(world,start,target,tolerance,mode,now,result.steps()));
        }
        return path(world,start,target,result.steps());
    }
    public static Stats stats(CompanionEntity npc){
        synchronized(STATS){return STATS.getOrDefault(npc,new Stats("IDLE",0,0,false));}
    }
    public static String diagnostics(CompanionEntity npc){
        Stats s=stats(npc);return "Baritone "+s.status()+" nodes="+s.routeNodes()+" expanded="+s.expandedNodes()
            +(s.cacheHit()?" cached":"");
    }
    public static void invalidate(CompanionEntity npc){synchronized(CACHE){CACHE.remove(npc);}}
    public static int budgetUsed(CompanionEntity npc){
        if(!(npc.getWorld() instanceof ServerWorld world))return 0;
        return PER_TICK_NODES-remaining(npc,world,world.getTime());
    }
    private static int remaining(CompanionEntity npc,ServerWorld world,long now){
        synchronized(BUDGETS){
            Budget b=BUDGETS.get(npc);
            return b!=null && b.world()==world && b.at()==now?Math.max(0,PER_TICK_NODES-b.used()):PER_TICK_NODES;
        }
    }
    private static void charge(CompanionEntity npc,ServerWorld world,long now,int count){
        synchronized(BUDGETS){
            Budget b=BUDGETS.get(npc);
            int prior=b!=null && b.world()==world && b.at()==now?b.used():0;
            if(BUDGETS.size()>=MAX_NPCS)BUDGETS.remove(BUDGETS.keySet().iterator().next());
            BUDGETS.put(npc,new Budget(world,now,Math.min(PER_TICK_NODES,prior+count)));
        }
    }
    private static void record(CompanionEntity npc,Stats stats){
        synchronized(STATS){
            if(STATS.size()>=MAX_NPCS)STATS.remove(STATS.keySet().iterator().next());
            STATS.put(npc,stats);
        }
    }
    private static Path path(ServerWorld world,BlockPos start,BlockPos target,List<Step> steps){
        List<PathNode> nodes=new ArrayList<>(steps.size()+1);
        nodes.add(node(world,start));
        for(Step step:steps)nodes.add(node(world,block(step.destination())));
        return new Path(nodes,target,true);
    }
    private static PathNode node(ServerWorld world,BlockPos p){
        PathNode node=new PathNode(p.getX(),p.getY(),p.getZ());
        node.type=world.getFluidState(p).isIn(FluidTags.WATER)
            || world.getFluidState(p.down()).isIn(FluidTags.WATER)?PathNodeType.WATER:PathNodeType.WALKABLE;
        return node;
    }
    private static Position position(BlockPos p){return new Position(p.getX(),p.getY(),p.getZ());}
    private static BlockPos block(Position p){return new BlockPos(p.x(),p.y(),p.z());}

    private static final class Terrain {
        private final CompanionEntity npc;
        private final ServerWorld world;
        private final Mode mode;
        private final Predicate<BlockPos> allowed;
        // These caches live only during one synchronous, read-only search.
        private final Map<BlockPos,Boolean> standing=new HashMap<>(),clear=new HashMap<>();
        Terrain(CompanionEntity npc,ServerWorld world,Mode mode,Predicate<BlockPos> allowed){
            this.npc=npc;this.world=world;this.mode=mode;this.allowed=allowed;
        }
        private boolean permitted(BlockPos p){
            return world.isChunkLoaded(p) && allowed.test(p)
                && (mode!=Mode.CAVE || SurfaceStoneGathering.caveAccess(npc,p));
        }
        private Box body(BlockPos p){
            double half=npc.getWidth()/2.0;
            return new Box(p.getX()+0.5-half,p.getY()+0.001,p.getZ()+0.5-half,
                p.getX()+0.5+half,p.getY()+npc.getHeight()-0.001,p.getZ()+0.5+half);
        }
        private boolean loaded(Box box){
            if(box.minY<world.getBottomY() || box.maxY>=world.getTopY())return false;
            int minX=((int)Math.floor(box.minX))>>4,maxX=((int)Math.floor(box.maxX))>>4;
            int minZ=((int)Math.floor(box.minZ))>>4,maxZ=((int)Math.floor(box.maxZ))>>4;
            for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)
                if(!world.isChunkLoaded(new BlockPos(x<<4,(int)Math.floor(box.minY),z<<4)))return false;
            return true;
        }
        private boolean hazard(BlockPos p){
            var state=world.getBlockState(p);
            return state.isOf(Blocks.LAVA) || state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE)
                || state.isOf(Blocks.MAGMA_BLOCK) || state.isOf(Blocks.CACTUS)
                || state.isOf(Blocks.CAMPFIRE) || state.isOf(Blocks.SOUL_CAMPFIRE)
                || state.isOf(Blocks.POWDER_SNOW) || state.isOf(Blocks.WITHER_ROSE)
                || state.isOf(Blocks.SWEET_BERRY_BUSH);
        }
        private boolean water(BlockPos p){return world.getFluidState(p).isIn(FluidTags.WATER);}
        private boolean bodyClear(BlockPos p){
            return clear.computeIfAbsent(p.toImmutable(),key->{
                if(!permitted(key) || !world.isChunkLoaded(key.up()) || !loaded(body(key)))return false;
                if(hazard(key) || hazard(key.up()))return false;
                if(mode==Mode.WATER){
                    if((!world.getFluidState(key).isEmpty() && !water(key))
                            || (!world.getFluidState(key.up()).isEmpty() && !water(key.up())))return false;
                    if((water(key) || water(key.up())) && npc.getAir()<150)return false;
                } else if(!world.getFluidState(key).isEmpty() || !world.getFluidState(key.up()).isEmpty())return false;
                return !world.getBlockCollisions(npc,body(key)).iterator().hasNext();
            });
        }
        boolean standable(BlockPos p){
            return standing.computeIfAbsent(p.toImmutable(),key->{
                if(!bodyClear(key) || !world.isChunkLoaded(key.down()) || hazard(key.down()))return false;
                if(mode==Mode.WATER && npc.getAir()>=150 && (water(key) || water(key.down())))return true;
                if(!WorkNavigation.supportedGround(npc,key.down()))return false;
                return (mode!=Mode.WORK && mode!=Mode.CAVE) || WorkNavigation.safeWorkFooting(npc,key);
            });
        }
        private boolean transition(BlockPos from,BlockPos to){
            if(!standable(to))return false;
            int dx=to.getX()-from.getX(),dy=to.getY()-from.getY(),dz=to.getZ()-from.getZ();
            if(Math.abs(dx)>1 || Math.abs(dz)>1 || dy>1 || dy<-(mode==Mode.SAFE?3:1))return false;
            if(dx==0 && dz==0)return mode==Mode.WATER && Math.abs(dy)==1 && bodyClear(from);
            if(dx!=0 && dz!=0){
                if(dy!=0 || !standable(from.add(dx,0,0)) || !standable(from.add(0,0,dz)))return false;
            }
            if(dy>0){
                if(!bodyClear(from.up()))return false;
            } else if(dy<0){
                // Clear the lateral column before stepping down, never tunnel through it.
                for(int level=0;level>=dy;level--)if(!bodyClear(new BlockPos(to.getX(),from.getY()+level,to.getZ())))return false;
            }
            Box crossing=body(dy>0?from.up():from).stretch(dx,0,dz);
            return loaded(crossing) && !world.getBlockCollisions(npc,crossing).iterator().hasNext();
        }
        Iterable<Step> neighbors(Position position){
            BlockPos from=block(position);List<Step> choices=new ArrayList<>(14);
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
                if(dx==0 && dz==0)continue;
                BlockPos level=from.add(dx,0,dz);
                if(transition(from,level)){
                    boolean swim=mode==Mode.WATER && (water(from) || water(level) || water(level.down()));
                    double cost=(swim?ActionCosts.WALK_ONE_IN_WATER_COST:ActionCosts.WALK_ONE_BLOCK_COST)
                        *(dx!=0 && dz!=0?Math.sqrt(2):1);
                    choices.add(new Step(position(level),cost,swim?Movement.SWIM:dx!=0 && dz!=0?Movement.DIAGONAL:Movement.TRAVERSE));
                }
                if(dx!=0 && dz!=0)continue;
                BlockPos up=level.up();
                if(transition(from,up))choices.add(new Step(position(up),
                    ActionCosts.WALK_ONE_BLOCK_COST+ActionCosts.JUMP_ONE_BLOCK_COST,Movement.ASCEND));
                int maxDrop=mode==Mode.SAFE?3:1;
                for(int drop=1;drop<=maxDrop;drop++){
                    BlockPos down=level.down(drop);
                    if(transition(from,down)){
                        choices.add(new Step(position(down),ActionCosts.WALK_ONE_BLOCK_COST
                            +ActionCosts.FALL_N_BLOCKS_COST[drop],Movement.DESCEND));break;
                    }
                }
            }
            if(mode==Mode.WATER && water(from))for(int dy:new int[]{-1,1}){
                BlockPos to=from.up(dy);
                if(transition(from,to))choices.add(new Step(position(to),ActionCosts.WALK_ONE_IN_WATER_COST,Movement.SWIM));
            }
            return choices;
        }
        boolean validRoute(BlockPos start,List<Step> steps){
            if(steps.isEmpty())return standable(start);
            BlockPos from=start;
            for(Step step:steps){BlockPos to=block(step.destination());if(!transition(from,to))return false;from=to;}
            return true;
        }
    }
}
