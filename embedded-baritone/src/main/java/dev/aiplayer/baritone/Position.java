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

/** Immutable integer world coordinate, independent of Minecraft classes. */
public record Position(int x,int y,int z) {
    public Position offset(int dx,int dy,int dz) {
        return new Position(Math.addExact(x,dx),Math.addExact(y,dy),Math.addExact(z,dz));
    }
    public double distanceSquared(Position other) {
        double dx=(double)x-other.x,dy=(double)y-other.y,dz=(double)z-other.z;
        return dx*dx+dy*dy+dz*dz;
    }
}
