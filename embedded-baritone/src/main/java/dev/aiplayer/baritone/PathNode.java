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

/** Adapted from Baritone PathNode (leijurv): mutable cost/backpointer and
 * indexed open-set membership, with a server-neutral Position and action. */
final class PathNode {
    final Position position;
    final double estimatedCostToGoal;
    double cost=Double.POSITIVE_INFINITY;
    double combinedCost;
    PathNode previous;
    Step incoming;
    int heapPosition=-1;
    PathNode(Position position,Goal goal){
        this.position=position;estimatedCostToGoal=goal.heuristic(position);
        if(!Double.isFinite(estimatedCostToGoal))throw new IllegalArgumentException("goal returned a non-finite heuristic at "+position);
    }
    boolean isOpen(){return heapPosition>=1;}
}
