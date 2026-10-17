# Independent isolation review and selective JSON acceptance cases

Read-only source review on 2026-10-17. This reviewer ran no builds, tests or benchmarks and changed no product code. Initial review covered an in-progress implementation; the final focused static review below records exact saved source hashes. Executable acceptance remains with the parent.

## Phase 1 findings — resolved in final focused static review

- **P2: interrupted joining can outlive the shared-store scope.** In `cli/src/jvmMain/kotlin/io/github/ehlyzov/branchline/cli/JvmPlatform.kt`, the initial implementation of `parallelMapWithWorker` captures `future.get()` with `runCatching` and then calls `shutdown()` in `finally`. `InterruptedException` clears the caller's interrupt flag without joining that future. With two tasks, let the first complete and block the second, then interrupt the caller while it waits for the second: the method throws while that task still runs, and `withSharedStore` restores the global provider. A later host call can observe the wrong provider. Join interrupted futures before leaving the scope, preserving the interruption for propagation, or cancel and await actual task termination. Ordinary task exceptions are already joined in input order.
- **P2 verification gap: overlap assertions run after cleanup releases the same latches.** In `CompiledFanoutIsolationTest.overlappingCompiledWorkersKeepTheirOwnEnvironmentAndStack`, cleanup counts down `firstRead` and `secondRead` before assertions inspect their counts. Those assertions pass even when no input-read events occur. Keep separate observation flags or counters that cleanup cannot modify, and verify two distinct worker threads reached the coordination point. The test's barrier does force overlap when both read events occur; the gap is proving those events occurred. Explicit expected per-job output was added during review and is present in the later observed test source.

**Final disposition:** both findings are resolved by source inspection. `JvmPlatform.parallelMapWithWorker` now retries `get()` on the interrupted future, records interruption as that ordered result's failure, joins all remaining tasks, shuts down the pool, and restores the thread interrupt flag only in the final cleanup. `ParallelWorkerJvmTest.interruptedCollectorKeepsSharedContextUntilWorkersComplete` deliberately interrupts the collector before dispatch, blocks both tasks, verifies no early return during a bounded observation, and checks completion, exception, restored flag and restored provider. `CompiledFanoutIsolationTest` now records actual worker `Thread` objects in a concurrent set and requires exactly two; cleanup cannot manufacture those observations. The expected output also independently checks each seeded job value. No remaining actionable finding was identified within the frozen phase-1 scope. Resource-exhaustion failures during executor submission and simultaneous independent CLI invocations remain outside this slice; this does not claim complete whole-repository concurrency safety.

### Final reviewed source binding (SHA-256)

All paths are repository-relative. Hashes identify saved source bytes, not test results.

```text
0bab901930ced696c554ae1a56ff939bdfde89bec13a263139841d25ff2ef3e1  cli/src/commonMain/kotlin/io/github/ehlyzov/branchline/cli/BranchlineCli.kt
86130af5286106560ababe9d5ca34baa7aa5c48762b15bf24d9d7b10d69cce35  cli/src/commonMain/kotlin/io/github/ehlyzov/branchline/cli/PlatformIO.kt
b910ac3f7655510197fb515db1b1a9df98b359799506a71c706d4fa87f753b3b  cli/src/jvmMain/kotlin/io/github/ehlyzov/branchline/cli/JvmPlatform.kt
25841b39939fce70d44b30ae3446b02072761cf113ab5c2d1c5861c6559514e0  cli/src/jsMain/kotlin/io/github/ehlyzov/branchline/cli/JsPlatform.kt
22f64758e37ab85c5b49d6ac0ba92a45d727343fd400f8388f1ae029dd30b544  cli/src/jvmTest/kotlin/io/github/ehlyzov/branchline/cli/CompiledFanoutIsolationTest.kt
41e5556f5c5f8ca4abbb469fc869747179ca3eca643f6c9d956aae5d6e87403d  cli/src/commonTest/kotlin/io/github/ehlyzov/branchline/cli/ParallelWorkerTest.kt
e8ef2b61eb1151e4c9d096d13101742b16fd05222bef030b866ea0f9d0c935b6  cli/src/jvmTest/kotlin/io/github/ehlyzov/branchline/cli/ParallelWorkerJvmTest.kt
1a94f8706f4eef9a7bcb206107cb9149597083bb09a540399a6b98ade08208c0  interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/std/StdStringsModule.kt
b8d6da10cdcbd5daec0751a0c27cc8cfc083b8a0e32eee08318f742e6a71ead1  interpreter/src/jvmMain/kotlin/io/github/ehlyzov/branchline/std/RegexCacheJvm.kt
2506fb5ebbf4cb8428cbccc079b412d7bdd4fd99021a62fead3f875df21de3ba  interpreter/src/jsMain/kotlin/io/github/ehlyzov/branchline/std/RegexCacheJs.kt
c291876ec27775afdb6bf05ab351c1977586abe6c7b405eca81b22215deb3375  interpreter/src/jvmTest/kotlin/io/github/ehlyzov/branchline/std/RegexCacheConcurrencyTest.kt
```

