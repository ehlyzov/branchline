# Interpreter Refactor Plan

## Goals
- Define ExecutionCaps and thread them through interpreter execution entry points.
- Unify evaluation into Exec and retire the restricted FUNC interpreter path.
- Update lambda capture semantics to allow outer mutation.
- Enforce OUTPUT/shared restrictions with runtime guards (and optional IR-time checks).
- Add conformance tests for FUNC/lambda rules.

## Plan
- [x] Step 1: Define ExecutionCaps + Env model, refactor Exec to own expression evaluation, and remove execFuncIR.
- [x] Step 2: Thread caps through all entry points, add output/shared/env guards, and add host-fn metadata.
- [x] Step 3: Update tests for FUNC/lambda semantics and restrictions.

## Progress Log
- 2025-02-14: Plan created.
- 2025-02-14: Step 1 complete (ExecutionCaps + Env, Exec now owns expression evaluation).
- 2025-02-14: Step 2 complete (caps threaded, output/shared/env guards, host fn metadata).
- 2025-02-14: Step 3 complete