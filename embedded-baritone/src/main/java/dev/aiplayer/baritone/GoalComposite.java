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

/** Any constituent goal may satisfy this goal, as in upstream GoalComposite.
 * Adaptation: defensive copies prevent active searches changing under callers. */
public final class GoalComposite implements Goal {
    private final Goal[] goals;
    public GoalComposite(Goal... goals){
        Objects.requireNonNull(goals,"goals");
        if(goals.length==0)throw new IllegalArgumentException("at least one goal is required");
        this.goals=goals.clone();
        for(Goal goal:this.goals)Objects.requireNonNull(goal,"goal");
    }
    @Override public boolean isInGoal(int x,int y,int z){
        for(Goal goal:goals)if(goal.isInGoal(x,y,z))return true;
        return false;
    }
    @Override public double heuristic(int x,int y,int z){
        double cost=Double.POSITIVE_INFINITY;
        for(Goal goal:goals)cost=Math.min(cost,goal.heuristic(x,y,z));
        return cost;
    }
    public Goal[] goals(){return goals.clone();}
    @Override public boolean equals(Object other){return other instanceof GoalComposite c && Arrays.equals(goals,c.goals);}
    @Override public int hashCode(){return Arrays.hashCode(goals);}
    @Override public String toString(){return "GoalComposite"+Arrays.toString(goals);}
}
