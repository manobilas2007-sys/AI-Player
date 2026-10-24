package dev.aiplayer.ai;

import dev.aiplayer.entity.CompanionEntity;
import dev.aiplayer.ai.brain.ResourcePlan;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.enchantment.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import java.util.*;

/** Cave-only work. The coordinator owns interruption; this executor owns one committed block. */
public final class SurfaceStoneGathering {
    public enum Result { ACTIVE, PROGRESSED, MINED, EXHAUSTED, NEED_CAVE, NEED_TOOL, RULE_BLOCKED }
    private static final List<BlockPos> COLUMNS=java.util.stream.IntStream.rangeClosed(-32,32).boxed()
        .flatMap(x->java.util.stream.IntStream.rangeClosed(-32,32).mapToObj(z->new BlockPos(x,0,z)))
        .filter(p->p.getX()*p.getX()+p.getZ()*p.getZ()<=1024)
        .sorted(Comparator.comparingDouble(p->p.getSquaredDistance(BlockPos.ORIGIN))).toList();
    private final CompanionEntity npc;
    private final PhysicalMining mining;
    private final SafeExcavation excavation;
    private int lastDigAttempt,scanStarted;
    private boolean floorSeen;
    private BlockPos excavationSource;
    private BlockPos center,target,stance;
    private int column,height=-8,passes,waitUntil,nextRoute,routeStarted,paused=-1;
    private Vec3d routePosition;
    private final Map<BlockPos,Long> avoided=new HashMap<>();
    public SurfaceStoneGathering(CompanionEntity npc){this.npc=npc;mining=new PhysicalMining(npc,SurfaceStoneGathering::suitablePick);excavation=new SafeExcavation(npc,mining);}
    public static boolean suitablePick(ItemStack s){
        return s.getItem() instanceof PickaxeItem && !ResourcePlan.worn(s)
            && s.isSuitableFor(Blocks.STONE.getDefaultState()) && EnchantmentHelper.getLevel(Enchantments.SILK_TOUCH,s)==0;
    }
    public static boolean hasPick(CompanionEntity npc){
        if(suitablePick(npc.getMainHandStack()))return true;
        if(npc.autoEquip)for(int i=0;i<npc.backpack.size();i++)if(suitablePick(npc.backpack.getStack(i)))return true;
        return false;
    }
    public boolean committed(){return mining.hasProgress();}
    public boolean inExcavation(){return excavation.active();}
    public void pause(){if(paused<0)paused=npc.age;excavation.pause();mining.reset();}
    public void resume(){if(paused>=0){int delay=npc.age-paused;waitUntil+=delay;routeStarted+=delay;paused=-1;}excavation.resume();}
    public void reset(){excavation.stop();center=null;target=null;stance=null;excavationSource=null;column=0;height=-8;passes=0;waitUntil=0;nextRoute=0;paused=-1;floorSeen=false;scanStarted=0;avoided.clear();}
    public void failed(){
        if(target!=null){avoided.put(target,npc.getWorld().getTime()+100);npc.intelligence().memory().failed(target,npc.getWorld().getTime());}
        target=null;stance=null;excavation.stop();
    }
    public boolean safeBlock(BlockPos p){
        if(!(npc.getWorld() instanceof ServerWorld world) || !world.isChunkLoaded(p)
            || !world.getBlockState(p).isOf(Blocks.STONE) || OwnedBlocks.get(world).protectedBlock(p) || OwnedBlocks.get(world).claimed(p))return false;
        if(new Box(p).stretch(0,1,0).intersects(npc.getBoundingBox())
            || !world.getEntitiesByClass(LivingEntity.class,new Box(p.up()),e->e.isAlive() && e!=npc).isEmpty())return false;
        for(Direction dir:Direction.values())if(!world.isChunkLoaded(p.offset(dir)) || !world.getFluidState(p.offset(dir)).isEmpty())return false;
        return !(world.getBlockState(p.up()).getBlock() instanceof FallingBlock);
    }
    /** Natural overhead cover; trees and buildings do not count as caves.
     * Bounded to loaded blocks: this is a conservative local classification,
     * not a biome or a guarantee that every nearby route is safe. */
    public static boolean caveAccess(CompanionEntity npc,BlockPos feet) {
        var world=npc.getWorld();
        for(int dy=2;dy<=24 && feet.getY()+dy<world.getTopY();dy++) {
            BlockPos p=feet.up(dy);
            if(!world.isChunkLoaded(p) || !world.getFluidState(p).isEmpty())return false;
            var state=world.getBlockState(p);
            if(state.isIn(net.minecraft.registry.tag.BlockTags.LEAVES))continue;
            if(state.getCollisionShape(world,p).isEmpty())continue;
            if(world instanceof ServerWorld server && (OwnedBlocks.get(server).protectedBlock(p)
                    || OwnedBlocks.get(server).claimed(p)))return false;
            return state.isIn(net.minecraft.registry.tag.BlockTags.DIRT)
                || state.isOf(Blocks.STONE) || state.isOf(Blocks.DEEPSLATE)
                || state.isOf(Blocks.GRANITE) || state.isOf(Blocks.DIORITE)
                || state.isOf(Blocks.ANDESITE) || state.isOf(Blocks.TUFF);
        }
        return false;
    }
    private boolean undergroundWork(){return caveAccess(npc,npc.getBlockPos());}
    private boolean standable(BlockPos p,BlockPos block){
        var world=npc.getWorld();
        if(p.down().equals(block) || !world.isChunkLoaded(p)
            || !caveAccess(npc,p)
            || !world.getFluidState(p).isEmpty() || !world.getFluidState(p.up()).isEmpty()
            || !world.getBlockState(p.down()).isSolidBlock(world,p.down()))return false;
        var floor=world.getBlockState(p.down());
        if(floor.isOf(Blocks.MAGMA_BLOCK)||floor.isOf(Blocks.CAMPFIRE)||floor.isOf(Blocks.SOUL_CAMPFIRE))return false;
        if(!WorkNavigation.safeWorkFooting(npc,p))return false;
        return !world.getBlockCollisions(npc,new Box(p.getX()+0.15,p.getY()+0.01,p.getZ()+0.15,p.getX()+0.85,p.getY()+2,p.getZ()+0.85)).iterator().hasNext();
    }
    /** Wood pickup or bench work can leave us standing on the requested stone.
     * A neighboring bench does not make that support mineable: descend first,
     * without removing either support or entering an outdoor mining route. */
    private boolean leaveStoneSupport(){
        if(!(npc.getWorld() instanceof ServerWorld world))return false;
        BlockPos support=npc.getBlockPos().down();
        if(!world.isChunkLoaded(support) || !world.getBlockState(support).isOf(Blocks.STONE)
                || OwnedBlocks.get(world).protectedBlock(support)
                || OwnedBlocks.get(world).claimed(support))return false;
        if(!WorkNavigation.stepDown(npc,path->surfaceRoute(path,npc.getBlockY()-1)))return false;
        npc.intelligence().memory().remember("surface_stone",support,npc.getWorld().getTime(),24000);
        return true;
    }
    private boolean surfaceRoute(Path path,int y){
        if(!WorkNavigation.surfacePath(npc,path,y))return false;
        for(int i=0;i<path.getLength();i++){
            var node=path.getNode(i);
            if(!caveAccess(npc,new BlockPos(node.x,node.y,node.z)))return false;
        }
        return true;
    }
    private void selected(BlockPos block,BlockPos feet){
        target=block.toImmutable();stance=feet.toImmutable();routeStarted=npc.age;routePosition=npc.getPos();nextRoute=0;
        npc.intelligence().memory().remember("surface_stone",target,npc.getWorld().getTime(),24000);
    }
    private boolean select(BlockPos p){
        if(avoided.getOrDefault(p,0L)>npc.getWorld().getTime() || !safeBlock(p))return false;
        if(p.getY()<npc.getBlockY())floorSeen=true;
        // A ceiling block can be mined from the stable floor directly below it.
        // It must never be load-bearing or covered by falling materials/fluid.
        if(p.getY()>=npc.getBlockY()+2 && mining.reachable(p) && npc.isOnGround()
                && WorkNavigation.safeWorkFooting(npc,npc.getBlockPos())){
            selected(p,npc.getBlockPos());return true;
        }
        List<BlockPos> spots=new ArrayList<>();
        for(Direction dir:Direction.Type.HORIZONTAL)for(int dy=0;dy<=1;dy++){
            BlockPos feet=p.offset(dir).up(dy);if(standable(feet,p) && excavation.canOpenGalleryFrom(feet,p))spots.add(feet);
        }
        if(spots.isEmpty())return false;
        // A free-standing target below the working feet is a floor, not a wall.
        // Never individually remove this support: stairs are the only way down.
        if(spots.stream().noneMatch(feet->p.getY()>=feet.getY())){
            floorSeen=true;return false;
        }
        if(standable(npc.getBlockPos(),p) && excavation.canOpenGalleryFrom(npc.getBlockPos(),p) && mining.reachable(p)){selected(p,npc.getBlockPos());return true;}
        for(BlockPos feet:spots){
            Path path=npc.getNavigation().findPathTo(feet,0);
            if(surfaceRoute(path,feet.getY())){selected(p,feet);return true;}
        }
        avoided.put(p,npc.getWorld().getTime()+50);return false;
    }
    public String diagnostics(BlockPos p){return "safe="+safeBlock(p)+" ground="+npc.isOnGround()+" reachable="+mining.reachable(p)+" standable="+standable(npc.getBlockPos(),p)+" state="+npc.getWorld().getBlockState(p)+" target="+target+" column="+column;}
    public String scanDiagnostics(){return "target="+target+" stance="+stance+" column="+column
        +" height="+height+" passes="+passes+" retry="+waitUntil+" underground="+undergroundWork()
        +" excavation="+excavation.active();}
    public Result tick(){
        resume();
        if(!npc.getWorld().getGameRules().getBoolean(net.minecraft.world.GameRules.DO_MOB_GRIEFING)
            || !npc.getWorld().getGameRules().getBoolean(net.minecraft.world.GameRules.DO_TILE_DROPS))return Result.RULE_BLOCKED;
        if(!undergroundWork()){reset();return Result.NEED_CAVE;}
        if(!hasPick(npc)){mining.reset();return Result.NEED_TOOL;}
        if(excavation.active()){
            switch(excavation.tick()){
                case STONE_MINED -> {return Result.MINED;}
                case PROGRESSED -> {return Result.PROGRESSED;}
                case FINISHED, BLOCKED -> {
                    if(excavationSource!=null && !npc.getWorld().getBlockState(excavationSource).isAir())
                        avoided.put(excavationSource,npc.getWorld().getTime()+180);
                    excavationSource=null;target=null;stance=null;center=null;
                    column=0;height=-8;waitUntil=npc.age+10;return Result.ACTIVE;
                }
                default -> {return Result.ACTIVE;}
            }
        }
        if(npc.age<waitUntil)return Result.ACTIVE;
        if(target!=null && (!safeBlock(target) || !(target.getY()>=stance.getY()+2
                && WorkNavigation.safeWorkFooting(npc,stance)) && !standable(stance,target)))failed();
        if(target!=null){
            if(npc.isOnGround() && (standable(npc.getBlockPos(),target)
                    || (target.getY()>=npc.getBlockY()+2 && WorkNavigation.safeWorkFooting(npc,npc.getBlockPos())))
                    && (target.getY()>=npc.getBlockY()+2 || excavation.canOpenGalleryFrom(npc.getBlockPos(),target))
                    && mining.reachable(target)){
                // Stone at foot/head level is a wall: dig a real two-by-two
                // gallery rather than making a disconnected, dangerous hole.
                if(target.getY()<=npc.getBlockY()+1){
                    if(excavation.beginGallery(target)){
                        excavationSource=target;target=null;stance=null;return Result.ACTIVE;
                    }
                    failed();return Result.ACTIVE;
                }
                // Only overhead stone is harvested directly, never the floor.
                if(mining.tick(target)){target=null;stance=null;passes=0;column=0;height=-8;waitUntil=npc.age+12;return Result.MINED;}
                return Result.ACTIVE;
            }
            if(routePosition==null || npc.getPos().squaredDistanceTo(routePosition)>0.5){routePosition=npc.getPos();routeStarted=npc.age;}
            if(npc.age-routeStarted>120){failed();return Result.ACTIVE;}
            // Do not throw away a functioning path every second. Repath only when idle
            // or when measurable progress has stopped, preventing stop/start shuffling.
            if(npc.age>=nextRoute && npc.isOnGround()
                    && (npc.getNavigation().isIdle() || npc.age-routeStarted>45)){
                nextRoute=npc.age+20;Path path=npc.getNavigation().findPathTo(stance,0);
                if(!surfaceRoute(path,stance.getY()) || !npc.getNavigation().startMovingAlong(path,1.1))failed();
            }
            return Result.ACTIVE;
        }
        // Navigation during a jump is not evidence that the destination is unreachable.
        if(!npc.isOnGround())return Result.ACTIVE;
        if(center==null){center=npc.getBlockPos().toImmutable();scanStarted=npc.age;floorSeen=false;
            var memory=npc.intelligence().memory().nearest("surface_stone",center,npc.getWorld().getTime(),32);
            if(memory.isPresent() && select(memory.get()))return Result.ACTIVE;
        }
        // Reconsider the former support before taking another downward step.
        // Once below it, this observed stone is a wall we can mine normally.
        if(leaveStoneSupport() || WorkNavigation.leaveIsolatedPerch(npc)){
            center=null;return Result.ACTIVE;
        }
        // Underground stone floors may cover thousands of cells. Do not spend
        // minutes rejecting their unsafe individual blocks when we can instead
        // dig a supported staircase after a short wall/ceiling survey.
        if(floorSeen && undergroundWork() && npc.age-scanStarted>75 && npc.age-lastDigAttempt>160){
            lastDigAttempt=npc.age;
            if(excavation.beginStair())return Result.ACTIVE;
        }
        int candidates=0;
        for(int budget=0;budget<1632 && column<COLUMNS.size();budget++){
            BlockPos o=COLUMNS.get(column),p=center.add(o.getX(),height,o.getZ());
            if(++height>8){height=-8;column++;}
            if(avoided.getOrDefault(p,0L)>npc.getWorld().getTime() || !safeBlock(p))continue;
            // Sensing a loaded, exposed stone is not the same as being able to
            // reach it right now. Keep this real observation in bounded memory
            // even when a first navigation attempt cannot cover the distance.
            npc.intelligence().memory().remember("surface_stone",p,npc.getWorld().getTime(),24000);
            if(select(p))return Result.ACTIVE;
            if(++candidates==2)return Result.ACTIVE;
        }
        if(column<COLUMNS.size())return Result.ACTIVE;
        // A cave floor can be approached with a supported staircase. Outdoor
        // excavation is rejected before any route or digging is started.
        if(npc.age-lastDigAttempt>240){
            lastDigAttempt=npc.age;
            if(excavation.beginStair()){excavationSource=null;column=0;height=-8;return Result.ACTIVE;}
        }
        if(passes++<2){column=0;height=-8;waitUntil=npc.age+60;return Result.ACTIVE;}
        return Result.EXHAUSTED;
    }
}
