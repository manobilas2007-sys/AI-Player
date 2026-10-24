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

/** Actor-specific, read-only movement graph. Neighbours must be finite and
 * deterministic. Costs must dominate the selected goal's displacement lower
 * bound; diagonal travel costs at least sqrt(2) walking ticks per block.
 * World mutation, mining, placement and actor movement belong to the executor. */
@FunctionalInterface
public interface SearchContext { Iterable<Step> neighbors(Position from); }
