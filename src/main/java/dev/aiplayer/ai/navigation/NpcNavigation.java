/*
 * Server adaptation of Baritone (c) leijurv and contributors.
 * SPDX-License-Identifier: LGPL-3.0-or-later
 * Upstream: 8c55ad0721d42a01a359834edc16aaa9b5bc587d (1.10.5).
 * NPC changes: server entity movement/physics, bounded scanning, actual
 * inventory, task ownership and persistence. See embedded-baritone/UPSTREAM.md.
 */
package dev.aiplayer.ai.navigation;

import dev.aiplayer.ai.WorkNavigation;
import dev.aiplayer.entity.CompanionEntity;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.*;
import java.util.function.*;

/** Server counterpart of Baritone's PathingBehavior/PathExecutor ownership:
 * one physical path executor, explicit outcomes, live replanning and an
 * off-path/progress watchdog. Client key presses are replaced by NPC controls.
 * Upstream reference: 8c55ad0721d42a01a359834edc16aaa9b5bc587d (LGPL-3+). */
public final class NpcNavigation {
    public enum Outcome { IDLE, RUNNING, WAITING_RESOURCE, WAITING_WORLD, BLOCKED, SUCCEEDED, CANCELLED }
    private final CompanionEntity npc;
    private PreparedRoute executor;
    private PreparedRoute fallback;
    private Predicate<BlockPos> boundary=p->true;
    private boolean executing;
    private BlockPos destination,lastCell;
    private int nextSample,progressAt,replans;
    private double best=Double.POSITIVE_INFINITY;
    private Outcome outcome=Outcome.IDLE;
    private String intent="";
    private final LinkedHashMap<BlockPos,Integer> visits=new LinkedHashMap<>();
    private final Map<BlockPos,Integer> blocked=new HashMap<>();
    public NpcNavigation(CompanionEntity npc){this.npc=npc;}
    public boolean executing(){return executing;}
    public int replans(){return replans;}
    public void resetWatchdog(){visits.clear();lastCell=null;progressAt=npc.age;best=Double.POSITIVE_INFINITY;replans=0;}
    public boolean prepared(){return executor!=null && executor.active();}
    public Outcome outcome(){return outcome;}
    public BlockPos destination(){return destination;}
    public String diagnostics(){return outcome+" goal="+destination+" replans="+replans+" noProgress="+(npc.age-progressAt);}
    public void failed(BlockPos p){blocked.put(p.toImmutable(),npc.age+200);replans++;}
    public void stopOrdinary(){boolean prior=executing;executing=true;try{npc.getNavigation().stop();}finally{executing=prior;}}
    public boolean avoid(BlockPos p){return blocked.getOrDefault(p,0)>npc.age;}
    public void bind(PreparedRoute route,BlockPos goal){
        if(executor!=null && executor!=route)executor.pause();
        executor=route;intent=currentIntent();setGoal(goal);outcome=Outcome.RUNNING;
    }
    private String currentIntent(){return npc.mode()+":"+(npc.tasks()==null?"":npc.tasks().kind())
        +":"+(npc.gathering()==null?"":npc.gathering().kind());}
    public boolean execute(PreparedRoute route,BooleanSupplier action){
        boolean before=executing;executing=true;
        try{return action.getAsBoolean();}finally{
            executing=before;
            if(executor==route && !route.active())executor=null;
        }
    }
    private void setGoal(BlockPos goal){
        if(!Objects.equals(destination,goal)){
            destination=goal.toImmutable();best=npc.squaredDistanceTo(Vec3d.ofBottomCenter(goal));
            progressAt=npc.age;replans=0;
        }
    }
    /** All ordinary paths pass through CompanionNavigation as well. */
    public void ordinaryPath(Path path){
        if(executing)return;
        if(executor!=null){executor.pause();executor=null;}
        if(path!=null){setGoal(path.getTarget());outcome=Outcome.RUNNING;}
    }
    public void stopped(){
        if(executing)return;
        // Idle WAIT stops the ordinary path every tick. It must not erase
        // an explicitly running prepared work action. A changed task/mode,
        // a menu or combat still relinquishes that work immediately.
        if(prepared() && intent.equals(currentIntent()) && !npc.menuOpen() && !npc.isEating() && npc.getTarget()==null)return;
        cancel();
    }
    public void cancel(){
        if(executor!=null){executor.pause();executor=null;}
        outcome=Outcome.CANCELLED;
    }
    public boolean request(BlockPos goal,Predicate<BlockPos> permitted,boolean mayModify){
        boundary=permitted;
        if(fallback==null)fallback=new PreparedRoute(npc,p->boundary.test(p));
        if(!Objects.equals(destination,goal)){fallback.pause();setGoal(goal);}
        fallback.allowChanges(mayModify);
        if(!npc.getWorld().isChunkLoaded(goal))outcome=Outcome.WAITING_WORLD;
        if(fallback.active())return fallback.tick();
        boolean begun=fallback.begin(goal,0);
        if(!begun && !fallback.active())outcome=Outcome.BLOCKED;
        return begun;
    }
    public void waitingForResource(){outcome=Outcome.WAITING_RESOURCE;}
    public void tick(){
        if(npc.getWorld().isClient || npc.menuOpen() || npc.isEating() || npc.getTarget()!=null)return;
        if(prepared()){executor.tick();return;}
        if(npc.age<nextSample)return;nextSample=npc.age+20;
        blocked.entrySet().removeIf(e->e.getValue()<=npc.age);
        var path=npc.getNavigation().getCurrentPath();
        if(path==null || npc.getNavigation().isIdle() || destination==null)return;
        double distance=npc.squaredDistanceTo(Vec3d.ofBottomCenter(destination));
        if(distance<0.65){outcome=Outcome.SUCCEEDED;progressAt=npc.age;return;}
        BlockPos cell=npc.getBlockPos();
        if(!cell.equals(lastCell)){
            visits.merge(cell.toImmutable(),1,Integer::sum);lastCell=cell.toImmutable();
            while(visits.size()>128)visits.remove(visits.keySet().iterator().next());
        }
        if(distance<best-0.4){best=distance;progressAt=npc.age;}
        boolean cycle=visits.getOrDefault(cell,0)>=4 && npc.age-progressAt>80;
        if(!cycle && npc.age-progressAt<160)return;
        // Preserve the actual objective, not a random sidestep which can send
        // the worker into a cave. Failed local cells are temporarily penalized.
        blocked.put(cell.toImmutable(),npc.age+400);visits.clear();progressAt=npc.age;replans++;
        BlockPos goal=destination;boolean prior=executing;executing=true;
        try{npc.getNavigation().stop();}finally{executing=prior;}
        NpcPathfinder.invalidate(npc);
        Path retry=WorkNavigation.findPath(npc,goal,0,NpcPathfinder.Mode.WORK,p->!avoid(p) || p.equals(cell));
        if(retry!=null && retry.reachesTarget()){
            npc.getNavigation().startMovingAlong(retry,1);return;
        }
        request(goal,p->true,true);
    }
    public void write(NbtCompound root){
        NbtCompound n=new NbtCompound();n.putInt("Version",1);
        if(destination!=null)n.putLong("Goal",destination.asLong());
        n.putString("Dimension",npc.getWorld().getRegistryKey().getValue().toString());root.put("Navigation",n);
    }
    public void read(NbtCompound root){
        if(executor!=null)executor.pause();executor=null;fallback=null;destination=null;
        visits.clear();blocked.clear();lastCell=null;nextSample=0;progressAt=npc.age;outcome=Outcome.IDLE;
        // The owning persisted task reissues the goal; a saved path is never
        // executed against a potentially changed world after reload.
    }
}
