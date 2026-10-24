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

/** Baritone's octile X/Z goal. The fixed lower bound replaces global client settings. */
public record GoalXZ(int x,int z) implements Goal {
    public GoalXZ(Position position){this(position.x(),position.z());}
    @Override public boolean isInGoal(int x,int y,int z){return this.x==x && this.z==z;}
    @Override public double heuristic(int x,int y,int z){return calculate((double)x-this.x,(double)z-this.z);}
    public static double calculate(double dx,double dz){
        double x=Math.abs(dx),z=Math.abs(dz);
        double diagonal=Math.min(x,z),straight=Math.max(x,z)-diagonal;
        return (diagonal*Math.sqrt(2)+straight)*ActionCosts.WALK_ONE_BLOCK_COST;
    }
}
