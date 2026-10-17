# Independent Branchline 1.0 readiness review — 2026-10-17

Read-only review of the repository at the published Alpha candidate
`449f559ece55ef14657c34a56266e472bd22d665`. No product edits, browser use,
test reruns, commit or deployment. Existing test/publication results below are
recorded evidence, not new independent execution. Parent owns the plan and INDEX.

**Decision: suitable for the declared Alpha evaluation; not yet supported by the
evidence required for a stable 1.0 claim.** UX repair improves adoption, but cannot
close the operational pilot or define the compatibility promise by itself.

## Remaining gates with bounded acceptance

| Gate | Concrete evidence and consequence | Smallest credible acceptance |
| --- | --- | --- |
| Independent operation | `docs/guides/release-readiness.md:30,34-38` keeps this gate open. `development/planning/branchline-next-stage.md:29,78-79` requires two independent integrators and at least two weeks. `development/evidence/branchline-price-pilot.md:43` records no external trial, representative production replay, load or rollback observation. Local synthetic correctness is strong evidence for the tested transform, not field reliability. | Two people independently install the published artifacts and perform a schema change/error repair; preserve interventions and failures. Replay an agreed representative dataset; record output/rejection agreement and resource limits. Observe at least two weeks with no unexplained corruption and execute a documented rollback using the previous runtime/source. Choose a narrower declared workload if necessary. |
| Stable supported contract | `docs/guides/release-readiness.md:61-65` explicitly leaves stable compatibility as a future commitment. `README.md:62-66` promises feature stability levels, SLA-style targets and a deprecation plan, but the linked guide provides only Experimental/Stable and no complete freeze/deprecation policy. The inspect envelope already has a narrower additive promise in `docs/language/ai-canonical-subset.md:23,46-50`. | Publish one 1.0 support matrix: language/stdlib, interpreter vs VM, JVM/Node/browser, embedding vs CLI, contracts/diagnostics, compiled artifacts and snapshots. State stable versus preview API, major-version behavior, additive schema rules, deprecation/removal policy and supported runtime versions. Connect each stable promise to a conformance/consumer fixture. Retain source-recompile as the artifact migration policy if that is the intended promise. |
| Numeric profile consumers can trust | `docs/language/numeric.md:111-121` documents JS Long wrapping and Double decimal rounding, including `DEC("0.1") + DEC("0.2")`. `development/service/knowledge-gaps.yaml:33-41` leaves parity open but expressly permits a restricted profile. The browser playground executes JS, so success there cannot demonstrate JVM exact decimal behavior. | Choose JVM exact numbers as the primary stable profile and publish a precisely bounded common/JS profile; assert exact values, conversion boundaries and overflow behavior in that profile. Supported input/arithmetic bounds must be testable by integrators. Reject or explicitly specify unsupported overflow/rounding outcomes rather than implying precision from the `DEC`/BigDec names. Arbitrary precision JS is optional if excluded from the 1.0 promise. |
| Executable first-use documentation | The completed authoring report records invalid Branchline reference syntax that forced a whole-series restart (`development/experiments/authoring-2026-10-17/REPORT.md:65-67`) and calls executable canonical docs P0 (`:77`). Existing JVM/JS playground example tests are useful but are not evidence that every public guide/reference snippet is checked. A successful docs site build checks a different property. | Inventory the onboarding/canonical/agent snippets used in first adoption. Give each complete example an independent expected output and each fragment an explicit executable wrapper; run on its advertised engine in CI. Declare illustrative grammar snippets separately. Repair stale syntax and verify users can complete the main tutorial from published docs. Do not rewrite every document before this bounded path is trustworthy. |
| Operational guidance matches the selected scope | `docs/guides/production-use.md:7-26` is a short set of practices without measured limits, deployment/rollback procedure or effect/retry boundaries. `docs/vm.md:24,28` states that host effects are not rolled back and retry is application-defined. | From the pilot, write a short runbook for the supported JVM/trusted-transform deployment: ownership/concurrency, input/output limits, failure classification, telemetry, effect/retry policy, upgrade and tested rollback. State measured bounds for the chosen workload; there is no need to invent a universal SLO. |

## Work that does not block a scoped 1.0 by itself

- Universal speed or authoring superiority. The 270-session experiment is complete
  and did not establish general superiority (`REPORT.md:44-55,69-79`). A stable,
  useful transformation language does not require beating Kotlin/JSONata on all
  workloads. A new competitive claim needs new evidence, not a repeat of public
  holdouts.
- Default generated VM loops or selective JSON. Their preview contracts and
  tradeoffs can stay explicit. Current release docs already keep ordinary VM
  execution default (`release-readiness.md:46-55`).
- Arbitrary untrusted programs in process. Exclude this use from the stable host
  contract unless capability and resource isolation are delivered. Deterministic
  subset inspection is not a sandbox (`release-readiness.md:36-38` and
  `knowledge-gaps.yaml:42-47`). Operational resource bounds are still needed for
  the trusted pilot.
- General closure snapshot persistence. Keep it outside the stable serializable
  domain instead of promising arbitrary pause/resume portability
  (`docs/vm.md:73,77-81`; `knowledge-gaps.yaml:57-65`).
- New blanket release reruns before any material delta. The recorded Alpha gate
  passed 1,236 tests with one existing skip; publication and public playground
  scenarios passed for the stated commit in
  `output/playwright/release-0.11.0-alpha.1/published-release-notes.md:19-30`.
  These close the Alpha publication gate, not the remaining 1.0 gates.

## Suggested order

Repair the observed playground onboarding/edit/error-recovery UX and verify its
published acceptance path. Freeze the bounded 1.0 contract, then run the external
pilot against that contract while installing the executable first-use docs gate.
Use pilot failures to select the next implementation slice. A Beta/RC milestone
is defensible once the contract and local gates are frozen; promote to stable
after the independent operation/rollback evidence is accepted.
