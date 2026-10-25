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

/** Baritone's spherical near-goal, with an admissible enclosing-cube lower
 * bound instead of estimating all the way to the centre. */
public record GoalNear(int x,int y,int z,int range) implements Goal {
    public GoalNear {
        if(range<0)throw new IllegalArgumentException("range must not be negative");
    }
    public GoalNear(Position position,int range){this(position.x(),position.y(),position.z(),range);}
    @Override public boolean isInGoal(int x,int y,int z){
        double dx=(double)x-this.x,dy=(double)y-this.y,dz=(double)z-this.z;
        return dx*dx+dy*dy+dz*dz<=(double)range*range;
    }
    @Override public double heuristic(int x,int y,int z){
        return GoalBlock.calculate(outside((double)x-this.x),outside((double)y-this.y),outside((double)z-this.z));
    }
    private double outside(double difference){
        return Math.copySign(Math.max(0,Math.abs(difference)-range),difference);
    }
}
