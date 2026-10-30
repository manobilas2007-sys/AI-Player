/*
 * Server adaptation of Baritone (c) leijurv and contributors.
 * SPDX-License-Identifier: LGPL-3.0-or-later
 * Upstream: 8c55ad0721d42a01a359834edc16aaa9b5bc587d (1.10.5).
 * NPC changes: server entity movement/physics, bounded scanning, actual
 * inventory, task ownership and persistence. See embedded-baritone/UPSTREAM.md.
 */
package dev.aiplayer.ai.navigation;

import dev.aiplayer.entity.CompanionEntity;
import net.minecraft.util.math.*;
import java.util.function.Predicate;

/** MovementParkour's launch/flight/landing stages adapted to server entities.
 * Validate the whole ballistic body arc, including landing overshoot; changes
 * to the world invalidate the next step. Motion uses vanilla gravity. */
public final class JumpMotion {
    private final CompanionEntity npc;
    private BlockPos goal;
    private int launched,flightTicks;
    public JumpMotion(CompanionEntity npc){this.npc=npc;}
    private static int duration(int rise){
        double y=0,vy=0.42;
        for(int t=1;t<=30;t++){
            y+=vy;vy=(vy-0.08)*0.98;
            if(vy<0 && y<=rise)return t;
        }
        return -1;
    }
    public static boolean possible(CompanionEntity npc,BlockPos from,BlockPos to,Predicate<BlockPos> permitted){
        int rise=to.getY()-from.getY(),ticks=duration(rise);
        if(rise>1 || rise< -1 || ticks<0)return false;
        double dx=to.getX()-from.getX(),dz=to.getZ()-from.getZ();
        if(Math.sqrt(dx*dx+dz*dz)/ticks>0.36)return false;
        double y=from.getY(),vy=0.42,half=npc.getWidth()/2.0;
        Vec3d previous=Vec3d.ofBottomCenter(from);
        for(int tick=1;tick<=ticks;tick++){
            y+=vy;vy=(vy-0.08)*0.98;
            Vec3d next=new Vec3d(from.getX()+0.5+dx*tick/ticks,tick==ticks?to.getY():y,from.getZ()+0.5+dz*tick/ticks);
            Box body=new Box(previous.x-half,previous.y+0.001,previous.z-half,
                previous.x+half,previous.y+npc.getHeight(),previous.z+half).stretch(next.subtract(previous));
            for(BlockPos cell:BlockPos.iterate(BlockPos.ofFloored(body.minX,body.minY,body.minZ),BlockPos.ofFloored(body.maxX,body.maxY,body.maxZ))){
                if(!permitted.test(cell) || !npc.getWorld().isChunkLoaded(cell)
                        || !npc.getWorld().getFluidState(cell).isEmpty())return false;
            }
            if(!npc.getWorld().isSpaceEmpty(npc,body))return false;
            previous=next;
        }
        return true;
    }
    public void reset(){goal=null;}
    public boolean active(){return goal!=null;}
    public boolean tick(BlockPos target,Predicate<BlockPos> permitted){
        if(goal==null){
            if(!npc.isOnGround() || !possible(npc,npc.getBlockPos(),target,permitted))return false;
            Vec3d center=Vec3d.ofBottomCenter(npc.getBlockPos());
            double flat=npc.getPos().subtract(center).horizontalLengthSquared();
            if(flat>0.012){npc.getMoveControl().moveTo(center.x,center.y,center.z,0.6);return true;}
            goal=target.toImmutable();flightTicks=duration(target.getY()-npc.getBlockY());launched=npc.age;
            npc.getNavigation().stop();npc.setSprinting(true);
            Vec3d delta=Vec3d.ofBottomCenter(goal).subtract(npc.getPos());
            npc.setVelocity(delta.x/flightTicks,0.42,delta.z/flightTicks);return true;
        }
        if(npc.age==launched)return true;
        Vec3d delta=Vec3d.ofBottomCenter(goal).subtract(npc.getPos());
        if(npc.isOnGround()){
            boolean landed=Math.abs(delta.y)<0.2 && delta.horizontalLengthSquared()<0.4;
            reset();npc.setSprinting(false);return landed;
        }
        if(npc.age-launched>30){reset();npc.setSprinting(false);return false;}
        int remaining=Math.max(2,flightTicks-(npc.age-launched));
        double vx=MathHelper.clamp(delta.x/remaining,-0.36,0.36),vz=MathHelper.clamp(delta.z/remaining,-0.36,0.36);
        Box sweep=npc.getBoundingBox().stretch(vx,0,vz);
        if(!npc.getWorld().isSpaceEmpty(npc,sweep)){reset();npc.setSprinting(false);return false;}
        npc.setVelocity(vx,npc.getVelocity().y,vz);return true;
    }
}
