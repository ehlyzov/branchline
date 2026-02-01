---
status: In Progress
depends_on: []
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-01
changelog:
  - date: 2026-02-01
    change: "Migrated from research/conformance_suite.md and added YAML front matter."
  - date: 2026-02-01
    change: "Aligned conformance notes to input binding and buffer-only mode."
  - date: 2026-02-01
    change: "Corrected test helper description and file references."
---
# Conformance Suite -- Current Status


## Status (as of 2026-02-01)
- Stage: In progress.
- Interpreter coverage exists; `input` binding is default with `row` compatibility; JS VM assertions remain open.
- Next: expand VM parity coverage and add JS VM assertions once a JS backend exists.


## Modules and Targets
- `conformance-tests` is part of the main Gradle build alongside the interpreter, VM, CLI, and shared fixtures (settings.gradle still lists a `platform` stub, but no compiler module exists).[F:settings.gradle:L1-L7][F:cli/build.gradle:L1-L48][F:test-fixtures/build.gradle:L1-L52]
- The module is already Kotlin Multiplatform: JVM runs JUnit 5, JS runs on Node, and both reuse the interpreter module for assertions.[F:conformance-tests/build.gradle:L1-L49]

## Implemented Coverage
- `commonTest` focuses on IR-level parity. Tests construct IR directly and execute it via `buildRunnerFromIRMP`, which seeds the `input` binding (with `row` as a compatibility alias) and drives the interpreter runner on every platform.[F:interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/ir/RunnerMP.kt:L10-L39][F:conformance-tests/src/commonTest/kotlin/io/github/ehlyzov/branchline/conformance/ConformBasicsTest.kt:L1-L60][F:conformance-tests/src/commonTest/kotlin/io/github/ehlyzov/branchline/conformance/ConformLoopsTest.kt:L1-L56][F:conformance-tests/src/commonTest/kotlin/io/github/ehlyzov/branchline/conformance/ConformComputedKeysTest.kt:L1-L29]
- JVM integration tests compile full DSL snippets, pass them through semantic analysis, and execute them with whichever engine is requested. Helpers inject a simple `TRANSFORM` wrapper and use the shared runner helpers (no `SOURCE` stub).[F:test-fixtures/src/jvmMain/kotlin/io/github/ehlyzov/branchline/testutils/Runners.kt:L1-L94][F:conformance-tests/src/jvmTest/kotlin/io/github/ehlyzov/branchline/EngineSmokeTest.kt:L1-L12][F:conformance-tests/src/jvmTest/kotlin/io/github/ehlyzov/branchline/integration/TreeReconstructionDSLRuntimeTest.kt:L18-L119]
- `@EngineTest` parameterizes JUnit cases across `ExecutionEngine.INTERPRETER` and `.VM`, giving immediate parity checks whenever new behaviour lands in the VM backend.[F:test-fixtures/src/jvmMain/kotlin/io/github/ehlyzov/branchline/testutils/EngineTest.kt:L1-L13][F:interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/ExecutionEngine.kt:L1-L7]

## Outstanding Work
- The multiplatform runner seeds `input` plus `row` but still mirrors the entire input map into the environment. Decide whether to keep full mirroring or scope it for performance once compatibility costs are understood.[F:interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/ir/RunnerMP.kt:L45-L65]
- `TransformRegistry` forces buffer mode and recompiles on the JVM/JS sides using the interpreter; once the VM bytecode can be produced cross-platform we should extend these actuals to dispatch to compiled bytecode as well.[F:interpreter/src/jvmMain/kotlin/io/github/ehlyzov/branchline/ir/TransformRegistryJvm.kt:L1-L50][F:interpreter/src/jsMain/kotlin/io/github/ehlyzov/branchline/ir/TransformRegistryJs.kt:L1-L40]
- JS tests only validate the interpreter today, because the VM target has no JS runtime yet. Keep the IR-based coverage growing, but plan for bytecode-based assertions (golden programs or property tests) once a JS-capable VM lands.[F:conformance-tests/build.gradle:L32-L49][F:vm/build.gradle:L1-L52]
- Broaden JVM parity tests to cover shared state, host integrations, and snapshot/resume paths. Property-based tests exist for numeric parity in the VM module; wiring similar generators through `@EngineTest` will catch interpreter<->VM divergences earlier.[F:vm/src/jvmTest/kotlin/io/github/ehlyzov/branchline/vm/NumericParityPropertyTest.kt:L9-L16]

## Next Steps Checklist
1. Keep `input` as the default binding and gradually remove any remaining `row` usage in examples and docs.
2. Add golden IR/bytecode programs under `src/commonTest/resources` once the VM backend is shareable, so both engines execute exactly the same fixtures.
3. Promote frequently used DSL samples (Tree reconstruction, host functions) into reusable builders to avoid copy-pasting boilerplate in every test.
4. Track test gaps in a simple matrix (feature x engine) and require new features to add coverage in both the IR-driven and DSL-driven layers before merging.
