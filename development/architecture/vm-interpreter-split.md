---
status: In Progress
depends_on: []
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-10-17
changelog:
  - date: 2026-10-17
    change: "Added explicit compilation-only fallback policy, strict execution, and observable per-call VM path; runtime failures never replay the interpreter."
  - date: 2026-06-10
    change: "Added G6 fallback-risk note: public benchmark rows still cannot distinguish VM bytecode execution from interpreter fallback."
  - date: 2026-02-01
    change: "Migrated from research/splitvm.md and added YAML front matter."
  - date: 2026-02-01
    change: "Aligned to input binding wording."
---
# VM/Interpreter Split — Progress Report


## Status (as of 2026-10-17)
- Stage: Partial.
- `compileStream` selects interpreter or VM backends. Compatibility mode permits interpreter fallback after pre-execution compilation failure; strict policy requires bytecode.
- Next: fill missing VM instructions and independently validate successful bytecode execution before publishing VM benchmark claims.

## Reliability update (2026-10-17)

- `VMExec` and `VMEval` expose `VMFallbackPolicy.COMPILE_FAILURE` (compatibility default) and `NONE` (strict). Only pre-execution compilation errors can choose the interpreter. Compilation is deferred until invocation; `VMExec` caches its result.
- A failure after VM execution starts propagates without interpreter replay. This includes host exceptions and lazy function compilation failures. Existing host effects/environment mutations are not rolled back. `VMEval` no longer swallows errors into `null`.
- Both adapters expose `lastExecution` and an optional `onExecution` observer. A terminal report records path, success, compilation failure and execution failure. Paths distinguish bytecode, compilation fallback, explicit interpreter selection and strict compilation rejection. The observer cannot trigger replay; an execution exception retains priority over observer failure.
- `VMFactory` forwards policy/report options and honors `useVM = false`. `compileStream` accepts `vmFallbackPolicy` and `onVMExecution` for its VM engine; interpreter runners emit no VM evidence. `VMConfig.fallbackOnError` was never connected to these adapters and is deprecated.
- Common regression tests cover host side effects executing once on failure, prior effects/environment mutation before failure, runtime `NotImplementedError`, strict rejection before effects, successful bytecode, compilation fallback, failed fallback, disabled VM and observer failure. Native verification evidence is recorded in the next-stage plan by the parent.
- Integrated acceptance also exposed present-null loads, implicit function-return resumption and public exception-type differences previously hidden by replay. The bounded follow-up fixes those VM paths, preserves nullable lexical shadows, and translates VM-generated argument/state failures at adapters while retaining raw host-error identity and VM causes. Existing conformance remains unchanged. The focused follow-up gate passed `:conformance-tests:jvmTest :vm:jvmTest :vm:jsTest`: conformance JVM 359 tests (one existing skip), VM JVM 89 and VM JS Node 23, no failures. Nine common boundary regressions cover null/missing loads, nullable shadows, optional/nested/callback returns, public VM causes, host exception identity and lazy function compilation.
- This closes the adapter replay risk and exposes path data; benchmark reports still need to consume it and validate outputs. Aggregate compile metrics do not establish bytecode execution, correctness, or performance.

## Historical G6 fallback risk (2026-06-10)

- At that date, `VMExec` caught VM compilation/execution failures and reran the interpreter. This could replay effects after a partial VM run, and benchmark consumers could not tell whether a `branchline-vm` row used bytecode or fallback execution.
- G6 required benchmark smoke completed successfully, but `performance/case001` still showed VM slower than the interpreter, and multiple representative rows looked optimized away or otherwise non-credible.
- The split remains partial for publication purposes until VM rows expose execution-path metadata: bytecode executed, compile fallback, disabled/error, and validation state. Execution fallback has since been removed because replay is unsafe.


## Current Shape
- `compileStream` lives in the VM module and accepts an `ExecutionEngine` enum. On the JVM it can return either an interpreter-backed runner or a `VMExec` that compiles IR to bytecode before execution. Both paths still seed the environment with a cloned `row` map.【F:vm/src/commonMain/kotlin/v2/ir/StreamCompiler.kt†L31-L74】【F:interpreter/src/commonMain/kotlin/v2/ExecutionEngine.kt†L1-L7】
- There is no standalone compiler module; callers reach directly into the interpreter/VM entrypoints (`compileStream`, `VMExec`), so we still lack a stable public API surface for downstream consumers.【F:vm/src/commonMain/kotlin/v2/ir/StreamCompiler.kt†L31-L83】【F:vm/src/commonMain/kotlin/v2/vm/Integration.kt†L28-L90】
- `VMExec` acts as an adapter: it compiles IR lazily, caches bytecode or compilation failure, and permits compilation-only interpreter fallback under an explicit policy. Strict execution removes that fallback at the adapter boundary, while the VM module still depends on interpreter IR/runtime types.

## Gaps Before a Clean Split
1. **Shared IR utilities.** VM compilation still reaches into interpreter internals (`IRNode`, `Exec`, `TransformRegistry`). Extract the shared pieces into a neutral module so the VM can depend on stable interfaces instead of the interpreter implementation classes.【F:vm/src/commonMain/kotlin/v2/ir/StreamCompiler.kt†L31-L63】
2. **Environment contract.** Runners continue to mirror the entire input into `row`. Defining a platform-level environment builder (with the `input` binding) will keep future DSL changes from leaking into the VM layer.【F:vm/src/commonMain/kotlin/v2/ir/StreamCompiler.kt†L63-L74】
3. **Compilation surface.** Provide a small public façade (new module or package) that hides the VM/interpreter wiring. Right now callers are expected to jump straight into `compileStream`, `VMExec`, or test fixtures, which is not sustainable for external consumers.【F:vm/src/commonMain/kotlin/v2/ir/StreamCompiler.kt†L31-L83】【F:vm/src/commonMain/kotlin/v2/vm/Integration.kt†L28-L90】
4. **Fallback pruning.** Promote missing instructions or runtime hooks to first-class features. Use strict adapter policy and successful bytecode reports to measure actual VM coverage; compilation fallback remains the compatibility default.

## Suggested Next Steps
- Formalize a public compiler API (e.g., `BranchlineCompiler.compile(transform, engine = …)`) in a small façade module/package that delegates to the VM/interpreter internals.
- Move shared IR builders, tracing hooks, and host-function registries into common source sets so both interpreter and VM can depend on them without cyclic references.
- Introduce an environment builder in the future `platform` module that the VM/interpreter runners consume, aligning the execution contract across engines.
- Consume the VM execution report in benchmark/report tooling; reject failed, disabled or compilation-fallback runs from VM performance claims.
