package dev.aiplayer.ai.brain;

import dev.aiplayer.ai.*;
import dev.aiplayer.entity.CompanionEntity;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.*;
import java.util.*;
import static dev.aiplayer.ai.brain.DecisionPolicy.Activity.*;

/**
 * Single server decision owner. Sensors -> inventory plan -> arbitration -> executor -> verification.
 * Work progress is owned by resumable executors, not by animation or navigation state.
 */
public final class CompanionIntelligence {
    private final CompanionEntity npc;
    private final BrainMemory memory=new BrainMemory();
    private final NavigationMonitor navigation;
    private final TerrainRecovery recovery;
    private DecisionPolicy.Activity activity=IDLE;
    private ResourcePlan plan=new ResourcePlan(0,0,false,4,false);
    private NbtCompound suspendedJob,materialJob;
    private int nextScan,nextPlan,nextMove,nextStroll,attackCooldown,routeFailures;
    private int threatCount;
    private int followStalledAt, nextRecall;
    private Vec3d followPosition;
    private int lastTick=-1, combatProgressAt;
    private UUID combatTarget;
    private double combatDistance;
    private final Map<UUID,Integer> unreachableEnemies=new HashMap<>();
    private boolean following, craftAvailable;
    private long lastHurt, nextEmergencyFoodAt,nextMaterialAt;
    public CompanionIntelligence(CompanionEntity npc){this.npc=npc;navigation=new NavigationMonitor(npc);recovery=new TerrainRecovery(npc);}
    public BrainMemory memory(){return memory;}
    public DecisionPolicy.Activity activity(){return activity;}
    public ResourcePlan plan(){return plan;}
    public String status(){return (npc.tasks().active()?npc.tasks().status()+" | ":"")+activity+": "+memory.reason()+" | "+memory.objective()+" / "+memory.step()+" | faltam: "+plan.missingLogs()+" troncos, "+plan.missingFood()+" alimentos, "+ResourcePlan.stoneNeeded(npc)+" pedregulhos para melhorias";}
    public void navigationFailed(){routeFailures++;npc.speech().notice(CompanionSpeech.Event.ROUTE);}
    public void clearSuspended(){suspendedJob=null;materialJob=null;nextMaterialAt=0;}
    private boolean enemy(LivingEntity e){
        return e!=null && e.isAlive() && unreachableEnemies.getOrDefault(e.getUuid(),0)<=npc.age && npc.canTarget(e) && npc.squaredDistanceTo(e)<32*32;
    }
    private void scan(PlayerEntity owner) {
        if(npc.age<nextScan)return;
        nextScan=npc.age+5;
        if(!enemy(npc.getTarget()))npc.setTarget(null);
        unreachableEnemies.entrySet().removeIf(e->e.getValue()<=npc.age);
        LivingEntity attacker=npc.getAttacker();
        if(attacker!=null && npc.age-npc.getLastAttackedTime()<20)unreachableEnemies.remove(attacker.getUuid());
        if(enemy(attacker) && npc.age-npc.getLastAttackedTime()<200)npc.setTarget(attacker);
        else if(owner!=null && enemy(owner.getAttacker()) && owner.age-owner.getLastAttackedTime()<200)
            npc.setTarget(owner.getAttacker());
        else if(owner!=null && enemy(owner.getAttacking()) && owner.age-owner.getLastAttackTime()<200)
            npc.setTarget(owner.getAttacking());
        else if(enemy(npc.getOwnerMarkedTarget()))npc.setTarget(npc.getOwnerMarkedTarget());
        var threats=npc.getWorld().getEntitiesByClass(MobEntity.class,npc.getBoundingBox().expand(12),
            e->enemy(e) && (CompanionGoal.isNaturallyHostile(e) || e.getTarget()==npc)
                && npc.canSee(e));
        threatCount=(int)threats.stream().filter(e->npc.squaredDistanceTo(e)<64).count();
        if(npc.getTarget()==null && npc.mode()==Mode.GUARD)
            threats.stream().min(Comparator.comparingDouble(npc::squaredDistanceTo)).ifPresent(npc::setTarget);
    }
    private void add(List<DecisionPolicy.Candidate> list,DecisionPolicy.Activity a,int priority,String reason){
        list.add(new DecisionPolicy.Candidate(a,priority,reason));
    }
    public void tick() {
        if(npc.getWorld().isClient || !npc.isAlive() || npc.ownerId()==null || lastTick==npc.age)return;
        lastTick=npc.age;
        long now=npc.getWorld().getTime();
        memory.dimension(npc.getWorld().getRegistryKey().getValue().toString());
        if(npc.age%100==0)memory.expire(now);
        PlayerEntity owner=npc.owner();
        scan(owner);
        npc.survival().tick(); // physical pickup + already-started meal; never starts movement/crafting.
        if(attackCooldown>0)attackCooldown--;
        if(npc.age>=nextPlan){plan=ResourcePlan.inspect(npc);craftAvailable=npc.autoCraft && npc.crafting().canBegin();nextPlan=npc.age+10;}
        LivingEntity target=npc.getTarget();
        boolean danger=enemy(target);
        double distance=owner==null?0:npc.squaredDistanceTo(owner);
        if(distance>225)following=true;
        else if(distance<64)following=false;
        if(npc.getAttacker()!=null && npc.age-npc.getLastAttackedTime()<15)lastHurt=now;
        boolean overwhelmed=danger && (npc.getHealth()<=10 || (threatCount>=3 && npc.getHealth()<24));
        if(overwhelmed)npc.setRetreating(true);
        if(!danger && npc.getHealth()>=npc.getMaxHealth()*0.8f)npc.setRetreating(false);
        if(npc.isEating() && danger && npc.squaredDistanceTo(target)<64)npc.cancelEating();

        if(suspendedJob!=null && !npc.gathering().active() && !npc.gathering().hasCleanup()) {
            npc.gathering().restoreTask(suspendedJob);suspendedJob=null;nextEmergencyFoodAt=now+200;
        }
        // Resume when the real pick exists; optional crafting is not a dependency.
        if(materialJob!=null && (!npc.gathering().active() || (SurfaceStoneGathering.hasPick(npc)
                && npc.gathering().kind()==ResourceGathering.Kind.WOOD)) && !npc.gathering().hasCleanup()){
            npc.gathering().restoreTask(materialJob);materialJob=null;nextPlan=0;
            craftAvailable=npc.autoCraft && npc.crafting().canBegin();
        }
        // Critical food becomes a real subgoal; restore the original quantity/progress afterwards.
        if(now>=nextEmergencyFoodAt && npc.autoSurvive && npc.getHealth()<12 && plan.missingFood()==3
                && (npc.gathering().kind()==ResourceGathering.Kind.WOOD || npc.gathering().kind()==ResourceGathering.Kind.STONE)
                && !npc.gathering().hasCleanup() && suspendedJob==null && !danger) {
            suspendedJob=npc.gathering().taskSnapshot();
            npc.gathering().start(ResourceGathering.Kind.FOOD,3,false);
        }
        boolean wait=npc.mode()==Mode.WAIT;
        // The wood pillar and staircase executors already have their own safe
        // escape plans. Generic terrain recovery must not build a competing
        // pillar in the middle of either plan.
        if(!danger && (!wait || npc.gathering().isManual()) && !npc.menuOpen() && !npc.isEating()
                && !npc.gathering().onConstructedSupport() && !npc.gathering().miningStone()
                && recovery.tick()){
            npc.gathering().pause();npc.crafting().pause();
            memory.plan("Sair de terreno bloqueado","Construir apoio ou abrir degrau seguro");
            npc.speech().observe(CompanionSpeech.Event.RECOVERY);
            navigation.reset();return;
        }
        if(danger || wait || npc.menuOpen())recovery.pause();
        // An owned bench awaiting retrieval must not starve the last cobblestone
        // needed to finish a tool set. Mining is a real prerequisite for crafting.
        boolean stoneShortage=npc.autoCraft && ResourcePlan.stoneNeeded(npc)>0
            && SurfaceStoneGathering.hasPick(npc) && ResourcePlan.stoneCapacity(npc);
        // Bench retrieval is cleanup, not a prerequisite of an active stone
        // order. A manual order in WAIT must resume immediately after its
        // wooden pick is crafted, even if its own bench still needs recovery.
        boolean stoneOrderReady=npc.gathering().active()
            && npc.gathering().kind()==ResourceGathering.Kind.STONE
            && SurfaceStoneGathering.hasPick(npc);
        boolean automaticGatherAllowed=!danger && !npc.menuOpen() && !wait && !npc.tasks().active()
            && owner!=null && !owner.isSpectator() && !following
            && !npc.gathering().active() && !npc.gathering().hasCleanup()
            && npc.gathering().canStartAutomatic();
        boolean canStartStoneExpedition=automaticGatherAllowed && stoneShortage
            && npc.getHealth()>=12 && SurfaceStoneGathering.caveAccess(npc,npc.getBlockPos());
        // When a reclaimed bench is immediately in the path of an automatic
        // stone expedition, remove that real obstruction first. The bounded
        // physical reclamation then releases the route to the stone wall.
        // For a manual stone order, never let cleanup override the order.
        boolean benchBlocksStoneRoute=(stoneOrderReady || canStartStoneExpedition)
            && !npc.gathering().isManual()
            && npc.crafting().nearbyBenchObstruction();
        // A desired future stone upgrade is not an active expedition. Yielding
        // merely because stone is missing can strand a stale bench recovery and
        // suppress axe renewal during WOOD or after it finishes outdoors.
        boolean benchMayYield=npc.crafting().isReclaiming()
            && (stoneOrderReady || canStartStoneExpedition) && !benchBlocksStoneRoute;
        if(automaticGatherAllowed && (!npc.crafting().isWorking() || benchMayYield)) {
            // A stone pick and two cobblestones already form a viable upgrade plan.
            // The four-log reserve is desirable, but NOT a prerequisite for
            // collecting the final cobblestone needed by the remaining tool.
            if(canStartStoneExpedition)
                npc.gathering().start(ResourceGathering.Kind.STONE,ResourcePlan.stoneNeeded(npc),false);
            else if(npc.autoCraft && plan.missingLogs()>0 && (plan.toolsNeeded() || plan.missingFood()==0))
                npc.gathering().start(ResourceGathering.Kind.WOOD,Math.min(32,plan.missingLogs()),false);
            else if(npc.autoSurvive && plan.missingFood()>0)
                npc.gathering().start(ResourceGathering.Kind.FOOD,plan.missingFood(),false);
            else if(npc.autoCraft && plan.missingLogs()>0)
                npc.gathering().start(ResourceGathering.Kind.WOOD,plan.missingLogs(),false);
        }
        if(!danger && !npc.menuOpen() && npc.autoCraft && npc.autoEquip
                && npc.gathering().kind()==ResourceGathering.Kind.STONE && !SurfaceStoneGathering.hasPick(npc)
                && !npc.crafting().isWorking() && !npc.crafting().canBegin()
                && materialJob==null && now>=nextMaterialAt && npc.gathering().canStartAutomatic()){
            NbtCompound job=npc.gathering().taskSnapshot();boolean manual=npc.gathering().isManual();
            npc.gathering().start(ResourceGathering.Kind.WOOD,Math.max(4,plan.missingLogs()),manual);
            materialJob=job;nextMaterialAt=now+1200;nextPlan=0;
        }
        List<DecisionPolicy.Candidate> candidates=new ArrayList<>();
        add(candidates,IDLE,0,"Observando os arredores");
        if(npc.menuOpen())add(candidates,WAIT,200,"Inventário aberto");
        if(owner!=null && owner.isSpectator())add(candidates,WAIT,110,"Dono em espectador");
        if(npc.isInLava() || (npc.isSubmergedInWater() && npc.getAir()<100) || (npc.isOnFire() && !danger))add(candidates,ESCAPE,130,"Perigo ambiental");
        if(danger)add(candidates,overwhelmed || npc.isRetreating()?RETREAT:COMBAT,100,
            overwhelmed?"Desvantagem: recuar":"Proteger e combater");
        boolean safeMeal=!danger || npc.squaredDistanceTo(target)>=100;
        if(npc.isEating() || (npc.autoSurvive && safeMeal && npc.getHealth()<=npc.getMaxHealth()*0.8f
                && npc.survival().hasFood()))
            add(candidates,EAT,npc.isRetreating() && safeMeal?115:90,"Recuperar vida com alimento real");
        if(npc.gathering().cleanupReady())add(candidates,CLEANUP,75,"Recuperar apoios colocados");
        // Finish one real breaking action before starting an optional craft session.
        // A committed block break must finish; an excavation PLAN may pause
        // between blocks for a real craft when enough stone is already held.
        boolean stoneUpgradeReady=npc.autoCraft && npc.gathering().active()
            && !npc.gathering().isManual() && npc.gathering().kind()==ResourceGathering.Kind.STONE
            && npc.crafting().canCraftStoneUpgradeNow()
            && !npc.gathering().breakingBlock();
        boolean manualStoneNeedsPick=npc.autoCraft && npc.gathering().isManual()
            && npc.gathering().kind()==ResourceGathering.Kind.STONE
            && !SurfaceStoneGathering.hasPick(npc) && npc.crafting().canBegin();
        // Do not interrupt the search for the LAST physical cobblestone to
        // craft optional wooden equipment or to recover the bench. A stone
        // recipe may interrupt the excavation once its materials really exist.
        // Axe replacement is a durable equipment need, not merely a WOOD
        // task subgoal: FallingTree can consume the final durability at the
        // very end of a job, when gathering().kind() is already NONE.
        boolean axeMaintenance=npc.crafting().needsAxeReplacement();
        boolean woodNeedsAxe=axeMaintenance && npc.gathering().kind()==ResourceGathering.Kind.WOOD
            && npc.crafting().canBegin();
        boolean craft=(!benchMayYield || stoneUpgradeReady)
            && (!stoneOrderReady || stoneUpgradeReady || manualStoneNeedsPick || benchBlocksStoneRoute)
            && (npc.crafting().isWorking() || stoneUpgradeReady
            || manualStoneNeedsPick || woodNeedsAxe || (axeMaintenance && npc.crafting().canBegin())
            || (npc.autoCraft && !npc.gathering().miningStone()
            && (craftAvailable || (npc.gathering().kind()==ResourceGathering.Kind.STONE && npc.crafting().canBegin()))
            && (!npc.gathering().active() || (plan.toolsNeeded() && plan.missingLogs()==0)
                || (npc.gathering().kind()==ResourceGathering.Kind.STONE && !npc.gathering().miningStone()))));
        // FOLLOW (70) and idle WAIT (80) used to starve crafting forever,
        // including AFTER an axe had broken. Combat, escape, eating and open
        // inventory still outrank maintenance. Pillar cleanup remains ahead
        // of optional repair so NPCs do not place benches while descending.
        int craftPriority=axeMaintenance && !npc.gathering().cleanupReady()
            ? (wait && !npc.gathering().isManual()?85:78) : 65;
        if(craft)add(candidates,CRAFT,craftPriority,"Preparar materiais, fabricar e verificar equipamento");
        if(wait && !npc.tasks().active() && !npc.gathering().isManual() && !npc.gathering().cleanupReady())add(candidates,WAIT,80,"Aguardando ordem");
        if(owner!=null && following && !npc.tasks().active() && npc.gathering().canYieldToOwner())
            add(candidates,FOLLOW,70,"Reaproximar do dono sem perder a tarefa");
        if(npc.gathering().active())add(candidates,GATHER,npc.gathering().isManual()?60:50,
            "Coletar "+npc.gathering().kind()+" "+npc.gathering().progress()+"/"+npc.gathering().goal());
        if(npc.tasks().active())add(candidates,PROJECT,61,npc.tasks().status());
        boolean noCapacity=npc.gathering().kind()==ResourceGathering.Kind.FOOD?!ResourcePlan.foodCapacity(npc):npc.gathering().kind()==ResourceGathering.Kind.STONE?!ResourcePlan.stoneCapacity(npc):plan.full();
        if(noCapacity && npc.gathering().active() && !npc.gathering().hasCleanup())
            add(candidates,INVENTORY_FULL,62,"Mochila sem espaço; preservar blocos e recursos");
        var decision=DecisionPolicy.choose(candidates,activity);
        if(decision.activity()!=activity) {
            memory.transition(activity,decision.activity(),decision.reason(),npc.gathering().active() || npc.crafting().hasSession());
            if(activity==GATHER || activity==CLEANUP)npc.gathering().pause();
            if(activity==CRAFT)npc.crafting().pause();
            if(activity==PROJECT)npc.tasks().pause();
            npc.getNavigation().stop();npc.clearActionVisual();navigation.reset();nextMove=0;
            activity=decision.activity();
        } else memory.transition(activity,activity,decision.reason(),false);
        if(npc.autoEquip && !npc.isEating() && npc.age%20==0
                && activity!=GATHER && activity!=CLEANUP && activity!=CRAFT && activity!=PROJECT)npc.optimizeEquipment();
        if(activity!=GATHER && activity!=CLEANUP && activity!=PROJECT && (npc.gathering().active() || npc.gathering().hasCleanup()))npc.gathering().pause();
        if(activity!=PROJECT && npc.tasks().active())npc.tasks().pause();
        switch(activity) {
            case WAIT -> npc.getNavigation().stop();
            case ESCAPE -> escape();
            case RETREAT -> retreat(target,owner);
            case COMBAT -> combat(target);
            case EAT -> {
                npc.getNavigation().stop();
                if(!npc.isEating())npc.survival().requestMeal();
            }
            case CRAFT -> {
                memory.plan("Preparar equipamento",npc.crafting().description());
                npc.crafting().tick();
                if(!npc.crafting().isWorking())craftAvailable=false;
            }
            case CLEANUP -> {memory.plan("Recuperar materiais","Desmontar apoios");if(!npc.gathering().cleanupTick())idle(owner);}
            case GATHER -> {
                memory.plan(npc.gathering().isManual()?"Ordem do dono":"Sobrevivência autônoma",
                    "Obter "+npc.gathering().kind()+" "+npc.gathering().progress()+"/"+npc.gathering().goal());
                npc.gathering().resume();
                if(!npc.gathering().tick())idle(owner);
            }
            case PROJECT -> {memory.plan("Ordem de projeto",npc.tasks().status());npc.tasks().tick();}
            case FOLLOW -> follow(owner);
            case INVENTORY_FULL -> {
                npc.gathering().pause();
                if(owner!=null && distance>16)follow(owner);else npc.getNavigation().stop();
            }
            case IDLE -> {
                if(owner!=null && !wait && !npc.gathering().collectLooseDrops())idle(owner);
            }
        }
        npc.speech().observe(switch(activity){
            case INVENTORY_FULL -> CompanionSpeech.Event.FULL;
            case RETREAT -> CompanionSpeech.Event.RETREAT;
            case COMBAT -> CompanionSpeech.Event.COMBAT;
            case EAT -> npc.isEating()?CompanionSpeech.Event.EAT:null;
            case CRAFT -> CompanionSpeech.Event.CRAFT;
            case GATHER -> !npc.gathering().isManual() ? (npc.gathering().kind()==ResourceGathering.Kind.WOOD
                ? CompanionSpeech.Event.MATERIALS:npc.gathering().kind()==ResourceGathering.Kind.STONE?CompanionSpeech.Event.STONE:CompanionSpeech.Event.FOOD):null;
            default -> null;
        });
        navigation.tick();
    }
    private void combat(LivingEntity target) {
        if(!enemy(target))return;
        double distance=npc.squaredDistanceTo(target);
        if(!target.getUuid().equals(combatTarget)){
            combatTarget=target.getUuid();combatProgressAt=npc.age;combatDistance=distance;
        }
        if(distance<combatDistance-0.5){combatDistance=distance;combatProgressAt=npc.age;}
        if(npc.age-combatProgressAt>160 && npc.age-npc.getLastAttackedTime()>40){
            unreachableEnemies.put(target.getUuid(),npc.age+200);memory.failed(target.getBlockPos(),npc.getWorld().getTime());
            npc.setTarget(null);npc.getNavigation().stop();combatTarget=null;return;
        }
        npc.clearWorkingVisual();
        if(npc.autoEquip)npc.optimizeEquipment();
        npc.getLookControl().lookAt(target,30,30);
        if(npc.squaredDistanceTo(target)<6.25 && npc.canSee(target)){
            npc.getNavigation().stop();
            if(attackCooldown==0){if(npc.tryAttack(target))combatProgressAt=npc.age;attackCooldown=20;}
        } else moveTo(target.getBlockPos(),1.18);
    }
    private void retreat(LivingEntity target,PlayerEntity owner) {
        if(target==null){if(owner!=null)follow(owner);return;}
        if(npc.age<nextMove)return;nextMove=npc.age+15;
        Vec3d away=NoPenaltyTargeting.findFrom(npc,12,4,target.getPos());
        if(away!=null)startSafe(BlockPos.ofFloored(away),1.35);
    }
    private void escape(){
        if(npc.isSubmergedInWater())npc.getJumpControl().setActive();
        if(npc.age<nextMove)return;nextMove=npc.age+10;
        Vec3d escape=NoPenaltyTargeting.find(npc,8,4);
        if(escape!=null)startSafe(BlockPos.ofFloored(escape),1.3);
        if(npc.isTouchingWater() || npc.isInLava())npc.getJumpControl().setActive();
    }
    private boolean startSafe(BlockPos pos,double speed) {
        if(memory.blocked(pos,npc.getWorld().getTime()))return false;
        var path=WorkNavigation.findPath(npc,pos,0,dev.aiplayer.ai.navigation.NpcPathfinder.Mode.SAFE);
        if(!WorkNavigation.surfacePath(npc,path,pos.getY())){
            memory.failed(pos,npc.getWorld().getTime());routeFailures++;return false;
        }
        return npc.getNavigation().startMovingAlong(path,speed);
    }
    private void moveTo(BlockPos pos,double speed){
        if(npc.age<nextMove)return;nextMove=npc.age+15;startSafe(pos,speed);
    }
    private void follow(PlayerEntity owner) {
        if(owner==null)return;
        double distance=npc.squaredDistanceTo(owner);
        if(distance<36){following=false;routeFailures=0;return;}
        if(npc.age<nextMove)return;nextMove=npc.age+15;
        if(followPosition==null || npc.getPos().squaredDistanceTo(followPosition)>1){
            followPosition=npc.getPos();followStalledAt=npc.age;routeFailures=0;
        }
        if(npc.age>=nextRecall && npc.age-followStalledAt>240 && (distance>64*64 || routeFailures>=4)
                && npc.getWorld().getTime()-lastHurt>60
                && !npc.gathering().hasCleanup() && CompanionGoal.recall(npc,owner)){
            routeFailures=0;following=false;nextRecall=npc.age+200;followPosition=null;return;
        }
        if(startSafe(owner.getBlockPos(),1.2))return;
        WorkNavigation.moveNear(npc,owner.getBlockPos(),owner.getBlockY(),1.2);
    }
    private void idle(PlayerEntity owner) {
        if(owner==null || npc.mode()==Mode.WAIT)return;
        if(npc.squaredDistanceTo(owner)>225){follow(owner);return;}
        if(!npc.getNavigation().isIdle() || npc.age<nextStroll)return;
        nextStroll=npc.age+40+npc.getRandom().nextInt(40);
        Vec3d pos=NoPenaltyTargeting.find(npc,6,2);
        if(pos!=null && pos.squaredDistanceTo(owner.getPos())<225)startSafe(BlockPos.ofFloored(pos),0.95);
    }
    public void write(NbtCompound root){
        memory.write(root);recovery.write(root);root.putLong("NextEmergencyFood",nextEmergencyFoodAt);
        root.putLong("NextMaterialAttempt",nextMaterialAt);
        if(materialJob!=null)root.put("MaterialGatherJob",materialJob.copy());
        if(suspendedJob!=null)root.put("SuspendedGatherJob",suspendedJob.copy());
    }
    public void read(NbtCompound root){
        recovery.read(!root.contains("WorkDimension") || root.getString("WorkDimension").equals(npc.getWorld().getRegistryKey().getValue().toString())?root:new NbtCompound());
        memory.read(root);nextEmergencyFoodAt=root.getLong("NextEmergencyFood");activity=IDLE;lastTick=-1;nextPlan=0;navigation.reset();
        nextMaterialAt=root.getLong("NextMaterialAttempt");
        materialJob=root.contains("MaterialGatherJob") && (!root.contains("WorkDimension")
            || root.getString("WorkDimension").equals(npc.getWorld().getRegistryKey().getValue().toString()))?root.getCompound("MaterialGatherJob").copy():null;
        suspendedJob=root.contains("SuspendedGatherJob") && (!root.contains("WorkDimension")
            || root.getString("WorkDimension").equals(npc.getWorld().getRegistryKey().getValue().toString()))
            ?root.getCompound("SuspendedGatherJob").copy():null;
    }
}
