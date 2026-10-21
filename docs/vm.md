# Branchline VM Overview

Branchline's VM executes stack bytecode emitted from the IR; use this note as a quick reference to opcodes, closures, and runtime behaviour.

## Execution and fallback policy

`VMExec` and `VMEval` default to `VMFallbackPolicy.COMPILE_FAILURE` for compatibility: if compilation fails before execution, the interpreter executes the program once. Set `fallbackPolicy = VMFallbackPolicy.NONE` to require VM bytecode execution. A strict compilation failure is thrown when `run`/`eval` is called, before the environment or host functions are touched. `VMExec` caches compilation on its first call. For repeated expression execution, use `VMEval.prepare(expr)` as described below.

```kotlin
val executor = VMFactory.createExecutor(
    ir = ir,
    hostFns = hostFunctions,
    fallbackPolicy = VMFallbackPolicy.NONE,
    onExecution = { report -> println(report) },
)
val output = executor.run(environment)
val evidence = executor.lastExecution
```

The same options are available on `VMFactory.createEvaluator`. `compileStream(..., engine = ExecutionEngine.VM, vmFallbackPolicy = VMFallbackPolicy.NONE, onVMExecution = { report -> ... })` exposes policy and evidence through the stream runner. With the interpreter engine, no VM report is emitted. Factory `useVM = false` explicitly chooses the interpreter and bypasses VM compilation, even if the fallback policy is `NONE`.

`lastExecution` is initially `null` and records the last completed invocation. `onExecution` receives the same report once per completed invocation. Reports distinguish `VM_BYTECODE`, `INTERPRETER_COMPILE_FALLBACK`, `INTERPRETER_DISABLED`, and `COMPILATION_REJECTED`. Inspect `succeeded` before counting a result as successful; `compilationFailure` explains fallback/rejection and `failure` records the execution or rejected-compilation exception. Supplying precompiled bytecode still reports `VM_BYTECODE`; it does not imply that the adapter compiled it. Reports describe the selected adapter path, not an independent correctness or performance validation. Instances and their mutable reports are intended for sequential use.

Once VM execution begins, **any escaping runtime failure propagates without interpreter replay**, under both policies. Host effects and environment mutations already performed are not rolled back. Failures from interpreter compilation fallback also propagate; `VMEval` no longer replaces errors with `null`. An execution observer runs after the report is saved. Its own error propagates after successful execution, while an existing execution error retains priority (the observer error is suppressed); observer errors never cause replay.

The adapters preserve the public language error categories: VM-generated index bounds errors become `IllegalArgumentException`, and VM-generated missing-variable/function or type errors become `IllegalStateException`. The original `VMException` remains the cause, and the execution report records the emitted exception. Host-thrown exceptions retain identity, including a host's own `VMException`. The low-level `VM.execute` API retains raw VM exceptions. Translation occurs after VM execution, so language `TRY`/`CATCH` sees the original error and no work is replayed. Present-null bindings remain values, including loop items and parameters that shadow captured names; missing low-level variables remain errors. An implicit function return resumes at the instruction after the call, returning null once.

Migration: code relying on automatic recovery after a VM runtime error must now handle that error explicitly. Retry only with an application-defined policy for effects and state. The compatibility default still permits fallback after compilation errors, including unsupported features; use `NONE` and check successful `VM_BYTECODE` reports for VM-only checks and measurements. Lazy compilation of a user-defined function during VM execution counts as execution: failure there also propagates without fallback. The legacy `VMConfig.fallbackOnError` field is unused and deprecated; configure these adapters directly. This policy does not add missing VM instructions, resource isolation, or general interpreter/VM parity.

## Repeated evaluation and preparation

`val prepared = evaluator.prepare(expr)` captures a detached copy of the expression and function ASTs, plus the current host bindings and metadata. `prepared.eval(environment)` compiles lazily once and reuses its VM. Later changes to the original AST or authority maps do not change that handle; call `prepare` again to adopt them. The supplied environment and state captured inside host closures remain live. Reports belong to the prepared handle (`prepared.lastExecution`); its configured observer receives them. Handles are stateful and intended for sequential use.

Implicit `evaluator.eval(expr, environment)` keeps at most one cached expression when no function declarations are supplied and the combined host/metadata authority has at most 32 entries. It validates expression identity and a detached AST snapshot plus those bindings before reuse. Validation costs O(expression size + bounded bindings). Larger authority uses fresh compilation without scanning unused declarations; use explicit preparation for repeated work with a standard library or function registry. With `useVM = false`, implicit evaluation bypasses VM preparation and cache validation entirely.

## Instruction Set Reference

