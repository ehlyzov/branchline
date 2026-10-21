---
status: Implemented
depends_on: ['planning/playground-ux-2026-10-17']
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-10-18
---
# Bounded contracts, executable onboarding and operational pilot

The user requested the next step toward 1.0 using GPT-6.1 Sol agents and independent parent acceptance. Current Alpha status remains unchanged. No 1.0 publication or real operational pilot is implied.

## Scope and ownership

1. Compatibility agent: candidate 1.0 support/versioning/deprecation matrix in release-readiness and migrations. Separate current guarantees from future commitments and experimental surfaces; cite repository evidence. Own those two guides only.
2. Numeric agent: validate numeric semantics against source, document a bounded cross-runtime profile, add meaningful exact-value/boundary regressions where needed. Own numeric guide and new numeric contract tests; no runtime semantic changes without coordination.
3. Onboarding agent: add a bounded set of executable introductory examples with independent expected results and enforce doc/fixture consistency using existing JVM/JS infrastructure. Own getting-started guide, new fixtures/tests and narrowly necessary build/CI integration after announcing paths.
4. Parent: pilot plan/runbook, index/navigation/contour, cross-review and final execution evidence. Agents do not edit shared index, service contour, root README or navigation; no commits/push/deploy by agents.

## Priorities and acceptance

- P0: no unsupported compatibility or numeric claims; preserve explicit JVM/JS precision differences and source-recompile migration boundary. Candidate promises have scope, exclusions, evidence and change policy.
- P0: introductory examples run with independently specified outputs on advertised runtimes, through interpreter and strict VM where claimed. Deliberate stale-doc or wrong-output control fails the gate.
- P1: pilot has two independent integration roles, dataset admission and oracle policy, resource limits chosen before replay, >=14 consecutive observation days, rollback drill and explicit go/no-go decision. Unknown names, real workloads and thresholds remain pending; synthetic evidence is not field evidence.
- P1: parent verifies changed promises against implementation, runs meaningful JVM/JS checks, documentation build and contour audit; records actual tests and unresolved limitations.

No new broad performance or productivity claims, runtime redesign, deployment, or historical benchmark replay is part of this slice.

## Source-audit correction

The numeric audit found JVM decimal FLOOR/CEIL truncate fractions instead of
rounding toward the advertised direction. The numeric agent owns a bounded
BigNumJvm correction and positive/negative/exact-value regressions; this repairs
the documented contract without introducing syntax or a new numeric model.
The same audit identified the asymmetric Long.MIN_VALUE * -1 overflow check:
wrapped division can incorrectly accept the wrapped product. A symmetric special
case and exact-value regressions for both operand orders are included in the
numeric agent's bounded correction in Numeric.kt.
The first integrated contract run failed the independent product oracle
1000001 * 1000001: ordinary VM constant folding returned a wrapped Int while the
interpreter was correct. The numeric agent will replace raw integer constant-fold
arithmetic in VM Compiler.kt with the canonical numeric helpers and add Int/Long
boundary regressions. Preserve runtime timing of invalid-operation errors.
Node then exposed INT(1.5) truncation via Kotlin/JS primitive type checks, contrary
to the existing strict-INT documentation. The numeric agent owns a bounded
StdStringsModule integral/finite check and regression values. Independent review
also found that folded BigInt constants exceed the existing artifact i64 codec:
skip such folding and retain runtime arithmetic, without changing artifact format.
The onboarding agent owns a new JVM artifact roundtrip regression for this case.
The same JS primitive classification defect affects DEC(number): an independent
Node CLI probe serialized DEC(1.5) as "1". Include a narrow StdNumericModule
classification fix using existing numericKindOf, preserving explicit values and
JVM integer/float distinctions. Verify fractional values and serialization. This
closes the observed documented-conversion defects, not a new broad runtime audit.
Parent full CLI acceptance then reproduced Node compiled DEC(1.5) failing reload:
BytecodeIO marked the fractional primitive as i64 due to the same JS type check.
The onboarding agent owns the minimal primitive i64/f64 classification correction
and a common JVM/Node codec/execution regression. Existing format tags remain;
arbitrary BigInt/BigDec persistence is still excluded and recorded separately.

## Acceptance

Parent local acceptance passed: 1221 successful test executions, one existing
skip, no failures/errors; 24 independent JVM/Node source/compiled CLI cases.
See `development/evidence/one-zero-contracts-2026-10-18.md` for exact commands,
negative findings, repairs and remaining stable-release/pilot gates.
