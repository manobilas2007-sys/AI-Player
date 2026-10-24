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

import java.util.List;
import java.util.Objects;

/** Only COMPLETE routes are suitable for execution. PARTIAL is diagnostic
 * progress towards a goal; LIMIT and NO_PATH do not authorize movement. */
public record SearchResult(Status status,List<Step> steps,int expandedNodes,double cost) {
    public enum Status { COMPLETE, PARTIAL, NO_PATH, LIMIT }
    public SearchResult {
        Objects.requireNonNull(status,"status");steps=List.copyOf(steps);
        if(expandedNodes<0 || !Double.isFinite(cost) || cost<0)throw new IllegalArgumentException("invalid result accounting");
    }
    public boolean complete(){return status==Status.COMPLETE;}
}
