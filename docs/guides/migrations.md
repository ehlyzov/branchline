---
title: Migration Guides
---

# Migration Guides

## v0.11.0-alpha.1

- Install Java 21+ for JVM distributions. Node distributions require Node 18+.
- Recompile stored transformations from source when moving between Alpha releases.
  New compiled artifacts declare a format/version marker; older runtimes reject
  them. This runtime accepts legacy version-1 artifacts within the documented
  serializable instruction surface. See [compiled compatibility](../vm.md).
- VM compatibility fallback now applies only to compilation failure before
  execution. A runtime exception propagates once; the interpreter does not replay
  the transformation. Code that relied on replay must handle the error explicitly.
- Use the strict VM policy when bytecode execution is required. A successful
  compatibility fallback does not demonstrate VM coverage.
- POWER and SQRT now implement the documented domains and errors. Check
  [numeric semantics](../language/numeric.md), especially JVM/JS precision limits.
- The deterministic authoring profile rejects reachable ambient clock/randomness.
  Supply time and random values explicitly as input when needed.
- Experimental VM backends require explicit Kotlin opt-in. They remain sequential
  and are not a stable API or a replacement for the default VM. Create independent
  VM contexts for concurrent calls; `--jobs` does this for compiled CLI entries.

Keep the previous executable and original source to roll back. Do not expect an
older runtime to read artifacts written by this prerelease, or use snapshots as
portable persistence for executable closures.

## Related
- [Release Readiness & Stability](release-readiness.md)
- [Install Branchline](install.md)
