---
title: Examples
---

# Examples

The `examples/` module contains runnable JVM demos that showcase complex Branchline DSL workflows over
large graphs and trees. Each demo prints per-phase timing, plus the maximum number of running transforms
and waiting transforms (blocked on `AWAIT_SHARED`) observed during that phase.

## Included demos

- **Shortest path (Task 1)**: BFS-ordered ingestion over a random directed graph using shared `distances`
  and `parents` to reconstruct the shortest path.
- **Weighted tree metrics (Task 2)**: Top-down root distance and bottom-up leaf distance passes over a
  weighted tree with sentinel root/leaf edges.
- **Full tree reconstruction (Task 3)**: Builds a nested tree snapshot by combining Task 2 shared state
  with stored adjacency lists.

## Run locally

```
./gradlew :examples:runShortestPathDemo
./gradlew :examples:runWeightedTreeDemo
./gradlew :examples:runTreeReconstructionDemo
```

## Sample output

Results below are from a local run and will vary by machine.

Shortest path (Task 1):
```
Task 1: Shortest path
Nodes: 2000 | Edges: 5999 | Start: 1586 | Goal: 265
Phase 1: Edge ingest: 200ms | running=11 waiting=0
Distance: 10 | Path length: 11
Phase 2: Shortest path query: 6ms | running=1 waiting=0
Total time: 216ms
```

Weighted tree metrics (Task 2):
```
Task 2: Weighted tree metrics
Nodes: 512 | Edges: 511 | Root: 1
Phase 1: Root distance pass: 44ms | running=9 waiting=0
Phase 2: Leaf distance pass: 34ms | running=12 waiting=0
Phase 3: Edge metrics snapshot: 10ms | running=11 waiting=0
Sample metrics:
edge 1 -> 2 | rootSum=5 | leafSum=20
edge 5 -> 12 | rootSum=8 | leafSum=18
edge 2 -> 4 | rootSum=8 | leafSum=15
Total time: 103ms
```

Full tree reconstruction (Task 3):
```
Task 3: Full tree reconstruction
Nodes: 256 | Edges: 255 | Root: 1
Phase 1: Root distance pass: 33ms | running=9 waiting=0
Phase 2: Leaf distance pass: 23ms | running=11 waiting=0
Phase 3: Store children adjacency: 5ms | running=0 waiting=0
Root distances: rootSum=0 | leafSum=21
Tree nodes reconstructed: 256
Phase 4: Snapshot tree: 33ms | running=1 waiting=0
Total time: 103ms
```
