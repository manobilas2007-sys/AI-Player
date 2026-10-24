package dev.aiplayer.ai;

import dev.aiplayer.entity.CompanionEntity;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.Direction;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameRules;
import java.util.ArrayList;
import java.util.List;

/** Place only after a real jump clears the destination. Recover in reverse order. */
public final class TemporaryPillar {
    private record Placed(BlockPos pos, BlockState state) {}
    private final CompanionEntity npc;
    private final List<Placed> placed = new ArrayList<>();
    private BlockPos jumping;
    private int jumpStarted, cleanupBlocked, cleanupRetryAt, centeringStarted=-1;
    // Descend a tall, isolated pillar one block at a time using ONE temporary
    // side foothold. The real top support and then the side foothold are mined
    // and dropped as items before descending to the next level.
    private enum Descent { NONE, TO_SIDE, MINE_TOP, WAIT_TOP_PICKUP, TO_COLUMN }
    private Descent descent=Descent.NONE;
    private BlockPos descentSide, descentUpper;
    private int descentSince;
    private int awaitingSupportSince=-1;
    public TemporaryPillar(CompanionEntity npc) { this.npc = npc; }
    public boolean cleanupReady(){return npc.age>=cleanupRetryAt;}
    public boolean isJumping() {return jumping!=null;}
    public boolean active() { return !placed.isEmpty() || jumping != null; }
    public boolean owns(BlockPos pos) { return placed.stream().anyMatch(p -> p.pos.equals(pos)); }
    /** Diagnostic only: no change to gameplay or success criteria. */
    public String diagnostics() {
        return "supports="+placed.size()+" stage="+descent+" upper="+descentUpper
            +" side="+descentSide+" age="+(npc.age-descentSince)
            +" grounded="+npc.isOnGround()+" under="+npc.getBlockPos().down()
            +" spare="+availableScaffoldBlocks()+" retry="+cleanupRetryAt
            +" recoveredDrops="+npc.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class,
                npc.getBoundingBox().expand(12),drop -> drop.isAlive()
                    && drop.getCommandTags().contains("aiplayer_recovered_support")
                    && drop.getCommandTags().contains("aiplayer_drop_"+npc.getUuid())).size();
    }
    public static boolean hasJumpSpace(CompanionEntity npc){
        // Test the entire body through the jump, including overhangs beside its center.
        return npc.getWorld().isSpaceEmpty(npc,npc.getBoundingBox().stretch(0,1.25,0));
    }
    public boolean climb() {
        if (!npc.getWorld().getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) return false;
        if (placed.size() >= 20) return false;
        // Supports are borrowed from real inventory and recovered by descending
        // onto the verified solid block below. No extra side block is required.
        int slot=scaffoldSlot();
        if (slot<0) return false;
        if (jumping == null) {
            if (!npc.isOnGround() || !hasJumpSpace(npc)) return false;
            BlockPos p=npc.getBlockPos();
            if (!npc.getWorld().getBlockState(p).isAir()
                    || !npc.getWorld().getBlockState(p.up(2)).isAir()
                    || !npc.getWorld().getBlockState(p.down()).isSolidBlock(npc.getWorld(),p.down())) return false;
            // Stay centered enough that the whole body lands on the support.
            if (Math.abs(npc.getX()-p.getX()-0.5)>0.25 || Math.abs(npc.getZ()-p.getZ()-0.5)>0.25) {
                if(centeringStarted<0)centeringStarted=npc.age;
                if(npc.age-centeringStarted>60){centeringStarted=-1;return false;}
                steerToDescentFooting(p);return true;
            }
            centeringStarted=-1;jumping=p.toImmutable();jumpStarted=npc.age;
            holdHorizontal();npc.setVelocity(0,0.42,0);return true;
        }
        if (npc.age-jumpStarted>20) { jumping=null;return false; }
        if (npc.getY()<jumping.getY()+1.0) return true;
        if (!npc.getWorld().getBlockState(jumping).isAir()
                || !npc.getWorld().getOtherEntities(null,new Box(jumping)).isEmpty()) {jumping=null;return false;}
        Box landing=npc.getBoundingBox().offset(0,jumping.getY()+1-npc.getY(),0);
        if(!npc.getWorld().isSpaceEmpty(npc,landing)){jumping=null;return false;}
        ItemStack stack=npc.backpack.getStack(slot);
        BlockState state=((BlockItem)stack.getItem()).getBlock().getDefaultState();
        if (npc.getWorld().setBlockState(jumping,state,3)) {
            if(npc.getWorld() instanceof net.minecraft.server.world.ServerWorld server)OwnedBlocks.get(server).claim(jumping,npc.getUuid());
            stack.decrement(1);npc.backpack.markDirty();placed.add(new Placed(jumping,state));
            npc.animateInteraction(CompanionEntity.PLACE,8);
        }
        jumping=null;return true;
    }
    private boolean reusableScaffold(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem
            && (stack.isOf(Items.DIRT) || stack.isOf(Items.COBBLESTONE)
                || stack.isIn(ItemTags.LOGS) || stack.isIn(ItemTags.PLANKS));
    }
    private int availableScaffoldBlocks() {
        int total=0;
        for(int i=0;i<npc.backpack.size();i++) {
            ItemStack stack=npc.backpack.getStack(i);
            if(reusableScaffold(stack))total+=stack.getCount();
        }
        return total;
    }
    private int scaffoldSlot() {
        // Consume readily replaceable dirt/stone before valuable wooden logs.
        for(int i=0;i<npc.backpack.size();i++) {
            ItemStack stack=npc.backpack.getStack(i);
            if(stack.isOf(Items.DIRT) || stack.isOf(Items.COBBLESTONE))return i;
        }
        for(int i=0;i<npc.backpack.size();i++)
            if(reusableScaffold(npc.backpack.getStack(i)))return i;
        return -1;
    }
    /** Creates a temporary side ledge one level below the NPC, from REAL inventory.
     * Both the original tower block and the ledge remain owned until collected.
     * One block is sufficient, since it gets recycled at EVERY descending level.
     */
    private boolean beginDescent(Placed upper) {
        var world=npc.getWorld();
        if(!world.getBlockState(upper.pos.down()).isSolidBlock(world,upper.pos.down()))return false;
        int slot=scaffoldSlot();
        if(slot<0)return false;
        for(Direction direction:Direction.Type.HORIZONTAL) {
            BlockPos side=upper.pos.offset(direction).down();
            if(!world.isChunkLoaded(side) || !world.isChunkLoaded(side.up(2)))continue;
            if(!world.getBlockState(side).isAir() || !world.getFluidState(side).isEmpty()
                    || !world.getBlockState(side.up()).getCollisionShape(world,side.up()).isEmpty()
                    || !world.getBlockState(side.up(2)).getCollisionShape(world,side.up(2)).isEmpty()
                    || !world.getFluidState(side.up()).isEmpty())continue;
            if(!world.getOtherEntities(null,new Box(side)).isEmpty())continue;
            if(world instanceof net.minecraft.server.world.ServerWorld sw &&
                    (OwnedBlocks.get(sw).protectedBlock(side) || OwnedBlocks.get(sw).claimed(side)))continue;
            ItemStack stack=npc.backpack.getStack(slot);
            if(!reusableScaffold(stack))return false;
            BlockState material=((BlockItem)stack.getItem()).getBlock().getDefaultState();
            if(!world.setBlockState(side,material,3))continue;
            if(world instanceof net.minecraft.server.world.ServerWorld sw)
                OwnedBlocks.get(sw).claim(side,npc.getUuid());
            stack.decrement(1);npc.backpack.markDirty();
            placed.add(new Placed(side.toImmutable(),material));
            npc.animateInteraction(CompanionEntity.PLACE,8);
            descent=Descent.TO_SIDE;
            descentSide=side.toImmutable();descentUpper=upper.pos.toImmutable();
            descentSince=npc.age;awaitingSupportSince=-1;cleanupBlocked=0;
            return true;
        }
        return false;
    }
    private boolean steerToDescentFooting(BlockPos feet) {
        double x=feet.getX()+0.5-npc.getX(),z=feet.getZ()+0.5-npc.getZ();
        double distance=Math.sqrt(x*x+z*z);
        if(distance>0.10) {
            // Vanilla navigation refuses steps off a ledge. Move horizontally
            // toward the verified temporary foothold without teleporting or
            // changing the vertical velocity; collision and gravity still apply.
            npc.getNavigation().stop();
            npc.getMoveControl().moveTo(npc.getX(),npc.getY(),npc.getZ(),0);
            npc.setForwardSpeed(0);npc.setSidewaysSpeed(0);
            double motion=Math.min(0.18,distance*0.4);
            npc.setVelocity(x/distance*motion,npc.getVelocity().y,z/distance*motion);
        } else holdHorizontal();
        return true;
    }
    private boolean descentTick(PhysicalMining mining) {
        if(descent==Descent.NONE)return false;
        if(descentUpper==null || descentSide==null) {clearDescent();return false;}
        if(!npc.getWorld().isChunkLoaded(descentUpper) || !npc.getWorld().isChunkLoaded(descentSide)
                || !npc.getWorld().getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING))return deferCleanup(mining);
        if(npc.age-descentSince>240){
            // Keep all placed blocks tracked. Another attempt can resume safely.
            descentSince=npc.age;
            return deferCleanup(mining);
        }
        BlockPos under=npc.getBlockPos().down();
        switch(descent) {
            case TO_SIDE -> {
                if(npc.isOnGround() && under.equals(descentSide)) {
                    descent=Descent.MINE_TOP;descentSince=npc.age;
                    holdHorizontal();return true;
                }
                return steerToDescentFooting(descentSide.up());
            }
            case MINE_TOP -> {
                if(!npc.isOnGround() || !under.equals(descentSide)) {
                    descent=Descent.TO_SIDE;return true;
                }
                holdHorizontal();
                if(npc.getWorld().getBlockState(descentUpper).isAir()){
                    placed.removeIf(p->p.pos.equals(descentUpper));
                    descent=Descent.WAIT_TOP_PICKUP;descentSince=npc.age;return true;
                }
                if(mining.tick(descentUpper,true)) {
                    placed.removeIf(p->p.pos.equals(descentUpper));
                    // Do not walk away from the physical item on the next
                    // column. The recovered log is the material needed to
                    // construct the following side foothold.
                    descent=Descent.WAIT_TOP_PICKUP;descentSince=npc.age;
                }
                return true;
            }
            case WAIT_TOP_PICKUP -> {
                // Remain on the safe side ledge while SurvivalController
                // actually picks up the tagged drop (vanilla pickup delay).
                // Without this stage a one-block scaffold budget gets spent
                // after the very first level of a tall pillar.
                if(!npc.isOnGround() || !under.equals(descentSide)) {
                    descent=Descent.TO_SIDE;return true;
                }
                if(scaffoldSlot()>=0 || !hasUpperSupportDrop(descentUpper.down())
                        || npc.age-descentSince>=100) {
                    descent=Descent.TO_COLUMN;descentSince=npc.age;
                } else npc.getNavigation().stop();
                return true;
            }
            case TO_COLUMN -> {
                BlockPos next=descentUpper.down();
                if(npc.isOnGround() && under.equals(next)) {
                    // The original upper block has dropped onto the next step.
                    // Do not start another descent until that REAL item can be
                    // picked up. A six-high pillar may start with just one spare
                    // scaffold block, and it must be recycled level by level.
                    if(scaffoldSlot()<0 && hasUpperSupportDrop(next)
                            && npc.age-descentSince<100) {
                        if(awaitingSupportSince<0)awaitingSupportSince=npc.age;
                        npc.getNavigation().stop();return true;
                    }
                    clearDescent();holdHorizontal();return true;
                }
                return steerToDescentFooting(next.up());
            }
            default -> {return false;}
        }
    }
    private boolean hasUpperSupportDrop(BlockPos next) {
        // Tagged drops are produced by PhysicalMining, never materialized here.
        return !npc.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class,
            new Box(next).expand(2),drop -> drop.isAlive()
                && drop.getCommandTags().contains("aiplayer_recovered_support")
                && drop.getCommandTags().contains("aiplayer_drop_"+npc.getUuid())
                && reusableScaffold(drop.getStack())).isEmpty();
    }
    private void clearDescent(){descent=Descent.NONE;descentSide=null;descentUpper=null;awaitingSupportSince=-1;}

    /** Step sideways off a short isolated pillar onto actual safe terrain.
     * This is movement (not teleport), and is allowed only when the free drop
     * is at most two blocks, with no lava, water or obstructing collision.
     * Once grounded, the normal mining code reclaims every tracked support. */
    private boolean descendToAdjacentGround(BlockPos support) {
        BlockPos feet=npc.getBlockPos();
        if(!npc.isOnGround() || !feet.down().equals(support))return false;
        var world=npc.getWorld();
        for(net.minecraft.util.math.Direction direction:net.minecraft.util.math.Direction.Type.HORIZONTAL){
            BlockPos side=feet.offset(direction);
            // The air column immediately alongside the support must remain open
            // all the way down to a landing no more than two blocks below.
            if(!world.isChunkLoaded(side) || !world.getBlockState(side).getCollisionShape(world,side).isEmpty()
                    || !world.getBlockState(side.up()).getCollisionShape(world,side.up()).isEmpty())continue;
            for(int fall=1;fall<=2;fall++){
                BlockPos landing=side.down(fall);
                if(!world.isChunkLoaded(landing) || !world.isChunkLoaded(landing.down()))continue;
                if(!WorkNavigation.safeWorkFooting(npc,landing))continue;
                boolean free=true;
                for(int offset=0;offset<=fall;offset++){
                    BlockPos swept=side.down(offset);
                    if(!world.isChunkLoaded(swept) || !world.getFluidState(swept).isEmpty()){
                        free=false;break;
                    }
                    BlockState terrain=world.getBlockState(swept);
                    boolean shallowSnow=swept.equals(landing) && terrain.isOf(net.minecraft.block.Blocks.SNOW)
                        && terrain.get(net.minecraft.block.SnowBlock.LAYERS)<=2;
                    if(!shallowSnow && !terrain.getCollisionShape(world,swept).isEmpty()){
                        free=false;break;
                    }
                }
                if(!free)continue;
                // Do not ask vanilla pathfinding to navigate off a drop: it
                // deliberately refuses. Apply a small horizontal walking
                // velocity towards the clear landing, then let gravity work.
                double x=side.getX()+0.5-npc.getX(),z=side.getZ()+0.5-npc.getZ();
                double distance=Math.sqrt(x*x+z*z);
                if(distance<0.1)continue;
                npc.getNavigation().stop();
                npc.getMoveControl().moveTo(side.getX()+0.5,landing.getY(),side.getZ()+0.5,1.05);
                npc.setVelocity(x/distance*0.21,npc.getVelocity().y,z/distance*0.21);
                return true;
            }
        }
        return false;
    }
    /** Mine only our recorded support, with a verified one-block landing and
     * the whole body inside the column. No spare material or teleport needed. */
    private boolean canDescendOneBlock(Placed support){
        var world=npc.getWorld();
        BlockPos below=support.pos.down();
        if(!world.isChunkLoaded(below) || !world.getFluidState(below).isEmpty()
                || !world.getBlockState(below).isSolidBlock(world,below))return false;
        var floor=world.getBlockState(below);
        if(floor.isOf(net.minecraft.block.Blocks.MAGMA_BLOCK)
                || floor.isOf(net.minecraft.block.Blocks.CAMPFIRE)
                || floor.isOf(net.minecraft.block.Blocks.SOUL_CAMPFIRE))return false;
        Box body=npc.getBoundingBox();
        if(body.minX<support.pos.getX() || body.maxX>support.pos.getX()+1
                || body.minZ<support.pos.getZ() || body.maxZ>support.pos.getZ()+1)return false;
        Box landing=body.offset(0,-1,0);
        if(!world.getOtherEntities(npc,landing).isEmpty())return false;
        for(BlockPos cell:BlockPos.iterate(BlockPos.ofFloored(landing.minX,landing.minY,landing.minZ),
                BlockPos.ofFloored(body.maxX,body.maxY,body.maxZ)))
            if(!cell.equals(support.pos) && (!world.getFluidState(cell).isEmpty()
                    || !world.getBlockState(cell).getCollisionShape(world,cell).isEmpty()))return false;
        return true;
    }
    private void holdHorizontal(){
        npc.getNavigation().stop();
        npc.getMoveControl().moveTo(npc.getX(),npc.getY(),npc.getZ(),0);
        npc.setForwardSpeed(0);npc.setSidewaysSpeed(0);
        npc.setVelocity(0,npc.getVelocity().y,0);
    }
    private boolean deferCleanup(PhysicalMining mining) {
        if(++cleanupBlocked<40)return true;
        cleanupBlocked=0;cleanupRetryAt=npc.age+100;
        mining.reset();npc.getNavigation().stop();npc.clearWorkingVisual();
        return false;
    }
    public boolean cleanup(PhysicalMining mining) {
        if(npc.age<cleanupRetryAt)return false;
        jumping=null;
        if(descent!=Descent.NONE)return descentTick(mining);
        if(awaitingSupportSince>=0 && scaffoldSlot()<0 && !placed.isEmpty()) {
            // Give the tagged physical drop time to fall onto the next ledge
            // and be collected; never silently place a fresh side support.
            if(npc.age-awaitingSupportSince<90) {
                npc.getNavigation().stop();return true;
            }
            awaitingSupportSince=-1;
        }
        if(scaffoldSlot()>=0)awaitingSupportSince=-1;
        while (!placed.isEmpty()) {
            Placed p=placed.get(placed.size()-1);
            if (!npc.getWorld().isChunkLoaded(p.pos)
                    || !npc.getWorld().getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING))return deferCleanup(mining);
            if (npc.getWorld() instanceof net.minecraft.server.world.ServerWorld server
                    && (OwnedBlocks.get(server).protectedBlock(p.pos)
                        || !OwnedBlocks.get(server).owned(p.pos,npc.getUuid()))) {
                // The player or another entity has claimed this support. Do not touch it.
                placed.remove(placed.size()-1);continue;
            }
            if (!npc.getWorld().getBlockState(p.pos).equals(p.state)) {placed.remove(placed.size()-1);continue;}
            if (!npc.isOnGround()) return deferCleanup(mining);
            if (npc.getBlockPos().down().equals(p.pos)) {
                if(npc.getWorld().getBlockState(p.pos.down()).isSolidBlock(npc.getWorld(),p.pos.down())
                        && (Math.abs(npc.getX()-p.pos.getX()-0.5)>0.12
                            || Math.abs(npc.getZ()-p.pos.getZ()-0.5)>0.12)){
                    return steerToDescentFooting(p.pos.up());
                }
                if(canDescendOneBlock(p)){
                    holdHorizontal();cleanupBlocked=0;
                    if(mining.tick(p.pos,true))placed.remove(placed.size()-1);
                    return true;
                }
                // Mining our own floor would drop us straight back into the hole.
                // Leave the pillar intact until we can stand on natural adjacent ground.
                if(!npc.getNavigation().isIdle())return true;
                for (net.minecraft.util.math.Direction dir:net.minecraft.util.math.Direction.Type.HORIZONTAL) {
                    BlockPos exit=p.pos.offset(dir).up();
                    if (!npc.getWorld().isChunkLoaded(exit) || !npc.getWorld().isChunkLoaded(exit.down()))continue;
                    // Clear only a leaf blocking escape, never an unknown player's roof.
                    if(npc.getWorld().getBlockState(exit).isIn(net.minecraft.registry.tag.BlockTags.LEAVES)
                            && mining.reachable(exit)){mining.tick(exit);return true;}
                    if(npc.getWorld().getBlockState(exit.up()).isIn(net.minecraft.registry.tag.BlockTags.LEAVES)
                            && mining.reachable(exit.up())){mining.tick(exit.up());return true;}
                    if (!npc.getWorld().getBlockState(exit).getCollisionShape(npc.getWorld(),exit).isEmpty()
                            || !npc.getWorld().getBlockState(exit.up()).getCollisionShape(npc.getWorld(),exit.up()).isEmpty())continue;
                    boolean waterExit=npc.getWorld().getFluidState(exit).isIn(net.minecraft.registry.tag.FluidTags.WATER)
                            && npc.getAir()>150 && npc.getBlockY()-exit.getY()<=1;
                    if (!waterExit && !WorkNavigation.safeWorkFooting(npc,exit))continue;
                    if(!waterExit){
                        var path=WorkNavigation.findPath(npc,exit,0,dev.aiplayer.ai.navigation.NpcPathfinder.Mode.SAFE);
                        if (WorkNavigation.safePath(npc,path,Math.min(exit.getY(),npc.getBlockY())-1)
                                && npc.getNavigation().startMovingAlong(path,1.05))return true;
                    }
                    // If pathfinding refuses a short descent, steer towards a verified exit.
                    npc.getNavigation().stop();
                    npc.getMoveControl().moveTo(exit.getX()+0.5,exit.getY(),exit.getZ()+0.5,0.95);
                    if(waterExit)npc.getJumpControl().setActive();
                    return true;
                }
                if(descendToAdjacentGround(p.pos)){
                    cleanupBlocked=0;
                    return true;
                }
                // A high tower needs a reversible one-block-at-a-time descent,
                // not an unplanned leap or abandonment of the wood supports.
                if(beginDescent(p))return true;
                return deferCleanup(mining);
            }
            if (!mining.reachable(p.pos)) {
                if (npc.age%20==0) npc.getNavigation().startMovingTo(p.pos.getX()+0.5,p.pos.getY()+1,p.pos.getZ()+0.5,1);
                return deferCleanup(mining);
            }
            if(!npc.backpack.canInsert(new ItemStack(p.state.getBlock().asItem())))return deferCleanup(mining);
            cleanupBlocked=0;
            if (mining.tick(p.pos,true)) {
                placed.remove(placed.size()-1);
                // A side foothold is REAL material. Recover it through the
                // existing physical pickup, not by inventing another block.
                if(scaffoldSlot()<0)awaitingSupportSince=npc.age;
            }
            return true;
        }
        return false;
    }
    /** Recover escape supports only from adjacent stable ground; never navigate back into the pit. */
    public boolean recoverFromSide(PhysicalMining mining) {
        if(!npc.isOnGround() || placed.isEmpty())return false;
        for(int i=placed.size()-1;i>=0;i--){
            Placed p=placed.get(i);
            if(!npc.getWorld().isChunkLoaded(p.pos))continue;
            if(!npc.getWorld().getBlockState(p.pos).equals(p.state)
                || (npc.getWorld() instanceof net.minecraft.server.world.ServerWorld w && !OwnedBlocks.get(w).owned(p.pos,npc.getUuid()))){placed.remove(i);continue;}
            if(npc.getBlockPos().down().equals(p.pos) || npc.getBoundingBox().intersects(new Box(p.pos).stretch(0,1,0)))continue;
            if(!mining.reachable(p.pos))continue;
            if(mining.tick(p.pos,true))placed.remove(i);
            return true;
        }
        return false;
    }
    public void writeNbt(NbtCompound root) {
        NbtList list=new NbtList();
        for (Placed p:placed) {NbtCompound n=new NbtCompound();n.putLong("Pos",p.pos.asLong());n.putString("Block",net.minecraft.registry.Registries.BLOCK.getId(p.state.getBlock()).toString());list.add(n);}
        root.put("TemporaryPillar",list);
        if(descent!=Descent.NONE && descentSide!=null && descentUpper!=null){
            root.putString("PillarDescent",descent.name());
            root.putLong("PillarDescentSide",descentSide.asLong());
            root.putLong("PillarDescentUpper",descentUpper.asLong());
        }
    }
    public void readNbt(NbtCompound root) {
        placed.clear();jumping=null;clearDescent();
        NbtList list=root.getList("TemporaryPillar",NbtElement.COMPOUND_TYPE);
        for(int i=0;i<Math.min(20,list.size());i++) {
            NbtCompound n=list.getCompound(i);
            BlockState state=net.minecraft.registry.Registries.BLOCK.get(new net.minecraft.util.Identifier(n.getString("Block"))).getDefaultState();
            placed.add(new Placed(BlockPos.fromLong(n.getLong("Pos")),state));
        }
        if(root.contains("PillarDescent",NbtElement.STRING_TYPE)
                && root.contains("PillarDescentSide",NbtElement.LONG_TYPE)
                && root.contains("PillarDescentUpper",NbtElement.LONG_TYPE)) {
            try {
                descent=Descent.valueOf(root.getString("PillarDescent"));
                descentSide=BlockPos.fromLong(root.getLong("PillarDescentSide"));
                descentUpper=BlockPos.fromLong(root.getLong("PillarDescentUpper"));
                descentSince=npc.age;
            }catch(IllegalArgumentException ignored){clearDescent();}
        }
    }
}
