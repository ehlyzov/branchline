# Runtime Optimizations — Reality Check

> **Status:** ⚙️ Partial — function/lambda calls now stay inside the main VM with cached bytecode and small output/peephole opts, but env mirroring, reflection-heavy tracing, generic allocators, and missing inline caches remain the big hitters.【F:vm/src/commonMain/kotlin/v2/vm/VM.kt†L196-L240】【F:vm/src/commonMain/kotlin/v2/vm/VM.kt†L990-L1108】【F:vm/src/commonMain/kotlin/v2/vm/Compiler.kt†L185-L195】【F:vm/src/commonMain/kotlin/v2/vm/Compiler.kt†L350-L369】【F:vm/src/commonMain/kotlin/v2/vm/Compiler.kt†L720-L744】【F:vm/src/commonMain/kotlin/v2/vm/Compiler.kt†L1128-L1166】

## What the VM Does Today
- Function and lambda calls reuse VM call frames with per-function bytecode caches; no nested `lambdaVm` is spun up.【F:vm/src/commonMain/kotlin/v2/vm/VM.kt†L990-L1108】
- Locals still mirror into the global environment for compatibility: `compileLet` emits `DUP; STORE_VAR; STORE_LOCAL`, keeping locals tied to map updates instead of pure slots.【F:vm/src/commonMain/kotlin/v2/vm/Compiler.kt†L185-L195】
- The hot loop derives opcode names via `instruction::class.simpleName` for tracing/counters, so reflection/string allocation stays on the critical path when stepping is enabled.【F:vm/src/commonMain/kotlin/v2/vm/VM.kt†L196-L240】
- Object/array builders remain generic (`MAKE_OBJECT(n)`, `MAKE_ARRAY(n)`), so small literals do not get specialised constructors.【F:vm/src/commonMain/kotlin/v2/vm/Compiler.kt†L720-L744】【F:vm/src/commonMain/kotlin/v2/vm/Compiler.kt†L685-L692】
- Property access still relies on map lookups with no inline caches on static keys; env mirroring also keeps the global map hot for writes/reads.【F:vm/src/commonMain/kotlin/v2/vm/Compiler.kt†L185-L226】
- Some micro-opts already exist: OUTPUT_1/OUTPUT_2 for tiny objects and a peephole pass that drops trivial PUSH/POP/DUP/SWAP pairs.【F:vm/src/commonMain/kotlin/v2/vm/Compiler.kt†L350-L369】【F:vm/src/commonMain/kotlin/v2/vm/Compiler.kt†L1128-L1166】

## High-Impact Next Steps
1. **Locals-first execution.** Add escape analysis so uncaptured vars stay in locals; emit a single `STORE_BOTH` only when a binding must mirror into the environment.
2. **Opcode IDs for tracing.** Replace reflection-based names in the hot loop with integer opcode IDs, gating human-readable labels behind debug tracing.
3. **Specialised builders and quickening.** Add `MAKE_OBJECT_1/2/4`, small-array constructors, and inline caches for `ACCESS_STATIC`/`SET_STATIC` and host indices.
4. **Profile-guided cleanup.** Re-run the JMH suite once the structural items land to verify gains and chase residual hotspots (e.g., map churn in MODIFY/APPEND).

These changes build on the current implementation and aim to clear the remaining bottlenecks before tackling cross-platform backends.
