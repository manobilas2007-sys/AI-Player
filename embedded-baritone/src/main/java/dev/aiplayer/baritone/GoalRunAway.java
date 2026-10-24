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

import java.util.Arrays;
import java.util.Objects;

/** Baritone flee-goal membership, adapted to a nonnegative admissible
 * shortfall heuristic and server-neutral positions instead of client helpers. */
public final class GoalRunAway implements Goal {
    private final Position[] from;
    private final double distance;
    private final Integer maintainY;
    public GoalRunAway(double distance,Position... from){this(distance,null,from);}
    public GoalRunAway(double distance,Integer maintainY,Position... from){
        if(!Double.isFinite(distance) || distance<0)throw new IllegalArgumentException("invalid flee distance");
        Objects.requireNonNull(from,"from");
        if(from.length==0)throw new IllegalArgumentException("at least one threat is required");
        this.from=from.clone();
        for(Position position:this.from)Objects.requireNonNull(position,"threat");
        this.distance=distance;this.maintainY=maintainY;
    }
    @Override public boolean isInGoal(int x,int y,int z){
        if(maintainY!=null && maintainY!=y)return false;
        for(Position position:from){
            double dx=(double)x-position.x(),dz=(double)z-position.z();
            if(dx*dx+dz*dz<distance*distance)return false;
        }
        return true;
    }
    @Override public double heuristic(int x,int y,int z){
        double cost=0;
        for(Position position:from){
            double missing=Math.max(0,distance-Math.hypot((double)x-position.x(),(double)z-position.z()));
            cost=Math.max(cost,missing*ActionCosts.WALK_ONE_BLOCK_COST);
        }
        return maintainY==null?cost:Math.max(cost,GoalYLevel.calculate(maintainY,y));
    }
    @Override public boolean equals(Object other){
        return other instanceof GoalRunAway goal && Double.compare(distance,goal.distance)==0
            && Objects.equals(maintainY,goal.maintainY) && Arrays.equals(from,goal.from);
    }
    @Override public int hashCode(){return Objects.hash(distance,maintainY,Arrays.hashCode(from));}
    @Override public String toString(){return "GoalRunAway{distance="+distance+", y="+maintainY+", from="+Arrays.toString(from)+"}";}
}