The reviewed ownership design otherwise gives each worker its own `BranchlineProgram`, contract builder, AST/functions and `VMExec`; workers are created serially and bounded by both jobs and entry count. Result joins preserve input order. CLI-owned trace sessions dispatch serially. Regex cache access is synchronized on JVM and remains bounded at 64 entries. Summary execution stays on the caller after ordinary task completion. The global shared-store provider remains intentionally shared within one invocation; simultaneous independent CLI invocations are outside this slice.

## Phase 2 parent-held acceptance cases

These are independent acceptance obligations for the planned prototype, not executed results. Compare eager and experimental output or failure category/message as appropriate, and inspect plan/fallback metadata separately. Do not infer correct planning merely from equal output on one input.

1. **Alias collisions:** with `{"x":1,"input":{"x":7},"row":{"x":9}}`, compare `input.x`, `row.x` and bare `x`. Existing environment construction puts the raw `input` key over the default alias, then always binds `row` to the outer object. A selected plan must retain an `input` collision even when it does not look like a requested outer field.
2. **Alias rebinding and LET chains:** shadow `input`, shadow `row`, rebind a prior local name, and access a scalar local through another LET. Plans must use sequential binding authority rather than reserve alias names globally or mistake every identifier for an outer field.
3. **Whole-root escape through locals:** a LET binding to `row` or `input`, returned or passed through a second LET, must force eager fallback if the complete root can be observed. Reading a complete selected top-level subtree is safe only when the planner retains that entire value.
4. **LENGTH binding authority:** a user-defined `LENGTH`, local callable named `LENGTH`, whole-root `LENGTH(row)`, and `LENGTH` on a selected subtree must not all be treated as the same built-in plan. Preserve eager results and errors for string/list/object/null/number inputs. String length is UTF-16 code units in the existing implementation.
5. **Rejected syntax in unexecuted branches:** dynamic indexing, unknown calls, lambdas and whole-root observation in a branch that is false for the test data must still produce the documented conservative fallback, not optimistic omission.
6. **Duplicate keys in skipped values:** include a nested skipped duplicate where one spelling uses a Unicode escape, such as `{"keep":1,"unused":{"a":0,"\u0061":1}}`, plus duplicate top-level keys with null values. Duplicate detection must use decoded keys and key presence, not non-null lookup.
7. **Malformed skipped values:** trailing comma, unterminated string, bad escape, invalid high-surrogate escape, mismatched bracket, malformed literal and trailing root content must all fail before VM or host execution. Include a skipped malformed value after a valid selected field to catch premature execution.
8. **Numeric validation in skipped values:** STRICT-mode unsafe integers, integer overflow, more than 15 significant decimal digits, non-finite exponents, missing fraction/exponent digits and leading zeros must match eager rejection. EXTENDED mode must preserve eager acceptance and selected number representation. Avoid silently strengthening existing eager grammar while claiming parity.
9. **Existing Unicode/blank behavior:** compare escaped surrogate pairs, lone escaped low surrogates, raw UTF-16 surrogates and whitespace-only input. The eager parser explicitly validates high-surrogate pairs but currently accepts a lone escaped low surrogate; its top-level `isBlank()` fast path is broader than JSON's four whitespace characters. Preserve behavior or declare a separate semantic change.
10. **Key modes:** keep top-level input keys as strings while recursively converting selected subtree keys in NUMERIC mode, matching `parseJsonObjectInput`. Include canonical and noncanonical numeric key spellings such as `"0"`, `"01"`, a value beyond Long, and negative-looking keys.
11. **Missing/null/type distinctions:** selected missing field, explicit null, null intermediate, scalar intermediate, empty list and empty object must preserve eager VM output or failure; do not synthesize present-null keys for absent input.
12. **Contracts:** STRICT and WARN modes must take the documented eager fallback, including an extra unused field that changes a contract outcome. Equal output with missing warnings is not parity.
13. **Detached lifetime and reuse:** execute distinct large JSON texts repeatedly, retain an earlier output, mutate returned ordinary maps/lists where supported, then execute again. Earlier outputs must stay unchanged and must not retain the source string via token/span views. Heap and RSS evidence must retain identical input/output lifetimes for eager and selective paths.
14. **Validation before side effects:** rejected JSON must produce no VM execution report or host side effect, including when an unsupported plan would otherwise eager-fallback. Fallback must not execute once before validation and again afterward.

