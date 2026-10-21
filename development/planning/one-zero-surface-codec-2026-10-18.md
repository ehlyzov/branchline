---
status: Implemented
depends_on: ['planning/one-zero-contracts-2026-10-18']
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-10-18
---
# Complete stable surface inventory and exact bytecode numbers

The user requests GPT-6.1 Sol implementation and independent parent acceptance.
Prior uncommitted work is ours and its recorded hashes were verified unchanged.
Current release remains Alpha; freeze the intended 1.0 surface without declaring
unverified APIs stable or repeating an open-ended inventory plan.

## Ownership and acceptance

1. Codec agent owns BytecodeIO, compiled-artifact versioning and focused new/updated
   compatibility tests. Preserve BigInt/BigDec kind and exact JVM representation
   including decimal scale across value/bytecode/snapshot persistence. Define old
   reader rejection and new reader compatibility; fail explicitly on JS values
   outside its admitted representation instead of silently rounding. Do not
   redesign JS arithmetic. Coordinate format decision before final implementation.
2. Stdlib agent owns a complete function registry inventory with arities, domains,
   errors, platform/stability classification and evidence; every registered name
   must be accounted for. Add an executable drift gate and meaningful missing
   cases rather than claiming coverage from existence alone.
3. API agent owns an exact allowlist of stable-candidate embedding/API contracts,
   explicit exclusions of remaining surfaces and consumer/inventory regression
   checks. Include the practical execution entrypoints where supported. No
   whole-jar binary ABI guarantee. Avoid merely postponing signature enumeration.
4. Parent owns shared documentation navigation, migration/release policy, index,
   contour, integration, independent codec probes and final evidence.

Agents use GPT-6.1 Sol High, do not commit/push/deploy and do not run competing
Gradle builds. Report owned paths before editing. Keep existing work intact.

P0 gates: exact JVM value/kind/scale preservation; supported JS value preservation
and explicit rejection; nested containers and actual compile-save-load-execute;
new reader accepts preserved old fixtures without inventing lost precision;
old readers reject unsupported new format before execution. P1: exhaustive
registry/API inventory drift protection, accurate domains and evidence, integrated
JVM/Node checks and documentation build. External pilot remains outside this slice.

## Independent acceptance findings

The saved genuine JVM v2 artifact contains ordinary f64 1.5. New Node bytecode
execution retained it, but pretty/compact JSON output returned 1 because primitive
Byte/Short/Int checks intercepted fractional JS numbers. The codec agent owns the
bounded JsonOutput classification repair and CLI format regression.
The stdlib oracle independently exposed the same primitive-type issue in JS
FLOOR/CEIL: a fractional ordinary number was returned unchanged. The stdlib agent
owns the minimal existing StdNumericModule fix and signed-fraction regressions.
The subsequent JS stdlib run exposed a ROUND(250, -2) mismatch against the
independent half-even result 200. The same agent owns the bounded ROUND repair
and regression before the candidate inventory can be accepted.
API probes also exposed incorrect test assumptions about invalid-JSON exit status
and strict contract errors; reconcile the inventory with actual documented behavior.

## Accepted result

Completed by three GPT-6.1 Sol High agents and independently accepted by the parent.
The API allowlist has 27 exact declaration blocks; the stdlib inventory classifies
all 68 names (50 candidates, 18 experimental) and is protected by executable gates.
Artifact v3 preserves JVM BigInt/BigDec value/kind/scale, admits supported JS values
or rejects explicitly, reads genuine old fixtures and is rejected by an actual
old v2 reader. Acceptance also repaired Node JSON fraction output, FLOOR/CEIL and
ROUND defects found by independent oracles.

Final JVM/Node gate: 1,280 passing executions, one existing skip, zero failures or
errors. Parent probes: 31 CLI compatibility cases and 532 exact JVM numeric checks.
Strict documentation build passed. Full evidence, compatibility limits and raw
artifact locations: `development/evidence/one-zero-surface-2026-10-18.md`.
No publication or completed external pilot is claimed; release platform acceptance
and the operational pilot remain separate gates.
