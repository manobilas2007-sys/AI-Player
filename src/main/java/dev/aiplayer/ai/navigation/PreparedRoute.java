/*
 * Server adaptation of Baritone (c) leijurv and contributors.
 * SPDX-License-Identifier: LGPL-3.0-or-later
 * Upstream: 8c55ad0721d42a01a359834edc16aaa9b5bc587d (1.10.5).
 * NPC changes: server entity movement/physics, bounded scanning, actual
 * inventory, task ownership and persistence. See embedded-baritone/UPSTREAM.md.
 */
package dev.aiplayer.ai.navigation;

import dev.aiplayer.ai.*;
import dev.aiplayer.ai.brain.ResourcePlan;
import dev.aiplayer.baritone.*;
import dev.aiplayer.baritone.Position;
import dev.aiplayer.entity.CompanionEntity;
import net.minecraft.block.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.world.GameRules;
import java.util.*;
import java.util.function.Predicate;

/** A local Baritone graph whose costly edges are executed, not teleported.
 * Walking competes with cutting natural headroom and placing real footings.
 * Each edge is revalidated against the live world before any modification. */
public final class PreparedRoute {
    private final CompanionEntity npc;
    private final Predicate<BlockPos> boundary;
    private final PhysicalMining mining;
    private final ArrayDeque<BlockPos> steps=new ArrayDeque<>();
    private final Map<BlockPos,Movement> actions=new HashMap<>();
    private final Map<BlockPos,Double> estimatedCosts=new HashMap<>();
    private final JumpMotion jump;
    private final TemporaryPillar pillar;
    private int started, nextSearch, lastMove,requiredSupports;
    private Vec3d last;
    private BlockPos progressStep;
    private double bestStepDistance=Double.POSITIVE_INFINITY;
    private UUID supplyDrop;
    private double dropDistance;
    private int dropProgress;
    private final Map<UUID,Integer> unavailableDrops=new HashMap<>();
    private String status="Preparar passagem";
    private boolean allowChanges=true;
    private int lastTick=-1;
    public void allowChanges(boolean enabled){if(allowChanges!=enabled){pause();allowChanges=enabled;}}
    public PreparedRoute(CompanionEntity npc,Predicate<BlockPos> boundary){
        this.npc=npc;this.boundary=boundary;mining=new PhysicalMining(npc);jump=new JumpMotion(npc);pillar=new TemporaryPillar(npc);
    }
    public boolean active(){return !steps.isEmpty();}
    public String status(){return status;}
    public String diagnostics(){return status+" next="+steps.peek()+" remaining="+steps.size()+" supplies="+requiredSupports+" supplyDrop="+supplyDrop;}
    public boolean swimming(){return active() && actions.get(steps.peek())==Movement.SWIM;}
    private void releaseMotion(){
        npc.getMoveControl().moveTo(npc.getX(),npc.getY(),npc.getZ(),0);
        npc.setForwardSpeed(0);npc.setSidewaysSpeed(0);npc.getJumpControl().tick();npc.setJumping(false);
        npc.setVelocity(0,npc.getVelocity().y,0);
    }
    public void pause(){boolean moving=active();steps.clear();actions.clear();estimatedCosts.clear();jump.reset();mining.reset();last=null;progressStep=null;requiredSupports=0;npc.setSprinting(false);npc.navigationPlan().stopOrdinary();if(moving)releaseMotion();}
    private ServerWorld world(){return (ServerWorld)npc.getWorld();}
    private boolean loaded(BlockPos p){return boundary.test(p) && world().isChunkLoaded(p)
        && world().getWorldBorder().contains(p) && p.getY()>world().getBottomY()+1 && p.getY()<world().getTopY()-2;}
    private boolean dry(BlockPos p){return loaded(p) && world().getFluidState(p).isEmpty();}
    private boolean clear(BlockPos p){return dry(p) && world().getBlockState(p).getCollisionShape(world(),p).isEmpty()
        && !hazard(p);}
    private boolean hazard(BlockPos p){var s=world().getBlockState(p);return s.isOf(Blocks.FIRE) || s.isOf(Blocks.SOUL_FIRE)
        || s.isOf(Blocks.MAGMA_BLOCK) || s.isOf(Blocks.CACTUS) || s.isOf(Blocks.POWDER_SNOW)
        || s.isOf(Blocks.CAMPFIRE) || s.isOf(Blocks.SOUL_CAMPFIRE) || s.isOf(Blocks.SWEET_BERRY_BUSH);}
    private boolean solid(BlockPos p){return dry(p) && !hazard(p) && world().getBlockState(p).isSolidBlock(world(),p);}
    private boolean water(BlockPos p){return loaded(p) && world().getFluidState(p).isIn(net.minecraft.registry.tag.FluidTags.WATER);}
    private boolean aquatic(BlockPos p){
        return loaded(p) && loaded(p.up()) && !hazard(p) && !hazard(p.up())
            && (dry(p)||water(p)) && (dry(p.up())||water(p.up()))
            && world().getBlockState(p).getCollisionShape(world(),p).isEmpty()
            && world().getBlockState(p.up()).getCollisionShape(world(),p.up()).isEmpty();
    }
    private boolean climbable(BlockPos p){return loaded(p) && world().getBlockState(p).isIn(net.minecraft.registry.tag.BlockTags.CLIMBABLE);}
    private boolean editable(BlockPos p){return dry(p) && world().getBlockEntity(p)==null
        && allowChanges
        && !OwnedBlocks.get(world()).protectedBlock(p) && !OwnedBlocks.get(world()).claimed(p);}
    private ItemStack tool(BlockState state){
        ItemStack best=npc.getMainHandStack();
        if(npc.autoEquip)for(int i=0;i<npc.backpack.size();i++){
            var candidate=npc.backpack.getStack(i);
            if(!ResourcePlan.worn(candidate) && candidate.getMiningSpeedMultiplier(state)>best.getMiningSpeedMultiplier(state))best=candidate;
        }
        return best;
    }
    private double cutCost(BlockPos p){
        if(clear(p))return 0;
        if(!editable(p))return Double.POSITIVE_INFINITY;
        var s=world().getBlockState(p);
        if(!SafeExcavation.isExcavatableMaterial(s) && !s.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)
                && !s.isIn(net.minecraft.registry.tag.BlockTags.COAL_ORES)
                && !s.isIn(net.minecraft.registry.tag.BlockTags.IRON_ORES)
                && !s.isIn(net.minecraft.registry.tag.BlockTags.COPPER_ORES)
                && !s.isIn(net.minecraft.registry.tag.BlockTags.GOLD_ORES)
                && !s.isIn(net.minecraft.registry.tag.BlockTags.DIAMOND_ORES)
                && !s.isIn(net.minecraft.registry.tag.BlockTags.REDSTONE_ORES)
                && !s.isIn(net.minecraft.registry.tag.BlockTags.LAPIS_ORES)
                && !s.isIn(net.minecraft.registry.tag.BlockTags.EMERALD_ORES)
                && !s.isOf(Blocks.SAND) && !s.isOf(Blocks.RED_SAND) && !s.isOf(Blocks.GRAVEL)
                && !(s.isIn(net.minecraft.registry.tag.BlockTags.LOGS) && npc.treeHarvest().observe(p)))return Double.POSITIVE_INFINITY;
        if(world().getBlockState(p.up()).getBlock() instanceof FallingBlock)return Double.POSITIVE_INFINITY;
        for(Direction d:Direction.values())if(!dry(p.offset(d)))return Double.POSITIVE_INFINITY;
        ItemStack tool=tool(s);
        if(s.isToolRequired() && (!tool.isSuitableFor(s) || ResourcePlan.worn(tool)))return Double.POSITIVE_INFINITY;
        float hardness=s.getHardness(world(),p);
        return hardness<0?Double.POSITIVE_INFINITY:Math.max(1,hardness*30/Math.max(1,tool.getMiningSpeedMultiplier(s)));
    }
    private ItemStack support(){
        for(Item item:new Item[]{Items.DIRT,Items.COBBLESTONE,Items.COBBLED_DEEPSLATE}){
            for(int i=0;i<npc.backpack.size();i++)if(npc.backpack.getStack(i).isOf(item))return npc.backpack.getStack(i);
            if(npc.getMainHandStack().isOf(item))return npc.getMainHandStack();
        }
        return ItemStack.EMPTY;
    }
    private boolean bridgeCell(BlockPos p){
        // Place into observed dry air from an attached edge; the executor
        // completes each support before allowing the NPC to step over it.
        return editable(p) && world().getBlockState(p).isAir() && dry(p.down()) && !hazard(p.down());
    }
    private Iterable<Step> neighbors(Position from){
        var result=new ArrayList<Step>();BlockPos base=new BlockPos(from.x(),from.y(),from.z());
        for(Direction d:Direction.Type.HORIZONTAL)for(int dy:new int[]{0,1,-1}){
            BlockPos next=base.offset(d).up(dy);
            if(!loaded(next.up()) || !loaded(next.down()) || npc.navigationPlan().avoid(next))continue;
            if(npc.getAir()>150 && aquatic(base) && aquatic(next)
                    && (water(base)||water(base.down())||water(next)||water(next.down())))
                if(water(next)||water(next.down())||solid(next.down())){
                    result.add(new Step(new Position(next.getX(),next.getY(),next.getZ()),
                        ActionCosts.WALK_ONE_IN_WATER_COST+Math.abs(dy)*4,Movement.SWIM));continue;
                }
            double cut=cutCost(next)+cutCost(next.up());
            if(dy>0)cut+=cutCost(base.up(2));
            if(dy<0)cut+=cutCost(next.up(2));
            boolean place=!solid(next.down());
            if(!Double.isFinite(cut) || (place && (!bridgeCell(next.down()) || dy!=0)))continue;
            double cost=5+Math.abs(dy)*3+cut+(place?18:0);
            result.add(new Step(new Position(next.getX(),next.getY(),next.getZ()),cost,
                place?Movement.PLACE:cut>0?Movement.DIG:dy>0?Movement.ASCEND:dy<0?Movement.DESCEND:Movement.TRAVERSE));
        }
        for(int dx:new int[]{-1,1})for(int dz:new int[]{-1,1}){
            BlockPos next=base.add(dx,0,dz);
            if(clear(next) && clear(next.up()) && solid(next.down())
                    && clear(base.add(dx,0,0)) && clear(base.add(dx,1,0))
                    && clear(base.add(0,0,dz)) && clear(base.add(0,1,dz))
                    && solid(base.add(dx,-1,0)) && solid(base.add(0,-1,dz)))
                result.add(new Step(new Position(next.getX(),next.getY(),next.getZ()),5*Math.sqrt(2),Movement.DIAGONAL));
        }
        for(Direction d:Direction.Type.HORIZONTAL){
            if(!solid(base.offset(d).down()))for(int distance=2;distance<=4;distance++)for(int rise:new int[]{0,1,-1}){
                BlockPos next=base.offset(d,distance).up(rise);
                if(!solid(next.down()) || !clear(next) || !clear(next.up()) || !JumpMotion.possible(npc,base,next,boundary))continue;
                result.add(new Step(new Position(next.getX(),next.getY(),next.getZ()),distance*5+12+Math.abs(rise)*4,Movement.PARKOUR));
            }
            for(int down=2;down<=3;down++){
                BlockPos next=base.offset(d).down(down);
                if(!solid(next.down()) || !clear(next) || !clear(next.up()))continue;
                boolean column=true;for(int dy=-down;dy<=1;dy++)if(!clear(base.offset(d).up(dy)))column=false;
                if(column)result.add(new Step(new Position(next.getX(),next.getY(),next.getZ()),5+ActionCosts.FALL_N_BLOCKS_COST[down]+4,Movement.FALL));
            }
        }
        for(int dy:new int[]{1,-1}){
            BlockPos next=base.up(dy);
            if(climbable(base) && (climbable(next)||solid(next.down())) && (climbable(next) || aquatic(next)))
                result.add(new Step(new Position(next.getX(),next.getY(),next.getZ()),dy>0?ActionCosts.LADDER_UP_ONE_COST:ActionCosts.LADDER_DOWN_ONE_COST,Movement.CLIMB));
            if(water(base) && aquatic(next) && (npc.getAir()>150 || dy>0))
                result.add(new Step(new Position(next.getX(),next.getY(),next.getZ()),ActionCosts.WALK_ONE_IN_WATER_COST,Movement.SWIM));
        }
        if(allowChanges && editable(base) && clear(base) && clear(base.up()) && Double.isFinite(cutCost(base.up(2)))){
            BlockPos next=base.up();
            result.add(new Step(new Position(next.getX(),next.getY(),next.getZ()),70+cutCost(base.up(2)),Movement.PILLAR));
        }
        return result;
    }
    public boolean begin(BlockPos destination,int radius){return plan(destination,radius,Double.POSITIVE_INFINITY);}
    public boolean cheaperThanWalking(BlockPos destination,int walkNodes){return plan(destination,0,Math.max(0,walkNodes-1)*5.0);}
    private boolean plan(BlockPos destination,int radius,double walkingCost){
        if(active())return tick();
        if(npc.age<nextSearch || (!npc.isOnGround() && !npc.isTouchingWater() && !npc.isClimbing()))return false;
        nextSearch=npc.age+20;
        if(!world().getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)
                || !world().getGameRules().getBoolean(GameRules.DO_TILE_DROPS))allowChanges=false;
        BlockPos at=npc.getBlockPos();
        int remaining=NpcPathfinder.remaining(npc,world(),world().getTime());if(remaining<=0)return false;
        SearchResult result=new AStarPathFinder().search(new Position(at.getX(),at.getY(),at.getZ()),
            new GoalNear(destination.getX(),destination.getY(),destination.getZ(),radius),this::neighbors,new SearchLimits(Math.min(2048,remaining),32));
        NpcPathfinder.charge(npc,world(),world().getTime(),result.expandedNodes());
        if(!result.complete() || result.steps().isEmpty() || result.cost()>=walkingCost)return false;
        // Execute a bounded complete local segment; arrival is still physical.
        requiredSupports=(int)result.steps().stream().filter(edge->edge.movement()==Movement.PLACE || edge.movement()==Movement.PILLAR).count();
        for(Step edge:result.steps()){
            BlockPos next=new BlockPos(edge.destination().x(),edge.destination().y(),edge.destination().z());steps.add(next);actions.put(next,edge.movement());estimatedCosts.put(next,edge.cost());
        }
        started=npc.age;last=npc.getPos();lastMove=0;
        npc.navigationPlan().bind(this,destination);
        return tick();
    }
    private int supportCount(){
        int count=0;for(Item item:new Item[]{Items.DIRT,Items.COBBLESTONE,Items.COBBLED_DEEPSLATE}){
            count+=npc.backpack.count(item);if(npc.getMainHandStack().isOf(item))count+=npc.getMainHandStack().getCount();
        }return count;
    }
    private boolean obtainSupports(){
        status="Recolher terra para preparar os apoios do caminho";
        if(!npc.autoLoot)return false;
        unavailableDrops.entrySet().removeIf(e->e.getValue()<=npc.age);
        var drops=world().getEntitiesByClass(net.minecraft.entity.ItemEntity.class,npc.getBoundingBox().expand(6),
            drop->drop.isAlive() && drop.getStack().isOf(Items.DIRT) && !drop.cannotPickup()
                && !unavailableDrops.containsKey(drop.getUuid()) && drop.getCommandTags().contains("aiplayer_drop_"+npc.getUuid()) && dry(drop.getBlockPos()));
        if(!drops.isEmpty()){
            var drop=drops.stream().min(Comparator.comparingDouble(npc::squaredDistanceTo)).orElseThrow();
            double distance=npc.squaredDistanceTo(drop);
            if(!drop.getUuid().equals(supplyDrop) || distance<dropDistance-0.1){supplyDrop=drop.getUuid();dropDistance=distance;dropProgress=npc.age;}
            if(npc.age-dropProgress>160){unavailableDrops.put(drop.getUuid(),npc.age+200);supplyDrop=null;return false;}
            if(!npc.survival().canCollect(drop))WorkNavigation.moveToDrop(npc,drop,1);
            started=npc.age;return true;
        }
        BlockPos feet=npc.getBlockPos();
        for(BlockPos p:BlockPos.iterate(feet.add(-3,-1,-3),feet.add(3,1,3))){
            if(!loaded(p) || !editable(p) || p.equals(feet.down()) || occupied(p) || occupied(p.up())
                || steps.contains(p.up()) || !solid(p.down()) || !mining.reachable(p))continue;
            var state=world().getBlockState(p);
            if(!(state.isOf(Blocks.DIRT) || state.isOf(Blocks.GRASS_BLOCK)) || !Double.isFinite(cutCost(p)))continue;
            if(mining.tick(p.toImmutable())){npc.intelligence().restoration().removed(p);started=npc.age;}
            return true;
        }
        // Picking up the previous drop can leave the worker standing on the
        // last available soil block. Step onto real ground before mining it;
        // never remove the block that currently supports the worker.
        return WorkNavigation.leaveIsolatedPerch(npc);
    }
    private boolean occupied(BlockPos p){return !world().getEntitiesByClass(LivingEntity.class,new Box(p).contract(0.001),LivingEntity::isAlive).isEmpty();}
    public boolean tick(){
        if(lastTick==npc.age)return active();lastTick=npc.age;
        return npc.navigationPlan().execute(this,this::tickAction);
    }
    private boolean tickAction(){
        if(!active())return false;
        if(allowChanges && (!world().getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING) || !world().getGameRules().getBoolean(GameRules.DO_TILE_DROPS))){pause();return false;}
        if(npc.menuOpen() || npc.isEating() || npc.getTarget()!=null){mining.reset();return true;}
        if(npc.isSubmergedInWater() && npc.getAir()<100){pause();npc.getJumpControl().setActive();return false;}
        requiredSupports=(int)steps.stream().filter(p->(actions.get(p)==Movement.PLACE || actions.get(p)==Movement.PILLAR) && !solid(p.down())).count();
        BlockPos immediate=steps.peek();
        boolean needsSupport=(actions.get(immediate)==Movement.PLACE || actions.get(immediate)==Movement.PILLAR) && !solid(immediate.down());
        if(needsSupport && supportCount()==0){
            npc.navigationPlan().waitingForResource();if(obtainSupports())return true;pause();return false;
        }
        BlockPos next=steps.peek();
        Movement action=actions.get(next);
        if(npc.getPos().squaredDistanceTo(Vec3d.ofBottomCenter(next))<0.3 && Math.abs(npc.getY()-next.getY())<0.15
                && (npc.isOnGround() || action==Movement.SWIM || action==Movement.CLIMB)){
            steps.remove();actions.remove(next);estimatedCosts.remove(next);jump.reset();mining.reset();started=npc.age;last=npc.getPos();
            if(!active()){npc.navigationPlan().stopOrdinary();releaseMotion();}
            return active();
        }
        double stepDistance=npc.squaredDistanceTo(Vec3d.ofBottomCenter(next));
        if(!next.equals(progressStep)){progressStep=next;bestStepDistance=stepDistance;started=npc.age;}
        if(stepDistance<bestStepDistance-0.08){bestStepDistance=stepDistance;started=npc.age;}
        // PathExecutor retains the estimate made before breaking starts; the
        // remaining cost shrinks during mining and is not a valid timeout.
        int timeout=(int)Math.min(1200,Math.max(200,estimatedCosts.getOrDefault(next,0.0)+100));
        if(npc.age-started>timeout || npc.getBlockPos().getManhattanDistance(next)>5){npc.navigationPlan().failed(next);pause();return false;}
        int checked=0;BlockPos previous=npc.getBlockPos();
        for(BlockPos upcoming:steps){
            if(checked++>=3)break;
            Movement future=actions.get(upcoming);
            // Flow can wet a formerly dry walking edge after planning. The
            // same clear, adjacent body corridor is now a swimming edge, not
            // a blocked cell to blacklist while the current carries us away.
            if(future!=Movement.SWIM && future!=Movement.CLIMB
                    && Math.abs(upcoming.getY()-previous.getY())<=1
                    && Math.abs(upcoming.getX()-previous.getX())<=1 && Math.abs(upcoming.getZ()-previous.getZ())<=1
                    && aquatic(previous) && aquatic(upcoming)
                    && (water(upcoming)||water(upcoming.up()))){
                future=Movement.SWIM;actions.put(upcoming,future);
                estimatedCosts.put(upcoming,ActionCosts.WALK_ONE_IN_WATER_COST+4);
            }
            if(!loaded(upcoming) || hazard(upcoming) || hazard(upcoming.up())
                    || (future!=Movement.SWIM && future!=Movement.CLIMB && (!dry(upcoming)||!dry(upcoming.up())))){
                npc.navigationPlan().failed(upcoming);pause();return false;
            }
            previous=upcoming;
        }
        action=actions.get(next);
        BlockPos base=npc.getBlockPos();
        if(next.getY()-base.getY()>1 && action!=Movement.CLIMB && action!=Movement.SWIM){
            npc.navigationPlan().failed(next);pause();return false;
        }
        if(action==Movement.PARKOUR){
            status="Saltar com trajetória e pouso verificados";
            if(!jump.tick(next,boundary)){pause();return false;}return true;
        }
        if(action==Movement.SWIM){
            if(!aquatic(next)){pause();return false;}status="Nadar até uma posição segura";
            npc.getNavigation().stop();Vec3d delta=Vec3d.ofBottomCenter(next).subtract(npc.getPos());
            npc.getMoveControl().moveTo(next.getX()+0.5,next.getY(),next.getZ()+0.5,1);
            double vertical=MathHelper.clamp(delta.y*0.2,-0.12,0.12);
            if(npc.isTouchingWater() && solid(next.down()) && !water(next) && npc.getY()<next.getY()+0.1){
                npc.getJumpControl().setActive();vertical=0.3;
            }
            npc.setVelocity(MathHelper.clamp(delta.x*0.2,-0.15,0.15),vertical,MathHelper.clamp(delta.z*0.2,-0.15,0.15));return true;
        }
        if(action==Movement.CLIMB){
            if(!climbable(base) && !climbable(base.down())){pause();return false;}
            status="Subir ou descer pela escada existente";npc.getNavigation().stop();
            npc.setVelocity((next.getX()+0.5-npc.getX())*0.2,Math.signum(next.getY()-npc.getY())*0.15,(next.getZ()+0.5-npc.getZ())*0.2);return true;
        }
        if(action==Movement.FALL){
            if(!solid(next.down()) || !clear(next) || !clear(next.up())){pause();return false;}
            status="Descer com queda limitada e pouso livre";npc.getNavigation().stop();
            Vec3d delta=Vec3d.ofBottomCenter(next).subtract(npc.getPos());
            double horizontal=Math.sqrt(delta.x*delta.x+delta.z*delta.z);
            if(horizontal>0.05){
                double speed=Math.min(0.16,horizontal*0.4);
                npc.getMoveControl().moveTo(npc.getX(),npc.getY(),npc.getZ(),0);
                npc.setVelocity(delta.x/horizontal*speed,npc.getVelocity().y,delta.z/horizontal*speed);
            }
            return true;
        }
        if(action==Movement.PILLAR){
            BlockPos head=next.up();
            if(!clear(head)){
                if(!Double.isFinite(cutCost(head)) || occupied(head)){pause();return false;}
                mining.tick(head);return true;
            }
            status="Subir colocando apoio real sob os pés";
            if(!pillar.climb() && !npc.isOnGround())return true;
            return true;
        }
        ArrayList<BlockPos> cuts=new ArrayList<>();
        if(next.getY()>base.getY())cuts.add(base.up(2));
        if(next.getY()<base.getY())cuts.add(next.up(2));
        cuts.add(next.up());cuts.add(next);
        for(BlockPos cut:cuts){
            if(clear(cut))continue;
            if(!Double.isFinite(cutCost(cut)) || occupied(cut) || occupied(cut.up()) || cut.equals(base.down())){pause();return false;}
            npc.getNavigation().stop();status="Abrir passagem com a ferramenta adequada";
            if(mining.reachable(cut)){if(mining.tick(cut)){npc.intelligence().restoration().removed(cut);started=npc.age;}}
            return true;
        }
        BlockPos floor=next.down();
        if(!solid(floor)){
            ItemStack stack=support();
            if(!bridgeCell(floor) || stack.isEmpty() || occupied(floor) || npc.getEyePos().squaredDistanceTo(Vec3d.ofCenter(floor))>4.5*4.5){pause();return false;}
            boolean adjacent=false;for(Direction d:Direction.values())if(solid(floor.offset(d))){adjacent=true;break;}
            if(!adjacent){pause();return false;}
            npc.getNavigation().stop();status="Colocar apoio com um bloco da mochila";
            if(world().setBlockState(floor,((BlockItem)stack.getItem()).getBlock().getDefaultState(),3)){
                stack.decrement(1);requiredSupports=Math.max(0,requiredSupports-1);npc.backpack.markDirty();OwnedBlocks.get(world()).claim(floor,npc.getUuid());
                npc.swingHand(Hand.MAIN_HAND);started=npc.age;
            }
            return true;
        }
        if(npc.age-lastMove>=10 || npc.getNavigation().isIdle()){
            lastMove=npc.age;status="Percorrer a passagem preparada";
            var path=WorkNavigation.findPath(npc,next,0,NpcPathfinder.Mode.WORK,boundary);
            if(path!=null && path.reachesTarget())npc.getNavigation().startMovingAlong(path,1);
            else if(base.getManhattanDistance(next)<=2)npc.getMoveControl().moveTo(next.getX()+0.5,next.getY(),next.getZ()+0.5,1);
        }
        return true;
    }
}
