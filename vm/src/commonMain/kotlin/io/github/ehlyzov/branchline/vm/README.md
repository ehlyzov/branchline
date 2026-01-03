# Stack-based Virtual Machine for Branchline DSL

This directory contains a stack-based virtual machine (VM) for the Branchline DSL. It compiles IR to bytecode and executes it with a focus on performance and clearer execution semantics compared to the interpreter.

## Architecture Overview

The VM consists of several key components:

### 1. Instruction Set (`Instruction.kt`)
- **Stack Operations**: PUSH, POP, DUP, SWAP
- **Arithmetic**: ADD, SUB, MUL, DIV, MOD, NEG
- **Comparisons**: EQ, NEQ, LT, LE, GT, GE
- **Logic**: AND, OR, NOT, COALESCE
- **Variables**: LOAD_VAR, STORE_VAR, LOAD_SCOPE
- **Objects/Arrays**: MAKE_OBJECT, MAKE_ARRAY, ACCESS_STATIC/DYNAMIC
- **Control Flow**: JUMP variants, FOREACH loops (TRY/CATCH opcodes present)
- **Special Branchline ops**: OUTPUT, MODIFY, APPEND
- **Functions**: CALL, CALL_LAMBDA, RETURN
- **Debug**: TRACE, BREAKPOINT, LINE, NOP

### 2. Virtual Machine Core (`VM.kt`)
- Stack-based execution engine with safety checks (overflow/underflow)
- Environment management for variables and simple scoping
- Arithmetic operations supporting Branchline number types (I32, I64, IBig, Dec)
- Object and array operations with type checking
- Control flow: jumps, conditionals, FOREACH support
- Lambda closures with captured environment and invocation
- Debug/trace integration with the Branchline tracer and instruction counters
- Snapshot/restore (partial): bytecode/env/stack serialized; call/try stacks not yet serialized

### 3. IR-to-Bytecode Compiler (`Compiler.kt`)
- IR node compilation for LET/SET/APPEND/APPEND VAR/MODIFY/OUTPUT/IF/FOR EACH/TRY-CATCH/RETURN/ABORT/EXPR STMT
- Expressions: literals, identifiers, unary/binary ops (with short-circuit), access, arrays, objects, lambdas, invoke, if-else expr
- Array comprehension compilation (accumulator + loop)
- Label management and jump resolution
- Notable gaps: SharedStateAwaitExpr; generic SET/APPEND/MODIFY through nested access paths

### 4. Integration Layer (`Integration.kt`)
- VMExec: VM runner with automatic fallback to interpreter on compile/exec failure
- VMEval: expression evaluator with interpreter fallback and trace signals (FALLBACK Call/Return)
- Utilities: basic validation and performance estimation heuristics
- Runtime/Factory: configuration and metrics (timing, compilation stats)

### 5. Test Suite and Examples
- VM unit tests for arithmetic, variables, objects/arrays, conditionals, stack safety
- Lambda VM tests via ExecutionEngine.VM
- Trace integration tests (instruction counts, rows)
- Integration demo: `VMLoop.kt` (CLI runner) and snapshot helpers

## Current Status

### Implemented
- Defined instruction set and core VM loop with tracing support
- Arithmetic/logic/comparison operators for Branchline numeric types
- Variables: load/store/scope read; environment overlays for closures
- Objects/arrays: creation, static/dynamic access, concat, append (runtime)
- Control flow: conditional/unconditional jumps; FOREACH iteration
- Lambdas: compile-time template + runtime closure capture/invocation
- Array comprehensions compilation and execution
- OUTPUT building; basic abort (RETURN implemented via abort)
- Snapshot/restore for bytecode/env/stack; pretty-printer
- VMExec integration with interpreter fallback; basic validation/estimation utils

### Partially Implemented
- TRY/CATCH runtime — implemented; retriable catches route exceptions and support fallbacks.
- Snapshot completeness — callStack, tryStack, and loopStack are serialized and restored. Suspensions inside TRY and function calls are supported via snapshot handoff.
- Integration polish — VMEval fallback with tracing; VMFactory exposes basic metrics.

### Known Gaps vs Tests/Interpreter
- Try/Catch tests currently run on the interpreter only
- Host function invocation works; user-defined function invocation via FuncDecl is not wired in VM
- Some IR write paths (SET/APPEND/MODIFY nested) fall back to interpreter in VMExec

