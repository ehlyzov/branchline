---
status: In Progress
depends_on: []
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-01
changelog:
  - date: 2026-02-01
    change: "Migrated from research/multiplatform.md and added YAML front matter."
  - date: 2026-02-01
    change: "Aligned to input binding wording."
---
# Multiplatform Migration — Snapshot


## Status (as of 2026-01-31)
- Stage: In progress.
- Interpreter/VM/CLI are KMP; VM JS backend still lacks implementations and the compiler module has been removed.
- Next: decide the VM JS scope and implement missing instructions or document unsupported paths.


## Module Layout
- KMP modules in the build: `interpreter`, `vm`, `cli`, `test-fixtures`, and `conformance-tests` (registered in `settings.gradle`).【F:settings.gradle†L1-L7】【F:interpreter/build.gradle†L1-L47】【F:vm/build.gradle†L1-L54】【F:cli/build.gradle†L1-L48】【F:conformance-tests/build.gradle†L1-L52】【F:test-fixtures/build.gradle†L1-L52】
- There is no `compiler/` module in the repo, and the `platform` include in `settings.gradle` points to a missing directory; multiplatform work lives in the interpreter/VM/CLI stack today.【F:settings.gradle†L4-L9】

## Shared Runtime Pieces
- Big-number support is implemented as an `expect`/`actual` facade. Common code talks to `BLBigInt`/`BLBigDec`, JVM maps them to `java.math` types, and the JS actuals are lightweight `Long`/`Double` wrappers that let the code compile today.【F:interpreter/src/commonMain/kotlin/v2/runtime/bignum/BigNum.kt†L1-L36】【F:interpreter/src/jvmMain/kotlin/v2/runtime/bignum/BigNumJvm.kt†L1-L37】【F:interpreter/src/jsMain/kotlin/v2/runtime/bignum/BigNumJs.kt†L1-L44】
- Cross-platform execution helpers (`TransformRegistry`, `runIR`, `buildRunnerFromIRMP`) already live in common source sets so JS tests can evaluate IR without the JVM parser or VM.【F:interpreter/src/jvmMain/kotlin/v2/ir/TransformRegistryJvm.kt†L7-L35】【F:interpreter/src/jsMain/kotlin/v2/ir/TransformRegistryJs.kt†L7-L27】【F:interpreter/src/commonMain/kotlin/v2/ir/RunnerMP.kt†L10-L39】
- `compileStream` now resides in the VM module and exposes an `ExecutionEngine` parameter, allowing both interpreter and VM execution paths to share the same entrypoint on the JVM backend.【F:vm/src/commonMain/kotlin/v2/ir/StreamCompiler.kt†L31-L74】【F:interpreter/src/commonMain/kotlin/v2/ExecutionEngine.kt†L1-L7】

## Gaps to Close
- The VM module defines a JS target in Gradle but does not yet ship JS source sets, so bytecode execution remains JVM-only. Interpreter-based JS tests continue to pass, but VM parity on JS will require real `jsMain` actuals and possibly a stripped-down runtime.【F:vm/build.gradle†L1-L52】【d46dc5†L1-L2】
- JS big numbers are lossy because they are backed by `Long`/`Double`; replacing them with `kotlinx-bignum` (or another arbitrary-precision library) is necessary before exposing Branchline semantics on JS to production users.【F:interpreter/src/jsMain/kotlin/v2/runtime/bignum/BigNumJs.kt†L3-L44】
- The multiplatform runners still populate environments with the legacy `row` binding and clone the entire input map. Aligning them with the `input` default will avoid another round of breaking changes when the DSL surface is updated.【F:interpreter/src/commonMain/kotlin/v2/ir/RunnerMP.kt†L33-L38】【F:test-fixtures/src/jvmMain/kotlin/v2/testutils/Runners.kt†L35-L76】
- VM compilation currently depends directly on interpreter classes (IR builders, semantic analysis); there is no separate compiler module or public façade yet, so external consumers still call into `compileStream`/`VMExec` directly.【F:vm/src/commonMain/kotlin/v2/ir/StreamCompiler.kt†L31-L83】【F:interpreter/src/commonMain/kotlin/v2/ir/ToIR.kt†L1-L160】

## Next Steps
1. Introduce real JS `jsMain` sources for the VM (even if interpreter-backed initially) so the build stops relying on empty source sets.
2. Swap the JS big-number facade to a precise implementation and tighten numeric tests to run under both targets.
3. Extract the minimal IR + evaluation surface that both VM and interpreter share into a neutral module or façade, since there is no compiler module acting as the public API.
4. Update the shared runner helpers to expose the `input` binding and document the compatibility timeline for deprecating `row`.
