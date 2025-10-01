# Branchline VM Overview

Branchline's VM executes stack bytecode emitted from the IR; use this note as a quick reference to opcodes, closures, and runtime behaviour.

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

## User-Defined Functions

FuncDecl bytecode lives in a lazy `funcBytecode` cache compiled under `Compiler.enforceFuncBody`. `CALL`/`CALL_FN` pop arguments, assemble an `OverlayEnv`, push a `CallFrame` with caller state, and `RETURN[_VALUE]` restores that frame while resuming caller bytecode.

## Optimisations

- Peephole pass removes redundant `PUSH`/`POP`, `DUP`/`POP`, and `SWAP` pairs.
- Static `SET`/`APPEND` fast paths skip rebuilding access chains and log `PathWriteMeta`.
- `useLocals` maps hot variables to indexed slots, mirroring the environment only when needed.
- `initHostIndexTable` seeds `CALL_HOST` indices to avoid repeated name lookups.
