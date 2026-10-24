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

/** A destination predicate and an estimated remaining cost, in game ticks.
 * Adapted from Baritone's Goal; Minecraft and rendering dependencies removed. */
public interface Goal {
    boolean isInGoal(int x,int y,int z);
    double heuristic(int x,int y,int z);
    default boolean isInGoal(Position pos){return isInGoal(pos.x(),pos.y(),pos.z());}
    default double heuristic(Position pos){return heuristic(pos.x(),pos.y(),pos.z());}
}
