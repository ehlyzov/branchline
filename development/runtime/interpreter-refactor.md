---
status: Implemented
depends_on: []
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-01
changelog:
  - date: 2025-02-14
    change: "Step 1 complete: ExecutionCaps + Env, Exec owns expression evaluation."
  - date: 2025-02-14
    change: "Step 2 complete: caps threaded, guards added, host fn metadata."
  - date: 2025-02-14
    change: "Step 3 complete: FUNC/lambda semantics updated and tests added."
  - date: 2026-02-01
    change: "Migrated from research/interpreter.md and added YAML front matter."
---
# Interpreter Refactor Plan


## Status (as of 2026-01-31)
- Stage: Implemented.
- Steps 1-3 are marked complete in the progress log.
- Next: no pending items recorded.

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