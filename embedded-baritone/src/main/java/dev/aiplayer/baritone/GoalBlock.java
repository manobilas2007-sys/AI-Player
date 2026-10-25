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

/** Exact block goal. Adapted from Baritone GoalBlock by leijurv.
 * Uses max rather than a sum because an NPC step can change X/Z and Y together. */
public record GoalBlock(int x,int y,int z) implements Goal {
    public GoalBlock(Position position){this(position.x(),position.y(),position.z());}
    @Override public boolean isInGoal(int x,int y,int z){return this.x==x && this.y==y && this.z==z;}
    @Override public double heuristic(int x,int y,int z){
        return calculate((double)x-this.x,(double)y-this.y,(double)z-this.z);
    }
    public static double calculate(double dx,double dy,double dz){
        return Math.max(GoalXZ.calculate(dx,dz),GoalYLevel.calculateDifference(dy));
    }
}