## Roadmap (Planned Work)

Priority is ordered roughly by enabling breadth of IR coverage and test parity.

1) Generic write paths — done
- Implemented `compileAccessExprForSet/Append/Modify` for nested access paths.
- Added array index updates in VM `SET_STATIC/SET_DYNAMIC` and runtime `MODIFY`.
- Tests: `io.github.ehlyzov.branchline.vm.WritePathsVMTest` covers nested SET, dynamic keys, array index, APPEND with INIT, and MODIFY.

2) MODIFY runtime — done
- `VM.modifyObject` implemented to produce updated map immutably.

3) TRY/CATCH runtime — done
- Implemented `startTry/endTry/handleCatch` with try stack capturing stack depth, start PC, and retry attempts.
- Exceptions in VM route to CATCH; retries jump back to try start; otherwise fallback runs.
- Tests: `io.github.ehlyzov.branchline.vm.TryCatchVMTest` covers success, fallback, and retry count.

4) SharedStateAwait expression
- Decide representation: compile `SharedStateAwaitExpr` to `CALL` of a host fn (e.g., `__AWAIT(resource,key)`), or add a dedicated instruction.
- Enforce language rule already in tests: no AWAIT directly in IF condition; require LET then IF.
- Add targeted tests that run on VM with mocked host await.

5) FuncDecl invocation
- Implemented: CALL resolves user-defined functions; function bodies compile to bytecode and execute with overlay environment; suspensions propagate to the caller and are snapshotted with call stack.
- Support user-defined functions: either precompile FuncDecl bodies to lambdas and bind in env, or extend `CALL` to resolve compiled FuncDecl bytecode.
- Add tests for function calls (params, recursion limits, closures).

6) Snapshot completeness — done
- Serialize/restore `callStack`, `tryStack`, and `loopStack` for pause/resume.
- Tests: `SnapshotCompletenessVMTest` covers suspensions in TRY, in loops, and inside function calls. 

7) Integration polish — done
- `VMEval` falls back to interpreter with explicit trace events.
- `VMFactory` exposes metrics (compile/execute timing, counters); `Compiler.Metrics` accessible for set/append paths.

8) Optimization passes (post-correctness) — in progress
- Peephole opts implemented for PUSH/POP and SWAP/DUP cancellation.
- Constant folding for numeric literals and string concatenation.
- Specialized instructions: OUTPUT_1/OUTPUT_2 for small arities (implemented).

9) Coverage and parity
- Expand VM test suite to mirror interpreter tests where feasible.
- Add property-based tests for arithmetic/compare across numeric types.

## Usage Examples

### Basic VM Usage
```kotlin
// Create bytecode
val instructions = listOf(
    Instruction.PUSH(10),
    Instruction.PUSH(20),
    Instruction.ADD
)
val bytecode = Bytecode(instructions)

// Execute
val vm = VM()
val result = vm.execute(bytecode) // Returns 30
```

### Integration with Existing System
```kotlin
// VM-enabled executor with fallback
val vmExec = VMFactory.createExecutor(
    ir = irNodes,
    eval = fallbackEvaluator,
    useVM = true
)

val result = vmExec.run(environment)
```

### Performance Analysis
```kotlin
val estimate = VMUtil.estimatePerformanceBenefit(irNodes)
println("Expected speedup: ${estimate.expectedSpeedup}x")
println("Recommendation: ${estimate.recommendation}")
```

## Performance Expectations

Initial results demonstrate speedups on arithmetic/loop-heavy workloads. Further gains depend on completing write paths, try/catch, and enabling simple optimizations. Use `TraceReport.instructionsPerRow` and `VMUtil.estimatePerformanceBenefit` to guide adoption.

## Integration Strategy

1. Prefer `ExecutionEngine.VM` in tests where implemented; otherwise rely on `VMExec` fallback.
2. Incrementally migrate features; keep parity with interpreter semantics and trace output.
3. Use validation/estimation helpers to choose VM vs interpreter per path.

## Testing

Run the VM tests with:
```bash
./gradlew test --tests "io.github.ehlyzov.branchline.vm.*"
```

The test suite includes:
- Basic arithmetic/logic/comparison
- Stack safety; variable ops
- Objects/arrays creation/access/concat/append
- Control flow and array comprehensions
- Lambda closures and invocation
- Trace integration and instruction accounting
- Interpreter fallback integration

