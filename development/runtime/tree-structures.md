---
status: Proposed
depends_on: []
blocks: []
supersedes: ['runtime/tree-structures-ru']
superseded_by: []
last_updated: 2026-02-01
changelog:
  - date: 2026-02-01
    change: "English translation of runtime/tree-structures-ru; canonical version."
---
# Tree Data Structures for Branchline

## Status (as of 2026-01-31)
- Stage: recommendations/proposal.
- Implementation: runtime still uses `LinkedHashMap`/`List`; arena structures are not yet adopted.
- Next: pick hot paths and prototype array-based (arena) structures.

Short version: storing a whole tree "on the stack" is only realistic in C/C++/Rust (and even there only with strict lifetimes). On JVM/Kotlin you cannot keep a stable tree on the stack; objects live on the heap. For maximum performance, prefer **flat primitive arrays** plus **indexing** (arena) over node objects and references. That yields cache locality, fewer allocations, and faster traversals.

## Current status in Branchline

In production Branchline still uses `LinkedHashMap`/`List` for objects and arrays. `TreeModel.ObjMap` stores name/index keys in two `LinkedHashMap`s, and the VM also builds/copies objects via `LinkedHashMap`, which is convenient but not memory-local. These recommendations remain relevant: migrating to array arenas (CSR/FCNS) should reduce allocations and improve locality.

## What works faster in practice

### 1) CSR (children in a contiguous block) -- build once, read many

- Structure: `children: IntArray`, `offset: IntArray` (or `start`, `count` per node), `parent: IntArray`, `value: <primitive arrays>`.
- Each node's children sit contiguously in a single array; iterate via `offset[u]..offset[u]+count[u]-1`.
- Pros: ideal locality, minimal memory, fastest subtree traversals (prefix ranges).
- Cons: inserts/removals are expensive (shift tail).
- Best for **immutable** or build-once/read-many trees (ASTs, indexes).

### 2) FCNS (first-child/next-sibling) -- frequent changes

- Array layout:
  `firstChild[u]: Int`, `nextSibling[u]: Int`, `parent[u]: Int`, `value...`.
- Pros: O(1) amortized insert/remove of children without shifting large arrays.
- Cons: traversal is a bit less local than CSR, but still better than objects.
- Best for **dynamic** trees.

### 3) Euler tour + ranges

- Store `tin[u], tout[u]` (DFS entry/exit times). Subtree = contiguous range in DFS order.
- Combines with CSR/FCNS to speed subtree aggregation, LCA, etc.

## Why arrays are faster than objects

- No object headers or pointer indirection; less GC pressure.
- Sequential memory access improves cache hit rate and prefetching.
- JVM JIT/escape analysis rarely "stack allocates" whole trees; arrays approximate that model.

## Minimal arena example (Kotlin/JVM)

```kotlin
@JvmInline value class Id(val i: Int)

class TreeArena(capacity: Int, edgesCapacity: Int) {
    private val parent = IntArray(capacity) { -1 }
    private val firstChild = IntArray(capacity) { -1 }
    private val nextSibling = IntArray(capacity) { -1 }
    private val value = IntArray(capacity)

    private var n = 0

    fun newNode(v: Int): Id {
        val id = Id(n++)
        value[id.i] = v
        return id
    }

    fun addChild(p: Id, c: Id) {
        parent[c.i] = p.i
        nextSibling[c.i] = firstChild[p.i]
        firstChild[p.i] = c.i
    }

    inline fun forEachChild(u: Id, f: (Id) -> Unit) {
        var c = firstChild[u.i]
        while (c != -1) { f(Id(c)); c = nextSibling[c] }
    }

    fun sumSubtree(u: Id): Long {
        var acc = 0L
        fun dfs(x: Id) {
            acc += value[x.i]
            var c = firstChild[x.i]
            while (c != -1) { dfs(Id(c)); c = nextSibling[c] }
        }
        dfs(u)
        return acc
    }
}
```
