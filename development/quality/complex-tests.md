---
status: Implemented
depends_on: []
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-01
changelog:
  - date: 2026-02-01
    change: "Migrated from research/complextests.md and added YAML front matter."
---
# Task

## Status (as of 2026-01-31)
- Stage: Implemented.
- Steps 1-5 are marked complete (tests refactored, examples module + docs + samples added).
- Next: keep examples aligned with conformance coverage and add perf/metrics if needed.

Demonstrate complex, large-graph processing with Branchline DSL transforms, shared-state orchestration,
and parallel execution metrics.

# Demo implementation
There are some complex tests in the conformance tests module: `conformance-tests/src/jvmTest/kotlin/io/github/ehlyzov/branchline/integration/ComplexGraphTransformsTest.kt`.

It generates large graphs/trees and runs the three tasks sequentially:
Task 1 (shortest path), Task 2 (root + nearest-leaf distances), Task 3 (full tree reconstruction).

The demo prints:
- Task execution time (per phase and total).
- Number of running transforms.
- Number of waiting transforms (blocked on `AWAIT_SHARED`).

# Task 1: Shortest path between two graph nodes
Input is a large directed graph (random edges). Two random nodes are chosen (ensuring reachability).
The orchestrator processes edges level-by-level (BFS order) so the first distance written for a node is
the shortest. A `BuildPath` function reconstructs the path by following `parents`.

Expected output: a single object with `{ start, goal, distance, path }`, where `path` is the shortest
node list.

# Task 2: Weighted tree processing (nearest-leaf distance)
The weighted tree uses a sentinel `0`:
- `start = 0` marks the root edge.
- `end = 0` marks leaf edges.

Two passes:
1. Root distance (top-down): `rootDistance[end] = rootDistance[start] + weight`.
2. Nearest-leaf distance (bottom-up): for each node, choose the minimum over all leaf paths:
   `leafDistance[start] = min(weight + leafDistance[child])`, with `leafDistance[leaf] = weight`.

The demo ensures a post-order traversal so the bottom-up `min` is computed once and written to
`SHARED ... SINGLE`.

Expected output: per edge `{ src, dst, rootSum, leafSum }` where `leafSum` is the nearest-leaf distance.

# Task 3: Full tree reconstruction from Task 2 shared state
Task 3 runs after Task 2 and reads the shared state produced by Task 2. It builds one output object
representing the entire tree, including:
- Nodes with `rootDistance` and `leafDistance`.
- Edges with weights.
- Nested children lists (full reconstruction from adjacency).

Expected output: exactly one ob`j`ect containing the full tree snapshot.

# Goal 
We need to:
- Ensure that code in tests matches tasks descriptions above.
- Convert tests in the demo. It should have an executable main function which runs three defined tasks. It should conforms with what described in "Demo implementation" section. You can add new module: "examples" and explain all tasks in its README.md . The goal is to show branchline capabilitities.

# Plan
1. Add this plan and review notes, then keep a step-by-step implementation log here.
2. Refactor `ComplexGraphTransformsTest` to match the three tasks: BFS-ordered shortest path on a random graph, weighted tree root/leaf passes with post-order min aggregation, and full tree reconstruction from Task 2 shared state.
3. Create an `examples` module with three runnable mains (one per task), instrumentation for running/awaiting transforms, and a README explaining inputs/outputs and how to run.
4. Add a docs page describing what users can find in the examples module and update this log after each step.

# Review notes
- The shortest path test does not enforce BFS level ordering and the sample graph only has a single path, so the shared-state shortest-path behavior is not truly exercised.
- The weighted tree test uses a cycle instead of a tree and does not compute a min across multiple leaf paths.
- The graph rebuild test is separate from Task 2 state and does not reconstruct a full tree with root/leaf metrics.
- There is no runnable demo main or README, and no metrics for running/waiting transforms.

# Implementation log
## Step 1
- Added the plan and review notes to this document.
- Prepared this log section to record concrete changes for each step.

## Step 2
- Reworked `ComplexGraphTransformsTest` to generate a random, connected graph rooted at a random start node, and to ingest edges in BFS level order while asserting shortest-path outputs.
- Updated Task 2 to use a real weighted tree with sentinel root/leaf edges, a top-down root-distance pass, and a post-order leaf-distance pass that computes a single min per node.
- Replaced the old graph rebuild test with Task 3 tree snapshot validation: it reuses Task 2 shared state, stores children adjacency, and asserts a single nested tree output.

## Step 3
- Added the `examples` module (Gradle config + source set) and wired it into `settings.gradle`.
- Implemented a shared support layer for graph/tree generation, DSL parsing, execution, and runtime metrics (running/awaiting transforms).
- Added three runnable mains (one per task) plus `examples/README.md` describing inputs/outputs and how to run each demo.
- Added coroutines dependency and wrapped async usage in explicit coroutine scopes for demo execution helpers.

## Step 4
- Added `docs/guides/examples.md` describing the examples module and how to run each demo.
- Linked the new guide from `docs/guides/index.md` and `docs/toc.yaml`.

## Step 5
- Captured sample outputs from the three example runs and added them to `docs/guides/examples.md` for the site.