Parent owns the final executable gate and benchmark evidence. No selective JSON implementation existed in the reviewed phase-1 snapshot.

## Phase 2 initial static review

Read-only review of the parser projection, planner, programmatic pipeline and common tests; no build or test execution by this reviewer.

**P2: ambient tracing can observe omitted input fields.** `ExperimentalJsonPipeline.materialize` selects solely from the preparation report, and `execute` passes that projected object into the ordinary VM. `VM.currentTracer()` uses `Debug.tracer` when no explicit tracer is supplied, so the API's lack of a tracing parameter does not exclude tracing. For `TRANSFORM Test { OUTPUT input.a; }` and `{"a":1,"unused":2}`, an ambient tracer receiving `TraceEvent.Read("input", value)` observes an input map missing `unused`; forced eager execution exposes the complete map. This also occurs when tracing is installed after `prepare`, so a preparation-only check cannot close the gap. Check the ambient tracer before execution/materialization and take an explicit eager path, or reject tracing at execution before projection if tracing is outside the API contract. Ensure reported effective mode remains truthful if dynamic fallback is chosen. Source anchors: `ExperimentalJsonPipeline.kt:48-55`, `VM.kt:114`, `VM.kt:1785-1790`. Reported to parent immediately; unresolved in the hashes below.

Other inspected mechanisms match the bounded design: the planner always retains a raw `input` collision, treats `row` as the outer-root alias, rejects alias rebinding, root escape and dynamic access, rejects every user-function declaration, and only recognizes unshadowed `LENGTH` by exact name. Complete selected subtrees make subsequent local static access safe. Contracts other than OFF and numeric key mode take explicit eager fallback. Skipped object keys are decoded and tracked separately from projected values; duplicate checking occurs after validating the duplicate value as in eager parsing. Skipped strings share Unicode-escape validation with eager strings; skipped numbers call the same range/precision conversion. The parser tests cover malformed skipped values, truncated prefixes and selected-value parity; source inspection does not replace the parent's executable gate.

### Phase 2 initial reviewed source binding (SHA-256)

```text
dfde7feb777056ccb3a31db25ffefb6cac3db0c5497b6bd1c67da112d000e791  interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/json/JsonInput.kt
a284b9b357c5ae15db94a9867d1c3a780f631fc0f9c10a15411eacc22a0f69ce  interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/json/JsonInputSelection.kt
1a78ab429d6c6b89bc9639cb84773513a91c01d3f2fc4f9f72d5ce676ca9ca8b  cli/src/commonMain/kotlin/io/github/ehlyzov/branchline/cli/ExperimentalJsonPipeline.kt
5543eecf3abf1cbfdba85955b87267f41b4a8d94928859a36949755bbdcde6c9  cli/src/commonMain/kotlin/io/github/ehlyzov/branchline/cli/BranchlineRuntime.kt
e7e26382fea0e10925c478bd97339d2f2dcde7ec7fdc0b3cba319269c5b35d3b  interpreter/src/commonTest/kotlin/io/github/ehlyzov/branchline/json/JsonInputSelectionTest.kt
23db0124f2ab43f63886838656f21b53996fbaec29b2ae2694e09a839368984b  cli/src/commonTest/kotlin/io/github/ehlyzov/branchline/cli/ExperimentalJsonPipelineTest.kt
```

## Phase 2 tracing fix — resolved in final focused static review

The ambient tracing finding is resolved in the reviewed saved source. The public `report` getter now returns an immutable `EAGER` / `AMBIENT_TRACING` context snapshot whenever the prepared plan is selective and `Debug.tracer` is non-null. `materialize` reads that getter for each call, so installing a tracer after preparation requests complete eager input before VM execution. Existing eager reasons retain their original reason. Removing the tracer restores the selective prepared plan; previously read report objects remain unchanged. The documentation explicitly identifies reports as current-context snapshots rather than execution history.

