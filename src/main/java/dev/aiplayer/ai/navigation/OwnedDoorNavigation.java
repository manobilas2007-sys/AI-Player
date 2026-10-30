package dev.aiplayer.ai.navigation;

import dev.aiplayer.ai.OwnedBlocks;
import dev.aiplayer.entity.CompanionEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.RaycastContext;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Opens only the NPC's own wooden doors that its current physical route uses.
 * Planning is read-only; the actual door is operated only when the NPC arrives.
 */
public final class OwnedDoorNavigation {
    private static final int MAX_NPCS=128,MAX_OPEN=4;
    private static final Map<CompanionEntity,Opened> OPENED=new WeakHashMap<>();
    private static final class Opened {
        final ServerWorld world;
        final LinkedHashMap<BlockPos,Long> doors=new LinkedHashMap<>();
        Opened(ServerWorld world){this.world=world;}
    }
    private OwnedDoorNavigation() {}

    /** Return the lower half only for a complete, hand-openable door with both
     * halves still owned by this NPC. A protected/foreign door stays ordinary
     * terrain, even if it has the same wood palette as the house.
     */
    public static BlockPos ownedDoor(CompanionEntity npc,ServerWorld world,BlockPos pos){
        if(!world.isChunkLoaded(pos))return null;
        BlockState state=world.getBlockState(pos);
        if(!(state.getBlock() instanceof DoorBlock) || !state.isIn(BlockTags.WOODEN_DOORS)
                || !DoorBlock.canOpenByHand(state))return null;
        BlockPos lower=state.get(DoorBlock.HALF)==DoubleBlockHalf.UPPER?pos.down():pos;
        if(!world.isChunkLoaded(lower) || !world.isChunkLoaded(lower.up()))return null;
        BlockState bottom=world.getBlockState(lower),top=world.getBlockState(lower.up());
        if(!bottom.isOf(state.getBlock()) || !top.isOf(state.getBlock())
                || bottom.get(DoorBlock.HALF)!=DoubleBlockHalf.LOWER
                || top.get(DoorBlock.HALF)!=DoubleBlockHalf.UPPER
                || bottom.get(DoorBlock.FACING)!=top.get(DoorBlock.FACING)
                || bottom.get(DoorBlock.HINGE)!=top.get(DoorBlock.HINGE)
                || bottom.get(DoorBlock.OPEN)!=top.get(DoorBlock.OPEN))return null;
        OwnedBlocks ledger=OwnedBlocks.get(world);
        return !ledger.protectedBlock(lower) && !ledger.protectedBlock(lower.up())
            && ledger.owned(lower,npc.getUuid()) && ledger.owned(lower.up(),npc.getUuid())
                ?lower.toImmutable():null;
    }

    /** Collision check used by the planner. Only complete, owned wooden doors
     * are evaluated in their open state; every other block keeps its actual
     * collision shape. The door's open panel must still fit the swept body.
     */
    public static boolean collisionFree(CompanionEntity npc,ServerWorld world,Box box){
        boolean actualCollision=world.getBlockCollisions(npc,box).iterator().hasNext();
        boolean ownsDoor=false;
        for(BlockPos cell:BlockPos.iterate(BlockPos.ofFloored(box.minX,box.minY,box.minZ),
                BlockPos.ofFloored(box.maxX-0.000001,box.maxY-0.000001,box.maxZ-0.000001))){
            if(!world.isChunkLoaded(cell))return false;
            if(world.getBlockState(cell).getBlock() instanceof DoorBlock && ownedDoor(npc,world,cell)!=null){ownsDoor=true;break;}
        }
        // Door panels remain inside their own cells. Without an owned door
        // in the swept body there is no alternate shape to model, so retain
        // vanilla's answer instead of scanning all neighboring blocks again.
        if(!ownsDoor)return !actualCollision;
        var body=VoxelShapes.cuboid(box);
        BlockPos min=BlockPos.ofFloored(box.minX-1,Math.max(world.getBottomY(),box.minY-1),box.minZ-1);
        BlockPos max=BlockPos.ofFloored(box.maxX+1,Math.min(world.getTopY()-1,box.maxY+1),box.maxZ+1);
        ShapeContext context=ShapeContext.of(npc);
        for(BlockPos pos:BlockPos.iterate(min,max)){
            if(!world.isChunkLoaded(pos))return false;
            BlockState state=world.getBlockState(pos);
            if(state.getBlock() instanceof DoorBlock && ownedDoor(npc,world,pos)!=null)
                state=state.with(DoorBlock.OPEN,true);
            var shape=state.getCollisionShape(world,pos,context);
            if(!shape.isEmpty() && VoxelShapes.matchesAnywhere(shape.offset(pos.getX(),pos.getY(),pos.getZ()),
                    body,BooleanBiFunction.AND))return false;
        }
        return true;
    }

