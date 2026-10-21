---
status: Planned
depends_on: ['planning/one-zero-contracts-2026-10-18']
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-10-18
---
# Operational pilot run record — unfilled template

Copy once for each independent integration. Empty fields block admission; this
template is not evidence of execution. Follow `docs/guides/pilot-plan.md`.

## Frozen admission

- Integration ID / owner / independent integrator:
- Domain owner / operations owner / release reviewer:
- Candidate version / SHA / artifact checksums / transform + schema hashes:
- Baseline implementation / version / rollback artifact:
- Runtime, OS, CPU, heap, engine, fallback policy:
- Numeric and wire profile / conversion and rejection rules:
- Data provenance / permission / redaction / corpus hash / peak coverage:
- Independent oracle author / approval date / immutable expected-results hash:
- Max input bytes / output bytes / rows / concurrency / queue size:
- Timeout / process memory ceiling / p95 + p99 ceilings / regression budget:
- Recovery-time objective / idempotency + in-flight reconciliation mechanism:
- Redacted logs location / retention / alert recipient:
- Admission approver and time (UTC):

## Gates and observation

| Gate | UTC time / candidate | Exact command or procedure | Expected outcome | Actual outcome + evidence | Owner |
| --- | --- | --- | --- | --- | --- |
| Repository compatibility/numeric/onboarding | pending | | pass | | |
| Local three-engine comparison | pending | | pass | | |
| Corrupt-engine negative control | pending | | fail with injected mismatch | | |
| Independent installation | pending | | guide and own fixtures match | | |
| Representative replay and limit breaches | pending | | zero unexplained differences; bounded failures | | |
| Rollback + in-flight reconciliation | pending | | recovery within objective; zero lost/duplicate effects | | |
| Independent maintenance exercises | pending | | held-out oracle match | | |
| Observation window | pending | | >=14 consecutive representative days | | |

Daily record: UTC date, candidate identity, admitted/completed/rejected/quarantined
counts (reconciled), correctness differences, error categories, p95/p99, allocation
rate, heap-after-GC, peak RSS, queue peaks/restarts, incidents and owner disposition.

## Decision

- Start/end timestamps and representative traffic coverage:
- Installation/maintenance effort, assistance and failed attempts:
- Correctness incidents, resolution, rerun and restarted-window evidence:
- Resource threshold comparison / separately labelled optional performance result:
- Remaining limitations and excluded workloads:
- Outcome: pending / no-go / candidate accepted for this integration:
- Reviewer, date, rationale and links:

Two accepted integration records plus the remaining release gates are required
for 1.0 review. A single synthetic replay or a filled checklist without evidence
does not close the operational gate.
