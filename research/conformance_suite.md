# Conformance Suite — Current Status

> **Status:** ⚙️ Active multiplatform suite covers interpreter flows; alias migration to `in` and JS-capable VM assertions are still outstanding.【F:conformance-tests/build.gradle†L1-L49】【F:interpreter/src/commonMain/kotlin/v2/ir/RunnerMP.kt†L26-L38】

## Modules and Targets
- `conformance-tests` is part of the main Gradle build alongside the interpreter, VM, compiler, and shared fixtures, so CI can run the suite without extra wiring.【F:settings.gradle†L1-L7】
- The module is already Kotlin Multiplatform: JVM runs JUnit 5, JS runs on Node, and both reuse the interpreter module for assertions.【F:conformance-tests/build.gradle†L1-L49】

## Implemented Coverage
- `commonTest` focuses on IR-level parity. Tests construct IR directly and execute it via `buildRunnerFromIRMP`, which seeds a `row` environment and drives the interpreter runner on every platform.【F:interpreter/src/commonMain/kotlin/v2/ir/RunnerMP.kt†L10-L39】【F:conformance-tests/src/commonTest/kotlin/v2/conformance/ConformBasicsTest.kt†L1-L60】【F:conformance-tests/src/commonTest/kotlin/v2/conformance/ConformLoopsTest.kt†L1-L56】【F:conformance-tests/src/commonTest/kotlin/v2/conformance/ConformComputedKeysTest.kt†L1-L29】
- JVM integration tests compile full DSL snippets, pass them through semantic analysis, and execute them with whichever engine is requested. Helpers still wrap user code in `SOURCE row; TRANSFORM …` stubs, so row remains the canonical input binding during tests.【F:test-fixtures/src/jvmMain/kotlin/v2/testutils/Runners.kt†L35-L76】【F:conformance-tests/src/jvmTest/kotlin/v2/EngineSmokeTest.kt†L1-L12】【F:conformance-tests/src/jvmTest/kotlin/v2/integration/TreeReconstructionDSLRuntimeTest.kt†L18-L119】
- `@EngineTest` parameterizes JUnit cases across `ExecutionEngine.INTERPRETER` and `.VM`, giving immediate parity checks whenever new behaviour lands in the VM backend.【F:test-fixtures/src/jvmMain/kotlin/v2/testutils/EngineTest.kt†L1-L13】【F:interpreter/src/commonMain/kotlin/v2/ExecutionEngine.kt†L1-L7】

## Outstanding Work
- The multiplatform runner still hardcodes the legacy `row` binding and mirrors the entire input map into the environment. Migrating to the planned `in` binding (while leaving a compatibility alias) will unblock the language changes tracked in the responsibility brief.【F:interpreter/src/commonMain/kotlin/v2/ir/RunnerMP.kt†L33-L38】【F:test-fixtures/src/jvmMain/kotlin/v2/testutils/Runners.kt†L35-L62】
- `TransformRegistry` currently insists on `Mode.STREAM` and recompiles on the JVM/JS sides using the interpreter; once the VM bytecode can be produced cross-platform we should extend these actuals to dispatch to compiled bytecode as well.【F:interpreter/src/jvmMain/kotlin/v2/ir/TransformRegistryJvm.kt†L7-L35】【F:interpreter/src/jsMain/kotlin/v2/ir/TransformRegistryJs.kt†L7-L27】
- JS tests only validate the interpreter today, because the VM target has no JS runtime yet. Keep the IR-based coverage growing, but plan for bytecode-based assertions (golden programs or property tests) once a JS-capable VM lands.【F:conformance-tests/build.gradle†L32-L49】【d46dc5†L1-L2】
- Broaden JVM parity tests to cover shared state, host integrations, and snapshot/resume paths. Property-based tests exist for numeric parity in the VM module; wiring similar generators through `@EngineTest` will catch interpreter↔VM divergences earlier.【F:vm/src/jvmTest/kotlin/v2/vm/NumericParityPropertyTest.kt†L9-L16】

## Next Steps Checklist
1. Introduce the planned `in`/`row` aliasing in the common runner helpers and migrate the tests gradually.
2. Add golden IR/bytecode programs under `src/commonTest/resources` once the VM backend is shareable, so both engines execute exactly the same fixtures.
3. Promote frequently used DSL samples (Tree reconstruction, host functions) into reusable builders to avoid copying the `SOURCE row` boilerplate in every test.
4. Track test gaps in a simple matrix (feature × engine) and require new features to add coverage in both the IR-driven and DSL-driven layers before merging.