`ambientTracerEnabledAfterPreparationObservesCompleteInput` checks a tracer enabled after preparation, complete omitted-field observation in actual VM read events, materialization under tracing, preparation under tracing, restoration to selective operation, snapshot stability and preservation of `FORCE_EAGER`. No new actionable finding was identified in this focused delta. This review assumes the ambient tracing context is held stable during a sequential call; it does not establish behavior when another thread mutates global tracing during materialization or execution. Parent owns the broad JVM/Node execution gate; this reviewer ran no builds or tests.

### Phase 2 final reviewed source binding (SHA-256)

```text
dfde7feb777056ccb3a31db25ffefb6cac3db0c5497b6bd1c67da112d000e791  interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/json/JsonInput.kt
a284b9b357c5ae15db94a9867d1c3a780f631fc0f9c10a15411eacc22a0f69ce  interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/json/JsonInputSelection.kt
7f462fef84868b3b8a3a23073f2ec8bc9894e3dd6f239771ee1a655e6482f9bb  cli/src/commonMain/kotlin/io/github/ehlyzov/branchline/cli/ExperimentalJsonPipeline.kt
5543eecf3abf1cbfdba85955b87267f41b4a8d94928859a36949755bbdcde6c9  cli/src/commonMain/kotlin/io/github/ehlyzov/branchline/cli/BranchlineRuntime.kt
e7e26382fea0e10925c478bd97339d2f2dcde7ec7fdc0b3cba319269c5b35d3b  interpreter/src/commonTest/kotlin/io/github/ehlyzov/branchline/json/JsonInputSelectionTest.kt
b2cc3e876f26132756baf0adad511f30d11512a454b37f74da7c5676cc90674f  cli/src/commonTest/kotlin/io/github/ehlyzov/branchline/cli/ExperimentalJsonPipelineTest.kt
```

## Phase 2 unary context exposure — new focused finding

**P2: `SUSPEND` is admitted as a scalar unary expression but returns the entire VM environment.** Parent identified the broad unary branch; focused read-only tracing confirms the public source path. Reproduction source: `TRANSFORM Test { OUTPUT SUSPEND 0; }`, input `{"a":1,"unused":2}`, options OFF/default compared with `forceEager = true`. `planJsonInputSelection` accepts `UnaryExpr` by checking only its numeric operand and selects only the implicit raw `input` collision key. The parser recognizes `SUSPEND` as unary (`Parser.kt:923-926`); semantic analysis only checks its operand (`SemanticAnalyzer.kt:360-362`); IR preserves non-object output expressions; compiler emits `SUSPEND` without evaluating the operand (`Compiler.kt:749-751`). The VM builds a snapshot by serializing every environment entry (`VM.kt:1489-1494`) and returns its suspension map as an ordinary successful execution (`VM.kt:166-168`). The experimental result therefore serializes a snapshot missing fields that appear under eager execution, despite the strict VM path check succeeding. No execution was run by this reviewer.

Use an explicit unary token whitelist in the access planner and eager-fallback for `SUSPEND`. The only parser-created unary tokens found are `MINUS`, `BANG`, `AWAIT` and `SUSPEND`. `MINUS` and `BANG` operate on the operand; ordinary unary `AWAIT` compiles as an operand no-op. The parser converts the single static resource access form of `AWAIT` to a separate `SharedStateAwaitExpr`, already rejected by the whitelist. Rejecting `AWAIT` too is a permissible conservative prototype boundary. No additional non-scalar binary context operator was identified in `compileBinaryExpr`.

Finding source binding (SHA-256):

```text
a284b9b357c5ae15db94a9867d1c3a780f631fc0f9c10a15411eacc22a0f69ce  interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/json/JsonInputSelection.kt
c0a0434f6fcc7172f5551f2d464c24363ef7abf9f20406db03670e7a1e2c039b  interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/Parser.kt
780863b0849a8c6fb23e5a0ec67ef8da68751e891cbb1669c7b32bf4583453cf  interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/sema/SemanticAnalyzer.kt
e1fd5dc80eef9bdff446b5f85db4ba3512c3f66cae60c1b7b6a6e35c7c479646  vm/src/commonMain/kotlin/io/github/ehlyzov/branchline/vm/Compiler.kt
4fea7d8220392e87b408406405f04992711c2884548c5c4f3a66c11d392a2d49  vm/src/commonMain/kotlin/io/github/ehlyzov/branchline/vm/VM.kt
```

