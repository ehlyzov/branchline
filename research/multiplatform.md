# Multiplatform Migration — Snapshot

> **Status:** ⚙️ In progress — interpreter/VM modules already use Kotlin Multiplatform, but the VM JS backend still lacks implementations.【F:interpreter/build.gradle†L1-L47】【F:vm/build.gradle†L5-L54】【F:vm/src/commonMain/kotlin/v2/vm/README.md†L69-L73】

## Module Layout
- All core modules apply the Kotlin Multiplatform plugin. The interpreter and VM expose JVM + JS targets and reuse shared source sets, while the compiler module keeps stubs so existing callers continue to compile.【F:interpreter/build.gradle†L1-L47】【F:vm/build.gradle†L1-L52】【F:compiler/build.gradle†L1-L36】
- The MPP modules are assembled together inside the main build; the remaining `platform` module is still JVM-only and currently just aggregates dependencies on the three language layers.【F:settings.gradle†L1-L7】【F:platform/build.gradle†L1-L7】

## Shared Runtime Pieces
- Big-number support is implemented as an `expect`/`actual` facade. Common code talks to `BLBigInt`/`BLBigDec`, JVM maps them to `java.math` types, and the JS actuals are lightweight `Long`/`Double` wrappers that let the code compile today.【F:interpreter/src/commonMain/kotlin/v2/runtime/bignum/BigNum.kt†L1-L36】【F:interpreter/src/jvmMain/kotlin/v2/runtime/bignum/BigNumJvm.kt†L1-L37】【F:interpreter/src/jsMain/kotlin/v2/runtime/bignum/BigNumJs.kt†L1-L44】
- Cross-platform execution helpers (`TransformRegistry`, `runIR`, `buildRunnerFromIRMP`) already live in common source sets so JS tests can evaluate IR without the JVM parser or VM.【F:interpreter/src/jvmMain/kotlin/v2/ir/TransformRegistryJvm.kt†L7-L35】【F:interpreter/src/jsMain/kotlin/v2/ir/TransformRegistryJs.kt†L7-L27】【F:interpreter/src/commonMain/kotlin/v2/ir/RunnerMP.kt†L10-L39】
- `compileStream` now resides in the VM module and exposes an `ExecutionEngine` parameter, allowing both interpreter and VM execution paths to share the same entrypoint on the JVM backend.【F:vm/src/commonMain/kotlin/v2/ir/StreamCompiler.kt†L31-L74】【F:interpreter/src/commonMain/kotlin/v2/ExecutionEngine.kt†L1-L7】

## Gaps to Close
- The VM module defines a JS target in Gradle but does not yet ship JS source sets, so bytecode execution remains JVM-only. Interpreter-based JS tests continue to pass, but VM parity on JS will require real `jsMain` actuals and possibly a stripped-down runtime.【F:vm/build.gradle†L1-L52】【d46dc5†L1-L2】
- JS big numbers are lossy because they are backed by `Long`/`Double`; replacing them with `kotlinx-bignum` (or another arbitrary-precision library) is necessary before exposing Branchline semantics on JS to production users.【F:interpreter/src/jsMain/kotlin/v2/runtime/bignum/BigNumJs.kt†L3-L44】
- The multiplatform runners still populate environments with the legacy `row` binding and clone the entire input map. Aligning them with the planned `in` default will avoid another round of breaking changes when the DSL surface is updated.【F:interpreter/src/commonMain/kotlin/v2/ir/RunnerMP.kt†L33-L38】【F:test-fixtures/src/jvmMain/kotlin/v2/testutils/Runners.kt†L35-L76】
- VM compilation currently depends on interpreter classes (IR builders, semantic analysis). Decoupling the shared pieces into true common code and shrinking the remaining JVM-only bits in the `compiler` module will make it easier to publish JS/Native artifacts later. The compiler module already contains placeholders that point callers at the relocated implementations in `vm`/`interpreter`.【F:compiler/src/commonMain/kotlin/v2/ir/StreamCompiler.kt†L1-L3】【F:compiler/src/commonMain/kotlin/v2/ir/TransformRegistry.kt†L1-L3】【F:compiler/src/commonMain/kotlin/v2/vm/Compiler.kt†L1-L3】

## Next Steps
1. Introduce real JS `jsMain` sources for the VM (even if interpreter-backed initially) so the build stops relying on empty source sets.
2. Swap the JS big-number facade to a precise implementation and tighten numeric tests to run under both targets.
3. Extract the minimal IR + evaluation surface that both VM and interpreter share into a neutral module, leaving the legacy compiler stubs behind as a thin compatibility layer.
4. Update the shared runner helpers to expose the `in` binding and document the compatibility timeline for deprecating `row`.
