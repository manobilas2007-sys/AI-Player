package dev.aiplayer.ai;

import dev.aiplayer.entity.CompanionEntity;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.block.Blocks;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import java.util.*;

/** Reserves movement controls for the central intelligence and supplies safe recall. */
public class CompanionGoal extends Goal {
    private final CompanionEntity npc;
    public CompanionGoal(CompanionEntity npc) {
        this.npc = npc;
        setControls(EnumSet.of(Control.MOVE,Control.LOOK,Control.JUMP));
    }
    @Override public boolean canStart() { return npc.ownerId() != null; }
    @Override public boolean shouldContinue() { return canStart(); }
    @Override public boolean shouldRunEveryTick() { return true; }

    public static boolean isNaturallyHostile(LivingEntity e) {
        // Slimes/magma cubes are Monster mobs but do not inherit HostileEntity.
        return (e instanceof HostileEntity || e instanceof SlimeEntity)
            && !(e instanceof EndermanEntity) && !(e instanceof ZombifiedPiglinEntity);
    }

    @Override public void tick() {
        // Movement ownership is held by this goal, but the single decision tick runs in CompanionEntity.
    }

    public static boolean recall(CompanionEntity npc,PlayerEntity owner) {
        if(npc.getWorld()!=owner.getWorld())return false;
        BlockPos base=owner.getBlockPos();
        for(int radius=2;radius<=4;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++) {
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            for(int dy : new int[]{0,1,-1,2,-2}) {
                BlockPos p=base.add(dx,dy,dz);
                if(!npc.getWorld().isChunkLoaded(p))continue;
                var floor=npc.getWorld().getBlockState(p.down());
                if(!floor.isSolidBlock(npc.getWorld(),p.down()) || floor.isOf(Blocks.MAGMA_BLOCK)
                    ||floor.isOf(Blocks.CACTUS)||floor.isOf(Blocks.CAMPFIRE)||floor.isOf(Blocks.SOUL_CAMPFIRE))continue;
                if(!npc.getWorld().getFluidState(p).isEmpty()||!npc.getWorld().getFluidState(p.up()).isEmpty())continue;
                if(!npc.getWorld().getBlockState(p).isAir()||!npc.getWorld().getBlockState(p.up()).isAir())continue;
                Vec3d to=Vec3d.ofBottomCenter(p);
                Box box=npc.getBoundingBox().offset(to.subtract(npc.getPos()));
                if(!npc.getWorld().isSpaceEmpty(npc,box))continue;
                npc.getNavigation().stop();npc.refreshPositionAndAngles(to.x,to.y,to.z,npc.getYaw(),npc.getPitch());
                npc.setVelocity(Vec3d.ZERO);npc.fallDistance=0;return true;
            }
        }
        return false;
    }
    @Override public void stop() { npc.getNavigation().stop(); npc.setTarget(null); }
}