## Phase 2 unary fix — resolved in final focused static review

The `SUSPEND` finding is resolved by the saved source reviewed here. The planner admits unary `MINUS` and `BANG` explicitly and rejects both orchestration tokens `SUSPEND` and `AWAIT`. Planner tests reject direct suspension, suspension under LET and ordinary unary await while retaining scalar unary support. Pipeline tests exercise both direct and LET-bound suspension, require `EAGER` / `UNSUPPORTED_SYNTAX`, compare the exact output string with forced eager execution, and require the suspended result and unused field to remain present. No new actionable finding was identified in this narrow delta; executable results belong to the parent gate.

Final source binding after both phase-2 fixes (SHA-256):

```text
dfde7feb777056ccb3a31db25ffefb6cac3db0c5497b6bd1c67da112d000e791  interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/json/JsonInput.kt
125d66bba45bb478adb344b95a5c096afde4637b0d5623e407387f156299ab03  interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/json/JsonInputSelection.kt
7f462fef84868b3b8a3a23073f2ec8bc9894e3dd6f239771ee1a655e6482f9bb  cli/src/commonMain/kotlin/io/github/ehlyzov/branchline/cli/ExperimentalJsonPipeline.kt
5543eecf3abf1cbfdba85955b87267f41b4a8d94928859a36949755bbdcde6c9  cli/src/commonMain/kotlin/io/github/ehlyzov/branchline/cli/BranchlineRuntime.kt
a57763a993f9aeedf2dbced7081c05611d1cd7920dab571afa9b273619ff2427  interpreter/src/commonTest/kotlin/io/github/ehlyzov/branchline/json/JsonInputSelectionTest.kt
fbf8a77e16a418d9120c47d3880dbf349e8d4fe62c88bf39d7e86ab779837790  cli/src/commonTest/kotlin/io/github/ehlyzov/branchline/cli/ExperimentalJsonPipelineTest.kt
```

## Memory interpretation review

Read-only inspection of `memory-summary.csv`, four representative raw logs and the probe source; this reviewer started no measurement process. The probe records `MemoryMXBean.heapMemoryUsage.used` after `System.gc()`, finalization and another GC request, with explicit volatile strong roots released between methods. These are **post-requested-GC process heap-used observations under intended owner lifetimes**, not exact object-graph retained sizes or a census of all live objects. `detachedOutputOnly` names the strong roots intentionally kept by the probe; it does not prove that the entire reported heap belongs to the output.

The distinction is visible in the summary: strings/16 MiB/1%/selective has baseline 23,724,728 bytes and final 23,904,592 bytes even though raw and pipeline weak references clear; records/16 MiB/1%/selective is similarly flat across release. The weak-reference checks prove that those two referent objects (raw `String` and pipeline) were collectable at observation time. They do not by themselves establish collection of every backing array, materialized tree or formerly owned object, nor explain aggregate heap-accounting behavior. Do not infer either a leak or precise reclamation size from these flat readings.

`materializedMinusBaseline` and `reusableVmMinusBaseline` remain plausible comparative heap-used proxies under the same JVM configuration, input lifetimes and stage definitions: the former shows approximately 0.18 million versus 16.7 million bytes for 16 MiB sparse strings and 0.75 million versus 83.8 million bytes for sparse records. They support a large observed memory-footprint difference, with the above interpretation limits. Release deltas should stay descriptive; they are not independently proven graph sizes. Allocated bytes, post-GC heap used and peak process RSS must remain separately labelled. Exact retained graph claims would require stronger object-level evidence, outside this measurement slice.

Interpretation evidence binding (SHA-256):

```text
8aca111f934245f781642d2912e964c582f842752dc13752791a0fe7e1cd6e74  vm-benchmarks/src/jmh/kotlin/io/github/ehlyzov/branchline/vm/SelectiveJsonPipelineProbe.kt
05c4c0b6315baf6ad5ee132dc5175b6ac33a5c1e2b46ec9b5849e92be90e92a4  development/evidence/vm-json-pipeline/memory-summary.csv
```
