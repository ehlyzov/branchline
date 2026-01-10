# Examples module

This module contains runnable JVM demos that exercise Branchline DSL on larger graphs/trees with shared-state
orchestration. Each demo prints per-phase timing plus the maximum number of running transforms and waiting
transforms (blocked on `AWAIT_SHARED`) observed during that phase.

## Task 1: Shortest path (BFS)
- Builds a random directed graph rooted at a random start node.
- Processes edges level-by-level (BFS order) so the first distance written is the shortest.
- Uses shared `distances` and `parents` to reconstruct the path.

Run:
```
./gradlew :examples:runShortestPathDemo
```

## Task 2: Weighted tree metrics
- Builds a weighted tree with sentinel `0` edges for the root and leaves.
- Top-down root distance pass uses `rootDistance` shared state.
- Bottom-up leaf distance pass computes a single min per node using post-order traversal.
- Emits per-edge metrics `{ src, dst, rootSum, leafSum }`.

Run:
```
./gradlew :examples:runWeightedTreeDemo
```

## Task 3: Full tree reconstruction
- Reuses shared state from Task 2 (`rootDistance`, `leafDistance`) and stored adjacency lists.
- Builds a single nested tree snapshot with node metrics and weighted edges.

Run:
```
./gradlew :examples:runTreeReconstructionDemo
```
