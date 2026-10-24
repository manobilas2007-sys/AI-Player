/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

// AI-Player adaptation, 2026: Java 17, server-neutral API; see UPSTREAM.md.

package dev.aiplayer.baritone;

/** Vertical lower bounds from Baritone's GoalYLevel and gravity tick costs. */
public record GoalYLevel(int level) implements Goal {
    @Override public boolean isInGoal(int x,int y,int z){return y==level;}
    @Override public double heuristic(int x,int y,int z){return calculate(level,y);}
    public static double calculate(int goalY,int currentY){return calculateDifference((double)currentY-goalY);}
    static double calculateDifference(double difference){
        return difference>0 ? ActionCosts.FALL_N_BLOCKS_COST[2]/2*difference
            : -difference*ActionCosts.JUMP_ONE_BLOCK_COST;
    }
}
