package dev.aiplayer.ai.navigation;

import dev.aiplayer.entity.CompanionEntity;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.world.World;

/** Ordinary Minecraft navigation and planned terrain actions share one owner.
 * External stops/cancellations also stop any pending prepared movement. */
public final class CompanionNavigation extends MobNavigation {
    private final CompanionEntity npc;
    public CompanionNavigation(CompanionEntity npc,World world){super(npc,world);this.npc=npc;}
    @Override public boolean startMovingAlong(Path path,double speed){
        if(npc.navigationPlan()!=null)npc.navigationPlan().ordinaryPath(path);
        return super.startMovingAlong(path,speed);
    }
    @Override public void stop(){
        if(npc.navigationPlan()!=null)npc.navigationPlan().stopped();
        super.stop();
    }
}
