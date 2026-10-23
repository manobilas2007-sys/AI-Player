package dev.aiplayer.ai.brain;

import dev.aiplayer.entity.CompanionEntity;
import dev.aiplayer.ai.WorkNavigation;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.util.math.*;

/** Measures progress towards a destination, then repaths, detours and records failure. */
public final class NavigationMonitor {
    private final CompanionEntity npc;
    private BlockPos target;
    private Vec3d last=Vec3d.ZERO;
    private double bestDistance=Double.MAX_VALUE;
    private int stagnant, nextSample, attempts;
    public NavigationMonitor(CompanionEntity npc){this.npc=npc;}
    public int attempts(){return attempts;}
    public void reset(){target=null;stagnant=0;attempts=0;bestDistance=Double.MAX_VALUE;}
    public void tick() {
        if(npc.age<nextSample)return;
        nextSample=npc.age+20;
        var nav=npc.getNavigation();
        var path=nav.getCurrentPath();
        if(nav.isIdle() || path==null || path.getEnd()==null){stagnant=0;return;}
        var end=path.getEnd();BlockPos dest=new BlockPos(end.x,end.y,end.z);
        double distance=npc.getPos().squaredDistanceTo(Vec3d.ofBottomCenter(dest));
        if(target==null || target.getSquaredDistance(dest)>9) {
            target=dest;last=npc.getPos();bestDistance=distance;stagnant=0;attempts=0;return;
        }
        if(distance<2){stagnant=0;return;}
        if(distance<bestDistance-0.4){bestDistance=distance;stagnant=0;attempts=0;}
        else stagnant+=20;
        if(npc.getPos().squaredDistanceTo(last)<0.08 && npc.isOnGround() && npc.horizontalCollision)
            npc.getJumpControl().setActive();
        last=npc.getPos();
        if(stagnant<60)return;
        stagnant=0;attempts++;
        nav.stop();
        if(attempts==1) {
            var retry=nav.findPathTo(dest,0);
            if(WorkNavigation.safePath(npc,retry,Math.min(dest.getY(),npc.getBlockY())-2))
                nav.startMovingAlong(retry,1.15);
        } else if(attempts==2) {
            Vec3d side=NoPenaltyTargeting.find(npc,4,2);
            if(side!=null) {
                var detour=nav.findPathTo(BlockPos.ofFloored(side),0);
                if(WorkNavigation.safePath(npc,detour,npc.getBlockY()-2))nav.startMovingAlong(detour,1.1);
            }
        } else {
            npc.intelligence().memory().failed(dest,npc.getWorld().getTime());
            if(npc.intelligence().activity()==DecisionPolicy.Activity.GATHER)npc.gathering().navigationFailed(dest);
            npc.intelligence().navigationFailed();
            attempts=0;target=null;
        }
    }
}
