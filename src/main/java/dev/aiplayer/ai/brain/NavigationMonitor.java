package dev.aiplayer.ai.brain;

import dev.aiplayer.entity.CompanionEntity;

/** Compatibility facade. Progress and replanning belong to the shared physical
 * executor, so this monitor cannot replace a mining/bridge step with a random
 * detour or reset a destination merely because an intermediate path ended. */
public final class NavigationMonitor {
    private final CompanionEntity npc;
    public NavigationMonitor(CompanionEntity npc){this.npc=npc;}
    public int attempts(){return npc.navigationPlan().replans();}
    public void reset(){npc.navigationPlan().resetWatchdog();}
    public void tick(){npc.navigationPlan().tick();}
}