## Architecture Benefits

1. **Performance**: Stack-based execution is generally faster than tree-walking
2. **Optimization opportunities**: Bytecode can be optimized more easily than AST
3. **Memory efficiency**: Reduced object allocation during execution
4. **Extensibility**: Easy to add new instructions and optimizations
5. **Compatibility**: Designed to integrate seamlessly with existing code
6. **Debugging**: Full tracing and debugging support maintained

## Future Optimization Opportunities

1. **JIT Compilation**: Hot bytecode sequences could be compiled to native code
2. **Register Allocation**: Hybrid stack/register VM for better performance
3. **Constant Folding**: Compile-time evaluation of constant expressions
4. **Dead Code Elimination**: Remove unreachable bytecode
5. **Peephole Optimizations**: Local instruction sequence optimizations
6. **Specialized Instructions**: Domain-specific optimized instructions

This VM implementation provides a solid foundation for significantly improving Branchline DSL execution performance while maintaining full compatibility with existing code.

## Performance Plan (Next Improvements)

Near-term (low effort, high ROI)
- Expand peephole optimizations: remove more redundant sequences (e.g., RETURN_VALUE;POP), minimize DUP/SWAP shuffles, collapse JUMP chains, specialize ACCESS/SET for hot static keys.
- Extend constant folding: boolean logic, comparisons on constants, COALESCE with null, simple associative arithmetic chains.
- Small-object builders: add `MAKE_OBJECT_1/2` and `MAKE_ARRAY_1/2`; use in compiler for frequent small shapes.

Medium-term (moderate effort, strong ROI)
- Local variable slots: compile identifiers to `LOAD_LOCAL/STORE_LOCAL` using per-frame arrays. Keep a separate global scope for `row`/shared bindings. Removes string map lookups.
- Single-VM function calls: resolve `FuncDecl` to bytecode indices and call within the same VM by pushing a `CallFrame` (no `lambdaVm.execute`).
- Output fast paths: extend specialized OUTPUT ops to 3–4 fields; maintain expression-output as stack-only.

Runtime/data-structure optimizations
- Faster stacks: replace `ArrayDeque` with primitive array+pointer stacks for operands and frames.
- Immutable container writes: reuse backing arrays where possible; manual copy for lists/maps to cut iterator overhead.
- Loop iteration: avoid materialization for lists; only realize non-lists/iterables when needed or during snapshot.

Control-flow and dispatch
- Instruction dispatch micro-opts: move to enum-based opcode with compact operand structs; switch on `Opcode` instead of `is` checks.
- Group hot opcodes to aid JVM branch prediction (LOAD/STORE/ADD/ACCESS first).

Host calls and AWAIT
- Monomorphic host calls: pre-resolve host fn indices at compile time and emit `CALL_HOST idx` to avoid map lookups.
- AWAIT: ensure host waits are non-blocking and suspension-friendly; batch resumes to reduce overhead.

Compiler write-path improvements
- Path write specialization: emit SET_OBJ_NAME / SET_ARR_INDEX fast ops; `APPEND_INIT` specialized for append-with-init.
- Coalesced rebuilds: for nested SET chains, reconstruct minimal subtrees instead of stepwise rebuilds.

Measurement and rollout
- JMH micro-benchmarks: arithmetic loops, foreach with writes, small outputs, function calls. Track instruction histograms to find hotspots.
- Guardrails: preserve snapshot and TRY/CATCH semantics; add coverage for new ops; feature flags to toggle optimizations.

Expected impact
- Local slots + single-VM calls typically reduce runtime by 2–3x on function-heavy and variable-heavy code.
- String concatenation fusion (multi-part `BUILD_STR`) can reduce allocs and improve throughput on URI-heavy transforms.

## Bytecode Dumping (Non-JMH)

To inspect and compare VM bytecode for a complex, JSONata-like transform, run the test `io.github.ehlyzov.branchline.vm.BytecodeDumpTest`.

Options via system properties:
- `-Dbranchline.dumpBytecodeDir=language/build/bytecode-dumps` sets output dir (default is `language/build/bytecode-dumps`).
- `-Dbranchline.dumpIterations=3` sets how many iterations/files to write (default 3).

Each run produces files like `BranchlineDump-iter1.bc.txt`, `BranchlineDump-iter2.bc.txt` that you can diff.
