# Runtime Optimizations — Reality Check

> **Status:** ⏳ Research backlog — hotspots remain in the shipped VM; none of the outlined optimizations have landed yet.【F:vm/src/commonMain/kotlin/v2/vm/VM.kt†L754-L880】【F:vm/src/commonMain/kotlin/v2/vm/Compiler.kt†L729-L765】

## What the VM Does Today
- Lambda calls still spin up a nested VM. The main VM keeps a lazily-created `lambdaVm` instance and every `CALL`/`CALL_FN` delegates to `lambdaVm.execute`, so we pay for environment copies and snapshot plumbing on each invocation.【F:vm/src/commonMain/kotlin/v2/vm/VM.kt†L43-L68】【F:vm/src/commonMain/kotlin/v2/vm/VM.kt†L940-L1027】
- Local bindings are mirrored into the global environment to preserve interpreter semantics. `compileLet` emits `DUP; STORE_VAR; STORE_LOCAL`, and `SET`/`LET` keep updating the map, which prevents us from treating locals as pure array slots.【F:vm/src/commonMain/kotlin/v2/vm/Compiler.kt†L178-L207】
- The hot execution loop still relies on reflection for tracing and error reporting (`instruction::class.simpleName`), so even with tracing disabled we allocate strings and hit metadata lookups on every instruction.【F:vm/src/commonMain/kotlin/v2/vm/VM.kt†L187-L208】【F:vm/src/commonMain/kotlin/v2/vm/VM.kt†L667-L872】
- Object and array construction paths remain generic. The compiler emits only `MAKE_OBJECT(n)`/`MAKE_ARRAY(n)` variants, which means small literals pay the full dispatch cost instead of using super-instructions such as `MAKE_OBJECT_2`.【F:vm/src/commonMain/kotlin/v2/vm/Compiler.kt†L732-L758】
- Property access is resolved dynamically for every instruction. There are no inline caches on `ACCESS_STATIC`/`SET_STATIC`, so repeated lookups keep probing `LinkedHashMap` instances in the VM runtime.【F:vm/src/commonMain/kotlin/v2/vm/Compiler.kt†L224-L305】【F:vm/src/commonMain/kotlin/v2/vm/VM.kt†L730-L871】

## High-Impact Next Steps
1. **Function table + closures.** Precompile function bodies into a shared table and reuse the existing call frame machinery instead of invoking `lambdaVm`. This removes the extra VM hop and simplifies suspension handling.
2. **Locals-first execution.** Introduce an escape analysis pass so that uncaptured variables live purely in local slots. When a binding needs to escape, emit a single `STORE_BOTH` instruction instead of mirroring by default.
3. **Opcode IDs for tracing.** Attach integer opcode identifiers to each instruction and drive counters/logging through them. Keep human-readable names behind a guarded path for debug builds only.
4. **Specialised builders and quickening.** Add `MAKE_OBJECT_1/2/4`, small-array constructors, and constant-prefix concatenation opcodes. Combine them with inline caches for object slots and host function indices to avoid repeated map lookups.
5. **Profile-guided cleanup.** Once the structural items above land, re-run the VM microbenchmarks (`vm/src/jmh`) to verify improvements and identify any remaining hotspots (e.g. map allocations in MODIFY/APPEND).

These changes build directly on the current implementation and address the hottest call sites we still see in the JVM profiler. They also pave the way for future multiplatform backends by reducing reliance on reflection-heavy JVM facilities.
