package dev.aiplayer.ai;

import dev.aiplayer.entity.CompanionEntity;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;

/** Bounded local escape: real supports or a one-step staircase, never remote block removal. */
public final class TerrainRecovery {
    private final CompanionEntity npc;
    private final PhysicalMining mining;
    private final TemporaryPillar supports;
    private BlockPos step;
    private int started, retryAt, steps;
    private boolean active;
    private Vec3d recoveryAnchor;
    private int stationarySince, stepStarted;
    private int nextWaterRoute;
    private BlockPos waterCut;
    private Vec3d waterProgress;
    private int waterStalled;
    private final java.util.Map<BlockPos,Integer> failedSteps=new java.util.HashMap<>();
    public TerrainRecovery(CompanionEntity npc){this.npc=npc;mining=new PhysicalMining(npc);supports=new TemporaryPillar(npc);}
    private boolean clear(BlockPos p){return npc.getWorld().isChunkLoaded(p) && npc.getWorld().getBlockState(p).getCollisionShape(npc.getWorld(),p).isEmpty()
        && npc.getWorld().getFluidState(p).isEmpty();}
    private boolean soil(BlockPos p){
        var world=npc.getWorld();var s=world.getBlockState(p);
        if(!(world instanceof ServerWorld server) || !world.isChunkLoaded(p) || world.getBlockEntity(p)!=null
            || OwnedBlocks.get(server).protectedBlock(p) || OwnedBlocks.get(server).claimed(p))return false;
        if(!(s.isIn(BlockTags.DIRT) || s.isOf(Blocks.STONE) || s.isOf(Blocks.ANDESITE)
            || s.isOf(Blocks.DIORITE) || s.isOf(Blocks.GRANITE) || s.isOf(Blocks.SNOW) || s.isOf(Blocks.SNOW_BLOCK)
            || s.isOf(Blocks.SAND) || s.isOf(Blocks.RED_SAND) || s.isOf(Blocks.GRAVEL)))return false;
        // Do not open a liquid pocket or undermine falling sand/gravel.
        for(Direction d:Direction.values())if(!world.getFluidState(p.offset(d)).isEmpty())return false;
        return !(world.getBlockState(p.up()).getBlock() instanceof net.minecraft.block.FallingBlock);
    }
    /** A trapped worker can release a gravity column from its side when
     * the top is occluded. Stay outside its entire fall footprint; wait for
     * the real falling entities before entering the opened step. */
    private boolean controlledCollapse(BlockPos p){
        if(!(npc.getWorld() instanceof ServerWorld world) || !mining.reachable(p)
                || !(world.getBlockState(p).getBlock() instanceof net.minecraft.block.FallingBlock)
                || !world.getBlockState(p.down()).isSolidBlock(world,p.down()))return false;
        Box column=new Box(p).stretch(0,20,0);
        if(column.intersects(npc.getBoundingBox()) || !world.getOtherEntities(npc,column,e->e.isAlive()).isEmpty())return false;
        for(int y=0;y<=16;y++){
            BlockPos q=p.up(y);if(!world.isChunkLoaded(q))return false;
            if(!(world.getBlockState(q).getBlock() instanceof net.minecraft.block.FallingBlock))return world.getBlockState(q).isAir();
            if(OwnedBlocks.get(world).protectedBlock(q) || OwnedBlocks.get(world).claimed(q))return false;
            for(Direction side:Direction.values())if(!world.getFluidState(q.offset(side)).isEmpty())return false;
        }
        return false;
    }
    public boolean trapped(){
        if(!npc.isOnGround() || npc.isTouchingWater())return false;
        int walls=0;BlockPos feet=npc.getBlockPos();
        for(Direction d:Direction.Type.HORIZONTAL){BlockPos p=feet.offset(d);
            if(!clear(p) && !clear(p.up()))walls++;}
        if(recoveryAnchor==null || npc.getPos().squaredDistanceTo(recoveryAnchor)>4){
            recoveryAnchor=npc.getPos();stationarySince=npc.age;
        }
        // Corners of a 2x2 pit have only two adjacent walls; require sustained lack of progress.
        return walls==4 || ((walls>=2 || (walls>=1 && !TemporaryPillar.hasJumpSpace(npc))) && npc.age-stationarySince>=60);
    }
    public void pause(){mining.reset();}
    private boolean leaveWater(){
        if(!npc.isTouchingWater()){waterCut=null;waterProgress=null;return false;}
        if(waterCut!=null){
            if(clear(waterCut)){waterCut=null;mining.reset();}
            else if(soil(waterCut) && mining.reachable(waterCut)){mining.tick(waterCut);return true;}
            else {waterCut=null;mining.reset();}
        }
        npc.getJumpControl().setActive();
        if(waterProgress==null || npc.squaredDistanceTo(waterProgress)>0.1){waterProgress=npc.getPos();waterStalled=npc.age;}
        if(!npc.getNavigation().isIdle() && npc.age-waterStalled<60)return true;
        if(npc.age<nextWaterRoute)return true;
        nextWaterRoute=npc.age+15;
        BlockPos origin=npc.getBlockPos();
        var options=new java.util.ArrayList<BlockPos>();
        for(int dy=0;dy<=8;dy++)for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++){
            BlockPos p=origin.add(x,dy,z);
            if(npc.getWorld().isChunkLoaded(p) && npc.getWorld().isChunkLoaded(p.up())
                    && clear(p) && clear(p.up()) && WorkNavigation.safeWorkFooting(npc,p)
                    && npc.getWorld().isSpaceEmpty(npc,new Box(p).stretch(0,0.8,0)))options.add(p);
        }
        options.sort(java.util.Comparator.comparingDouble(p->p.getSquaredDistance(origin)));
        for(int i=0;i<Math.min(16,options.size());i++){
            var path=WorkNavigation.findPath(npc,options.get(i),0,dev.aiplayer.ai.navigation.NpcPathfinder.Mode.WATER);
            if(path!=null && path.reachesTarget() && npc.getNavigation().startMovingAlong(path,1.1))return true;
        }
        // A low natural bank may need headroom before the swimmer can climb.
        for(Direction side:Direction.Type.HORIZONTAL){
            BlockPos bank=origin.offset(side).up();
            if(npc.getWorld().isChunkLoaded(bank.up()) && soil(bank.up()) && clear(bank.up(2))
                    && npc.getWorld().getBlockState(bank.down()).isSolidBlock(npc.getWorld(),bank.down())
                    && mining.reachable(bank.up())){waterCut=bank.up();mining.tick(waterCut);return true;}
        }
        return true;
    }
    public boolean tick(){
        if(leaveWater())return true;
        if(npc.age<retryAt)return false;
        if(!active){
            if(npc.age%20!=0 || !trapped()){
                // Recovery never mines the block currently holding the NPC up.
                return supports.recoverFromSide(mining);
            }
            active=true;started=npc.age;steps=0;npc.getNavigation().stop();
        }
        if(npc.age-started>500 || steps>12){active=false;step=null;mining.reset();retryAt=npc.age+100;return false;}
        if(supports.isJumping()){supports.climb();return true;}
        if(step!=null && npc.age-stepStarted>100 && !mining.hasProgress()){
            failedSteps.put(step,npc.age+200);step=null;mining.reset();npc.getNavigation().stop();
        }
        if(step!=null){
            if(npc.getBlockY()>=step.getY() && npc.squaredDistanceTo(Vec3d.ofBottomCenter(step))<0.35){step=null;steps++;}
            else {
                for(BlockPos cut:new BlockPos[]{step.up(),step}){
                    if(clear(cut))continue;
                    // Expose the top of a gravity column first, then descend
                    // through it. A falling ceiling is never mined from below.
                    BlockPos upper=cut;
                    while(npc.getWorld().isChunkLoaded(upper.up())
                            && npc.getWorld().getBlockState(upper.up()).getBlock() instanceof net.minecraft.block.FallingBlock)
                        upper=upper.up();
                    if(!upper.equals(cut) && soil(upper) && mining.reachable(upper)){
                        if(mining.tick(upper))npc.intelligence().restoration().removed(upper);return true;
                    }
                    if((!soil(cut) && !controlledCollapse(cut)) || !mining.reachable(cut)){step=null;mining.reset();break;}
                    if(mining.tick(cut))npc.intelligence().restoration().removed(cut);return true;
                }
                if(step!=null){
                    if(!npc.getWorld().getEntitiesByClass(net.minecraft.entity.FallingBlockEntity.class,new Box(step).stretch(0,12,0),e->e.isAlive()).isEmpty())return true;
                    if(npc.age%10==0)npc.getNavigation().startMovingTo(step.getX()+0.5,step.getY(),step.getZ()+0.5,1);
                    return true;
                }
            }
        }
        if(!trapped()){
            if(!npc.isOnGround())return true;
            // Step out onto the rim before relinquishing movement; one rise is not an escape.
            BlockPos feet=npc.getBlockPos();
            for(Direction d:Direction.Type.HORIZONTAL){
                BlockPos rim=feet.offset(d).up();
                if(!clear(rim) || !clear(rim.up()) || !npc.getWorld().getBlockState(rim.down()).isSolidBlock(npc.getWorld(),rim.down()))continue;
                var path=WorkNavigation.findPath(npc,rim,0,dev.aiplayer.ai.navigation.NpcPathfinder.Mode.SAFE);
                if(WorkNavigation.safePath(npc,path,feet.getY())){step=rim;stepStarted=npc.age;npc.getNavigation().startMovingAlong(path,1);return true;}
            }
            active=false;mining.reset();return false;
        }
        // Move out from under an overhang before attempting any upward step.
        if(!TemporaryPillar.hasJumpSpace(npc)){
            BlockPos here=npc.getBlockPos();
            for(int radius=1;radius<=3;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
                if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
                BlockPos side=here.add(dx,0,dz);
                if(failedSteps.getOrDefault(side,0)>npc.age || !clear(side) || !clear(side.up())
                    || !npc.getWorld().getBlockState(side.down()).isSolidBlock(npc.getWorld(),side.down()))continue;
                Box swept=npc.getBoundingBox().offset(Vec3d.ofBottomCenter(side).subtract(npc.getPos())).stretch(0,1.25,0);
                if(!npc.getWorld().isSpaceEmpty(npc,swept))continue;
                var path=WorkNavigation.findPath(npc,side,0,dev.aiplayer.ai.navigation.NpcPathfinder.Mode.SAFE);
                if(WorkNavigation.safePath(npc,path,here.getY())){
                    step=side;stepStarted=npc.age;npc.getNavigation().startMovingAlong(path,1);return true;
                }
            }
            // Only natural, unprotected material may be removed overhead. Never dismantle a roof.
            for(int dy=2;dy<=3;dy++){
                BlockPos overhead=here.up(dy);
                if(soil(overhead) && mining.reachable(overhead)){if(mining.tick(overhead))npc.intelligence().restoration().removed(overhead);return true;}
                if(!clear(overhead))break;
            }
        }
        // A support preserves surrounding terrain, provided there is headroom and real material.
        if(supports.climb())return true;
        BlockPos feet=npc.getBlockPos();
        for(Direction d:Direction.Type.HORIZONTAL){
            BlockPos candidate=feet.offset(d).up();
            if(!TemporaryPillar.hasJumpSpace(npc) || failedSteps.getOrDefault(candidate,0)>npc.age)continue;
            if(!npc.getWorld().getBlockState(candidate.down()).isSolidBlock(npc.getWorld(),candidate.down()))continue;
            BlockPos cap=candidate.up();
            while(npc.getWorld().isChunkLoaded(cap.up())
                    && npc.getWorld().getBlockState(cap.up()).getBlock() instanceof net.minecraft.block.FallingBlock)cap=cap.up();
            boolean quarry=(soil(cap) && mining.reachable(cap)
                && npc.getWorld().getBlockState(candidate).getBlock() instanceof net.minecraft.block.FallingBlock) || controlledCollapse(candidate.up()) || controlledCollapse(candidate);
            if(quarry || ((clear(candidate)||soil(candidate)) && (clear(candidate.up())||soil(candidate.up())))){
                step=candidate;stepStarted=npc.age;return true;
            }
        }
        active=false;mining.reset();retryAt=npc.age+100;return false;
    }
    public void write(NbtCompound root){NbtCompound n=new NbtCompound();supports.writeNbt(n);root.put("EscapeSupports",n);}
    public void read(NbtCompound root){supports.readNbt(root.getCompound("EscapeSupports"));active=false;step=null;mining.reset();}
}
