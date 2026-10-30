# Embedded Baritone planning core

This Java 17 module adapts selected real source algorithms from [Baritone 1.10.5](https://github.com/cabaletta/baritone/tree/8c55ad0721d42a01a359834edc16aaa9b5bc587d), pinned to commit `8c55ad0721d42a01a359834edc16aaa9b5bc587d`. It is LGPL-3.0-or-later. Original headers and attribution remain in the adapted files. The `upstream/` directory contains unchanged copies of the corresponding source files for review; those reference files are excluded from compilation.

## Active algorithms

- The indexed binary heap and mutable path nodes are adapted from `BinaryHeapOpenSet` and `PathNode` (leijurv).
- The A* relaxation loop, decrease-key/reopening, minimum-improvement threshold and best-so-far coefficient strategy are adapted from `AStarPathFinder` and `AbstractNodeCostSearch` (leijurv).
- Goal membership and octile/vertical cost formulas derive from the upstream goal family. `ActionCosts` retains the upstream gravity and tick-cost formulas with package relocation only.

## NPC adaptations

The public API contains no Minecraft, Fabric, rendering, keyboard, network or client-player classes. `SearchContext` supplies the server actor's finite movement graph. The planner does not move an actor, mutate blocks, consume resources or claim success for an incomplete route. The executor must accept only `SearchResult.Status.COMPLETE` and independently revalidate its world actions.

Search limits bound retained nodes, expansions, total considered edges and the Chebyshev radius in all three coordinates. This replaces wall-clock timeouts with deterministic budgets. Heap ties are ordered by total cost, remaining heuristic and coordinates. Closed nodes can reopen when a cheaper route is found. A partial route retains the actual incoming movement actions and cost, but is diagnostic only.

The actor graph must charge at least `WALK_ONE_BLOCK_COST` for a horizontal unit, `sqrt(2)` times that for a diagonal, and the corresponding vertical lower bound for each height change. The server adapter may charge more for danger, swimming, digging or placement. GoalBlock combines horizontal and vertical lower bounds with `max`, because NPC movement can change both at once. GoalNear retains exact spherical membership but estimates distance to an enclosing cube, making its lower bound admissible. GoalRunAway retains distance-from-all-threats/optional-height membership and uses a nonnegative escape-shortfall lower bound instead of the upstream negative distance bias.

## Scope

This is a source adaptation of the listed algorithms, not the untouched client mod. The upstream client controls, Minecraft mixins, player-only process executors, chat commands, schematic rendering, Elytra logic, native Nether pathfinder and client cache are not active code in this module. NPC activity scheduling, physical mining, inventory provenance, recovery and crafting remain the responsibility of the AI Player server adapter.

## Validation and rebuilding

Run `:embedded-baritone:coreTest` from the repository build, or compile `src/main/java` and `src/test/java` with Java 17 and run `dev.aiplayer.baritone.CoreTest`. No test framework or downloaded dependencies are needed. The source JAR includes the adapter sources, this provenance document and the unchanged upstream references so the LGPL component can be rebuilt and replaced.

## Server executor expansion in 0.7.0

The Minecraft-aware adaptations live in `src/main/java/dev/aiplayer/ai/navigation` in the AI Player source JAR, under LGPL-3.0-or-later notices; they cannot live in the Java-only component. Unmodified source references for each newly consulted upstream component are included below `upstream/src/main/java/baritone`.

- `PreparedRoute` / `NpcNavigation`: PathExecutor and PathingBehavior's single movement owner, original per-movement cost timeout, live lookahead, physical completion, failure cancellation and replanning. Server controls replace client key presses. Ordinary and modifying routes share the per-tick search budget.
- `JumpMotion` and prepared movement actions: MovementParkour's launch/flight/landing stages, MovementPillar's jump-before-placement, and MovementHelper/CalculationContext's collision, tool, falling-block and fluid restrictions. Full body ballistic validation and server entity velocities replace player input. Unsafe gravity columns are quarried from above, or released from a verified side footprint when trapped.
- `ResourceIndex`: WorldScanner's resident section/palette scan and MineProcess's retained resource candidates, revalidation and cost ordering. Scans are incremental, with a shared 1024-cell tick budget, bounded coordinate/frontier storage, dimension-tagged NBT and physical travel before interaction. Dense stone is sampled; sparse ore and exposed faces are indexed.
- House, farm and recipe orchestration use AI Player's existing server tasks; their batches, pickup and return obligations are AI Player code. These are not claimed as an unchanged port of Baritone's client processes.

Reference files excluded from compilation are evidence for review, not extra runtime classes. Elytra and portal/interdimension travel are outside the agreed 0.7.0 task set. Tests establish the covered scenarios, not a guarantee of an optimal global route through an unseen world.
