# Independent native benchmark and acceptance review

Reviewed 2026-10-17 by the separate execution-review agent. This review reads source, generated JMH harnesses and existing evidence; it does not run builds, tests or workload benchmarks. Product implementation and runtime acceptance remain pending.

## Method assessment

The unchanged repository `VMTransformBenchmark` and `InterpreterTransformBenchmark` are appropriate paired execution benchmarks for this slice. Both prepare programs once at trial setup and pass results to JMH Blackhole. VM calls `VM.execute` directly, so compatibility fallback cannot produce its result. Interpreter uses prepared `Exec`; both retain numeric-site preparation across invocations. No parsing, compilation or I/O is timed.

The three program texts match: fixed nested path reads; filtered order-id comprehension plus first-order SKU comprehension; scalar total reduction with order count. Dataset builders match for `collectionType=hash`: small 10 orders × 5 items, medium 100 × 10, large 500 × 25, with identical deterministic field formulas. Both build a fresh HashMap environment containing the same input aliases before each invocation. The selected slice therefore exercises the intended plain-map fast path naturally; it does not demonstrate equivalent speed on arbitrary map implementations.

Both generated `typicalTransform_avgt_jmhStub` methods call `resetEnv()` before the `System.nanoTime()` timed bracket and accumulate only the benchmark-method interval into `realTime`. Invocation setup allocation still contributes to the iteration GC profiler counters. Report normalized bytes allocated per operation, including that setup; never call it retained memory, peak heap or RSS. For sub-microsecond path reads, per-invocation clock overhead is a material common harness floor.

`run_native.py` selects precisely the three unchanged methods and all three dataset sizes, overrides both SampleTime annotations to AverageTime, uses one thread, two independent JVM forks, identical heap/JVM and warmup/measurement settings, and `-prof gc`. Interpreter must keep `collectionType=hash`. Preserve exact commands, raw per-fork results, source hashes and jar/class hashes. Builds, profiling and benchmark processes must remain sequential. Source inspection found no tracked changes in either native benchmark module at review time.

The prior native run is useful historical evidence, but the optimization claim should use the preserved baseline jar and rebuilt changed jar under the same current conditions. Short sequential VM-then-interpreter runs have warmup, order and thermal uncertainty. Use longer warmed confirmation for a claimed material improvement; compare confidence intervals and per-fork results. Do not infer a general VM win from one workload or aggregate unlike units of work.

## Correctness gate

Existing `NativePairValidation.java` instantiates the actual benchmark states, compares independently created input/environment values, calls both engines directly, and checks all nine outputs against separately calculated expected values. Re-run it against the final jars, and ensure VM/Compiler/Exec class hashes identify the intended source revision. Its final input check compares the two input trees with each other; it is not an independent proof of immutability if both engines made the same mutation. For the final gate compare each post-run input with a freshly built expected input (or a separate deep baseline). The three selected programs are read-only on input by source inspection.

## Regression gates for the selected implementation

- No-tracer execution: result, final environment, errors and host-effect count must stay unchanged. A counting/throwing trace-metadata map can directly prove no Enter/Exit/PathWrite metadata access without an applicable tracer.
- Dynamic tracing: host code can install, replace or remove `Debug.tracer`; custom tracer callbacks can also change it. Resolve it at the same observation boundaries as before. In particular, re-check after a host call before Exit events and for subsequent instructions; preserve constructor-tracer precedence. Test both activation and deactivation.
- Environment observability: preserve the old-value get-before-put behavior for custom/delegated maps and subclasses with observable access. Exact HashMap/LinkedHashMap class checks are safer than `is` checks. Exercise null old values, watched variables and provenance PathWrite events with tracing. Custom map getters/setters can change `Debug.tracer`, so avoiding stale tracer decisions around their calls matters too.
- Tracing fidelity: existing `VMTracingTest` covers Enter/Exit node order and static/dynamic PathWrite old/new values; `MetricsVMTest` covers opcode counts. New focused common tests should cover the dynamic boundaries above on JVM and Node.
- Execution modes and failures: run VM common JVM/Node tests, including `VMRuntimePreparationTest` (callbacks, authority changes, errors and snapshots), collection-ownership suites, `SuspendVMTest`, `SnapshotCompletenessVMTest`, and try/catch/function tests. Keep numeric-overflow and conformance gates because the optimized loop still traverses numeric execution. No compiler/opcode/serialization change is part of this slice.

## Baseline instruction fact

`baseline-opcodes.json` was generated by the existing jar's `VMOpcodeSnapshotKt`, before this implementation, without a build or timing run. It confirms 38 static instructions / 17 ACCESS_STATIC operations for pathExpressions, 36 static instructions for arrayComprehension, and 22 static instructions for typicalTransform. The scalar reduction loop executes eight instructions per order. These counts contextualize per-instruction overhead; they are not a timing attribution or a speedup result. The selected slice should preserve these instruction counts.

## Review status

Method accepted for the bounded no-tracer optimization, with the limitations above. Final implementation inspection, regression outputs and repeated timing/allocation evidence are required before acceptance. This file and the opcode JSON are the only repository artifacts written by this reviewer; parent orchestration owns the already-modified development index.

## Slice 1 implementation inspection

Independently compared `VM.kt` against the preserved `/tmp/branchline-vm-hotpath/VM.before.kt`, rather than treating the much broader pre-existing working-tree diff as this slice. No blocking behavior regression found in this bounded diff. Reviewed source SHA-256: VM.kt `4fea7d8220392e87b408406405f04992711c2884548c5c4f3a66c11d392a2d49`; VMTraceFastPathTest.kt `159ed0339f159b81d99ff47ac3a40d19019900e23e2520a41a41007352f188ad`.

The ordinary-map flag is refreshed by the environment setter on execute/begin, callee entry, normal return, callback-error unwind and TRY unwind. Snapshot restore enters through begin and reconstructs call-frame maps that pass through the same setter on restoration. Its initial false value is conservative; execution initializes the real environment before stores. The `privateMutationAllowed` assignment in execute uses the same exact-class predicate as before; begin still disables private mutation.

For exact HashMap/LinkedHashMap environments with no tracer, storeVar now performs only the required write. It has no host or user callback between the tracer check and that plain-map write. Custom maps, delegated implementations and subclasses take the original get→put→Let→PathWrite sequence, with tracer resolution retained inside each emission helper. A Let callback that disables tracing therefore still suppresses PathWrite. No tracer is cached across instructions, host calls, Enter/Exit emission or environment replacement. Existing run-loop STEP events and exception paths are unchanged.

New common tests directly check ambient activation/deactivation inside a host call, including Exit on the activating opcode; explicit-tracer precedence; opcode counts; custom get-before-put behavior without tracing; activation from custom get and put with correct old/new values; and disabling PathWrite from a Let callback. These are meaningful behavior tests rather than assertions of implementation spelling. Existing VMTracingTest remains the provenance/path regression suite. This reviewer did not run tests or benchmarks; parent-owned runtime gates and timing evidence are still required for final acceptance.
