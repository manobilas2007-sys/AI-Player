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

import java.util.Objects;

/** One graph edge, carrying its real estimated tick cost and executor action. */
public record Step(Position destination,double cost,Movement movement) {
    public Step {
        Objects.requireNonNull(destination,"destination");Objects.requireNonNull(movement,"movement");
        if(!Double.isFinite(cost) || cost<=0)throw new IllegalArgumentException("step cost must be finite and positive");
    }
}