    public static void tick(CompanionEntity npc){
        if(!(npc.getWorld() instanceof ServerWorld world) || !world.getServer().isOnThread() || !npc.isAlive())return;
        Opened opened;
        synchronized(OPENED){
            opened=OPENED.get(npc);
            if(opened!=null && opened.world!=world){OPENED.remove(npc);opened=null;}
        }
        Path path=npc.getNavigation().getCurrentPath();
        if(opened!=null)closePassed(npc,world,path,opened);
        if(npc.menuOpen() || npc.isEating() || path==null || path.isFinished())return;
        int first=Math.max(0,path.getCurrentNodeIndex()-1),last=Math.min(path.getLength(),first+5);
        for(int index=first;index<last;index++){
            BlockPos door=ownedDoor(npc,world,path.getNodePos(index));
            if(door==null || npc.getEyePos().squaredDistanceTo(Vec3d.ofCenter(door.up()))>3.5*3.5)continue;
            BlockState state=world.getBlockState(door);
            if(state.get(DoorBlock.OPEN) || !visible(npc,world,door) || !safeToOpen(npc,world,door))continue;
            if(opened==null){
                opened=new Opened(world);
                synchronized(OPENED){
                    if(OPENED.size()>=MAX_NPCS)OPENED.remove(OPENED.keySet().iterator().next());
                    OPENED.put(npc,opened);
                }
            }
            if(opened.doors.size()>=MAX_OPEN && !opened.doors.containsKey(door))continue;
            ((DoorBlock)state.getBlock()).setOpen(npc,world,state,door,true);
            if(world.getBlockState(door).get(DoorBlock.OPEN)){
                opened.doors.put(door,world.getTime());NpcPathfinder.invalidate(npc);
            }
        }
    }
    private static boolean visible(CompanionEntity npc,ServerWorld world,BlockPos door){
        var hit=world.raycast(new RaycastContext(npc.getEyePos(),Vec3d.ofCenter(door.up()),
            RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,npc));
        return hit.getType()==HitResult.Type.MISS || hit.getBlockPos().equals(door) || hit.getBlockPos().equals(door.up());
    }
    private static boolean safeToOpen(CompanionEntity npc,ServerWorld world,BlockPos door){
        Box area=new Box(door).union(new Box(door.up())).expand(0.2);
        for(LivingEntity actor:world.getEntitiesByClass(LivingEntity.class,area,LivingEntity::isAlive)){
            var body=VoxelShapes.cuboid(actor.getBoundingBox());
            for(BlockPos half:new BlockPos[]{door,door.up()}){
                var shape=world.getBlockState(half).with(DoorBlock.OPEN,true)
                    .getCollisionShape(world,half,ShapeContext.of(actor));
                if(!shape.isEmpty() && VoxelShapes.matchesAnywhere(shape.offset(half.getX(),half.getY(),half.getZ()),
                        body,BooleanBiFunction.AND))return false;
            }
        }
        return true;
    }
    private static boolean stillAhead(Path path,BlockPos door){
        if(path==null || path.isFinished())return false;
        int first=Math.max(0,path.getCurrentNodeIndex()),last=Math.min(path.getLength(),first+5);
        for(int index=first;index<last;index++)if(path.getNodePos(index).equals(door))return true;
        return false;
    }
    private static void closePassed(CompanionEntity npc,ServerWorld world,Path path,Opened opened){
        for(Iterator<Map.Entry<BlockPos,Long>> iterator=opened.doors.entrySet().iterator();iterator.hasNext();){
            var entry=iterator.next();BlockPos door=entry.getKey();
            if(ownedDoor(npc,world,door)==null){iterator.remove();continue;}
            BlockState state=world.getBlockState(door);
            if(!state.get(DoorBlock.OPEN)){iterator.remove();continue;}
            if(world.getTime()-entry.getValue()<10 || state.get(DoorBlock.POWERED)
                    || stillAhead(path,door) || npc.squaredDistanceTo(Vec3d.ofCenter(door))<4)continue;
            Box doorway=new Box(door).union(new Box(door.up())).expand(0.2);
            if(!world.getEntitiesByClass(LivingEntity.class,doorway,LivingEntity::isAlive).isEmpty())continue;
            ((DoorBlock)state.getBlock()).setOpen(npc,world,state,door,false);
            iterator.remove();NpcPathfinder.invalidate(npc);
        }
    }
}