| Category | Opcodes | Notes |
| --- | --- | --- |
| Stack & Locals | PUSH, DUP, POP, SWAP, LOAD_VAR, STORE_VAR, LOAD_SCOPE, LOAD_LOCAL, STORE_LOCAL | Stack/env shuffles; `PUSH` wraps constants or lambda templates. |
| Arithmetic & Comparison | ADD, SUB, MUL, DIV, MOD, NEG, EQ, NEQ, LT, LE, GT, GE | Math and comparisons over primitive and big numeric types. |
| Logical & Nullish | AND, OR, NOT, COALESCE | Short-circuit logic plus null coalescing. |
| Aggregate Access | MAKE_OBJECT, MAKE_ARRAY, ACCESS_STATIC, ACCESS_DYNAMIC, SET_STATIC, SET_DYNAMIC, APPEND, CONCAT | Build/mutate objects or arrays; static ops embed keys, dynamic read them from the stack. |
| Control Flow | JUMP, JUMP_IF_TRUE, JUMP_IF_FALSE, JUMP_IF_NULL | Relative jumps; conditional forms consume the predicate. |
| Calls | CALL, CALL_HOST, CALL_FN, CALL_LAMBDA, RETURN, RETURN_VALUE | Dispatch DSL, host, FuncDecl, or closure bytecode; returns unwind the frame. |
| Output & Mutation | OUTPUT, MODIFY | Emit key/value records (`OUTPUT_1/2`) or batch updates. |
| Iteration | INIT_FOREACH, NEXT_FOREACH | Init and step foreach loops using a stack-held cursor. |
| Error Handling | TRY_START, TRY_END, CATCH, ABORT | Guard regions with retry-aware catches; `ABORT` throws the top value. |
| Diagnostics | TRACE, BREAKPOINT, LINE, NOP | Tracing, breakpoints, source lines, or padding. |
| Suspension | SUSPEND | Serialise snapshot and signal suspension. |

## Lambda Storage

`LambdaTemplate` constants become `LambdaValue` closures that capture the current environment by reference when executed. `CALL_LAMBDA` builds an `OverlayEnv` mixing captures with arguments before scheduling the lambda, and host callbacks receive adapters that delegate to `invokeLambda`.

Environment enumeration flattens nested overlays on demand in one iterative pass, then applies local bindings from outer to inner. It preserves live bindings, null shadowing and key order without caching a stale merged environment. Snapshot serialization still writes each saved frame and pays the resulting payload cost.

## User-Defined Functions

FuncDecl bytecode lives in a lazy `funcBytecode` cache compiled under `Compiler.enforceFuncBody`. `CALL`/`CALL_FN` pop arguments, assemble an `OverlayEnv`, push a `CallFrame` with caller state, and `RETURN[_VALUE]` restores that frame while resuming caller bytecode.

## Optimisations

- Peephole pass removes redundant `PUSH`/`POP`, `DUP`/`POP`, and `SWAP` pairs.
- Static `SET`/`APPEND` fast paths skip rebuilding access chains and log `PathWriteMeta`.
- `useLocals` maps hot variables to indexed slots, mirroring the environment only when needed.
- Prepared programs hold PC-indexed numeric specialization sites. `CALL_HOST` resolves the current host binding directly by name in O(1), without preparing or rescanning an instruction-index table. Arithmetic does not hash the complete bytecode. Calls restore prepared caller state and retain the caller environment reference instead of copying all bindings.
- Each VM keeps a working set of at most 64 prepared programs; changing the root bytecode identity clears that working set. Active call frames retain the state needed for return. Treat low-level bytecode arrays and a VM's function declarations as immutable during its lifetime; create a fresh VM for changed function declarations. Direct host bindings are resolved live; explicit `VMEval.prepare` instead owns a frozen binding map.
- Compiler ownership scopes restore only eligibility bindings changed in the scope, using reversible checkpoints. Entering a branch does not copy the full set of unrelated private roots.
- Fresh literal arrays/objects can use `PRIVATE_ARRAY_APPEND`, `PRIVATE_SET_STATIC`, or `PRIVATE_SET_DYNAMIC` while the compiler proves the root unobserved. Aliases, captures, reads, host effects, unknown roots and nested writes conservatively use copying. This makes eligible accumulation linear without changing shared-value semantics.
- Runtime private mutation additionally requires ordinary synchronous execution with an exact standard `HashMap`/`LinkedHashMap` environment and no tracer. Custom environments, `begin`/`step`, restored snapshots and tracing preserve observable intermediate values by copying. Existing array comprehensions retain their independent stack builder.

Snapshot stack order and cross-call TRY/loop ownership are covered by regression tests. Snapshot support remains limited to the existing serializable value domain: closure/template objects are not preserved as executable values. These optimizations do not add a general snapshot schema migration or universal interpreter parity guarantee.

## Compiled Artifact Compatibility

The next candidate's `blc` writes version 3 with `format: "branchline-vm"`.
Readers retain v1/v2 compatibility within their historical value domain and reject
unknown versions before parsing the embedded program. Versions 1/2 cannot carry
new explicit-number tags. Version 1 also excludes `PRIVATE_*` instructions.

Version 3 adds `bi` and `bd` decimal-text values. JVM roundtrips preserve BigInt
kind/value and BigDec kind/value/scale, including nested containers and supported
VM snapshot state. JS retains its signed-64-bit BigInt and Double-backed BigDec
limits: incompatible values fail instead of silently losing precision. JS does
not retain JVM decimal scale. Ordinary `i64`/`f64` values are unchanged.

Old v2 readers reject v3 by version; strict v1 readers reject its `format` marker.
Old raw bytecode/snapshot readers reject unknown value tags. Preserve source and
recompile with the target reader's compiler for rollback; do not relabel artifacts.
Legacy `f64` values cannot regain precision or BigDec kind that old writers lost.
See [migration policy](guides/migrations.md#compiled-artifact-policy) for the exact
matrix and fixtures. This numeric transport change does not make closure snapshots
portable or freeze all opcode/VM internals as a stable API.
