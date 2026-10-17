---
status: In Progress
depends_on: ['ai/ai-friendly-dsl', 'quality/conformance-suite', 'perf/jsonata-benchmarking']
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-10-17
changelog:
  - date: 2026-10-17
    change: "Delivered and independently accepted local P0/P1 core hardening, price pilot and three-engine comparison; external P2 remains open."
---
# Next stage: trustworthy transformations and a measurable pilot

## Decision and scope

Branchline remains Alpha. The next stage delivers a bounded, testable JVM transformation profile and a reproducible price-ingest pilot, then measures whether contracts and diagnostics reduce development and maintenance effort. It does not declare production readiness or competitive superiority from a green test suite.

Implementation is delegated to GPT-6.1 Sol agents with high reasoning. The parent owns planning, integration, independent acceptance and this record. Agents own disjoint files and report native test evidence. Existing untracked `deep-research-report*.md` files are user-owned and untouched. The initial local implementation excluded deployment, publishing, paid model experiments, production data, a new orchestration language and a VM rewrite. The user subsequently explicitly authorized the controlled model experiment recorded below.

## Priorities and acceptance gates

| Priority / owner | Deliverable | Ready when |
| --- | --- | --- |
| P0 / core agent | Complete registered POWER/SQRT behavior; document numeric domains, errors and JVM/JS limits | No TODO in registered numeric functions; normal/domain-error cases tested on JVM and JS; precision assertions check values, not only wrapper types; unsupported precision is stated explicitly |
| P0 / core agent | Deterministic AI authoring subset | Inspect rejects NOW/RANDOM in reachable expressions and function bodies with structured diagnostics; pure transforms remain compatible; documentation distinguishes subset compatibility from a security sandbox |
| P0 / VM agent | Explicit VM fallback policy without replay after execution failure | Compilation fallback is explicit/configurable; strict VM path can fail without fallback; an execution error is propagated without replaying host effects; execution-path evidence and regression tests cover these cases |
| P1 / pilot agent | Local price-ingest pilot and three-engine comparison | Reproducible command executes real Branchline, real JSONata and Kotlin against the same independent expected outputs; at least 30 named input cases cover ordinary, boundary and error behavior; failed/unavailable engines fail the gate rather than becoming wins |
| P1 / parent | Integrated verification and reproducible evidence | Appropriate root JVM/JS conformance, interpreter, VM and CLI checks pass; pilot checks pass; service contour, numeric/subset docs and this record match actual behavior |
| P2 / external pilot owner | Operational pilot | Two independent integrators can install and modify transforms; representative replay data and change scenarios pass; at least two weeks of observed operation with no unexplained data corruption; operational limits and rollback procedure recorded |
| P2 / experiment owner | Authoring and maintenance advantage | Blind holdout tasks, equal tool/model budget, independently checked outputs, and recorded time/repair metrics support a scoped claim; uncertainty and losing cases are reported |

P0 gates precede pilot acceptance. Work may proceed in parallel on disjoint files. External P2 adoption remains a follow-on evidence requirement. The subsequently authorized bounded authoring experiment is now complete, without establishing superiority.

## Core boundaries

- JVM is the authoritative exact-number pilot runtime. JS BigInt/BigDec currently have Long/Double backing; do not promise arbitrary precision or universal parity. Pin exact supported cases and known limitations in tests/docs. Replacing JS big-number storage is a separate follow-up unless the bounded work proves it necessary.
- Define POWER/SQRT domain semantics before implementation; do not silently downcast exact values while calling the operation exact.
- Deterministic subset excludes ambient clock/randomness and orchestration. User-defined function bodies must not bypass the check. Execution resource limits and capability isolation remain necessary before accepting arbitrary untrusted programs; this pilot executes repository-controlled programs only.
- A VM error after execution starts must not rerun the interpreter on the same environment. Compilation fallback can remain an explicit compatibility mode. Strict execution must expose failures honestly.
- Keep the grammar stable. Update public semantics and conformance when behavior changes.

## Pilot: price change ingestion

Use the existing independent `ktor-service` example: validate incoming price changes, normalize payloads, decide acceptance, and compose outbound acknowledgement/update payloads. Kotlin retains persistence, duplicate detection, ordering and outbox effects. Branchline owns pure transformations and their contracts. Clock and event identifiers are supplied as inputs.

Use synthetic fixtures only. Cover valid creation/update, zero/negative prices, exact cents, excess scale, missing/blank fields, acknowledgement choices, duplicate/stale events and contract evolution where the chosen executable surface supports them. Clearly separate transform comparison from Ktor stateful behavior; do not attribute Kotlin-hosted idempotency to a transformation engine.

Local delivery includes runnable checks and a recorded result. Real integration adoption and operational observation remain P2.

## Comparison protocol

1. Commit a common problem specification, fixtures and independent expected results before drawing conclusions. At least 30 cases are input examples, not 30 independent authoring tasks; report both counts accurately.
2. Run genuine Branchline JVM, JSONata with an explicitly recorded implementation/version, and ordinary Kotlin. Kotlin/JSONata receive equivalent validation/error handling, and exact money uses an agreed interoperable representation (e.g. integer cents) for the comparable track. Keep arbitrary-precision and inferred-contract capabilities in a separately labelled track.
3. Treat absent/null, numeric formatting, rejection behavior and output shape explicitly. Do not normalize away business differences. Unsupported, incorrect or unavailable cases are visible and excluded from speed ratios.
4. Correctness is the first gate: all declared supported cases match independent expected results. Add sensitivity checks proving deliberately incorrect output fails validation.
5. Performance follows correctness: separate preparation from warm execution, consume outputs, disclose JDK/machine/versions/data sizes, use the existing JMH infrastructure for publishable measurements. A local functional run is not evidence of speed superiority.
6. Authoring experiment: prepare 30 independent tasks across mapping, validation, joins, aggregation and schema changes; reserve holdout inputs from authors. Give each language the same model, examples/documentation access, time and repair budget. Randomize task order and use at least three attempts per task for AI; record correct-at-first-attempt, correct-within-budget, wall time, tokens/cost where available, repair count and hidden-test failures. Do not execute paid experiments implicitly.
7. Proposed continuation gate: no correctness regression versus baselines, and either >=20% lower median accepted-solution time or >=25% fewer repair iterations, with paired uncertainty analysis and a benefit visible in more than one task category. These are predeclared targets, not observed results. If intervals overlap no benefit or the advantage is confined to one niche, narrow the claim and product scope.

## Authoring task catalogue for the follow-on experiment

These are 30 independent assignments, separate from executable pilot input fixtures. Each assignment needs at least one visible example and three withheld boundary inputs before an experiment starts. Authors receive the same business specification and expected error policy in each language. A reviewer writes holdouts independently and seals them before generation; tests committed alongside solutions today are not blind evidence.

| Category | Five assignments | Required holdout themes |
| --- | --- | --- |
| API mapping | A01 rename/nest customer fields; A02 flatten order lines; A03 select optional address; A04 normalize enum labels; A05 build outbound acknowledgement | absent vs null, empty collections, unexpected optional fields |
| Validation | A06 validate price cents; A07 collect required-field errors; A08 constrain quantity bounds; A09 validate mutually dependent fields; A10 reject invalid enum transitions | zero/negative/boundary numbers, multiple errors, wrong kinds |
| Lookup and grouping | A11 enrich SKU from catalogue; A12 retain missing lookups explicitly; A13 group orders by customer; A14 deduplicate by supplied revision; A15 join warehouse availability | missing/duplicate keys, stable ordering, empty groups |
| Aggregation | A16 line totals in cents; A17 discounts with stated rounding; A18 aggregate accepted/rejected counts; A19 choose latest supplied timestamp; A20 build stock summaries | overflow boundary, ties, all-rejected input, exact rounding |
| Contract changes | A21 add optional source metadata; A22 rename a required field; A23 make an optional field required; A24 extend output enum; A25 change nested list element shape | old/new inputs, compatibility classification, downstream output guarantees |
| Maintenance and repair | A26 repair absent/null bug; A27 repair singleton-list bug; A28 repair incorrect rounding; A29 add an ack opt-out; A30 split validation from event composition | preserve old behavior outside change, plausible wrong implementation, negative tests |

Timebox each attempt to 20 minutes and at most three inspect/test repair rounds, identically across languages. Capture a per-attempt row: taskId, language, model/version, attempt/seed, docs supplied, start/end time, initial/final correctness, repair rounds, token/cost availability and reviewer notes. Use paired task-level summaries so repeated attempts do not masquerade as independent tasks. Report setup/onboarding time separately; report failed attempts rather than dropping them from median-time analysis. Record both time-to-success and success-within-budget. Publish per-task results and paired bootstrap intervals before asserting the predeclared continuation thresholds.

## Rollout order

- Local implementation now: P0 fixes, executable P1 pilot, common correctness gate, native regression checks and documented limitations.
- Pilot week 1: two integrators reproduce setup and implement a small contract change without author assistance; record every intervention and installation blocker.
- Pilot week 2–3: shadow replay against the existing implementation using an agreed representative dataset; compare outputs/rejections, resource use and rollback behavior. No automated production rollout is authorized by this plan.
- After correctness/operability acceptance: run the authoring catalogue with held-out tests and the agreed model budget, then decide to expand, narrow or stop productization.

## Verification and evidence

Use commands in `development/service/VERIFY.md`. Agents run focused checks; the parent runs the integrated final gate once after material changes. New pilot commands are added to VERIFY only after verified locally. Do not overwrite failed evidence with an unsupported success claim.

Baseline from the preceding assessment: root conformance JVM reported 356 tests with one skipped; JS reported 104, no failures, at commit ce58599. This is historical baseline, not acceptance for this change.

## Execution ledger

- Plan recorded before repository implementation.
- Core implementation delivered: POWER/SQRT domains and exact JVM arithmetic, explicit JS limits, recursive deterministic-subset checks, JVM/JS regressions and a playground example. Independent review found and closed a mutable callable-shadowing bypass; writes are tracked conservatively within each function/transform.
- VM implementation delivered: configurable compilation-only fallback, strict rejection, execution reports and no replay after runtime failure. Integration exposed eight previously masked conformance failures. Fixes preserve nullable variables/shadowing, resume correctly after implicit function return, defer unused named-function compilation and adapt VM-generated public exception categories without replacing host exceptions. Conformance expectations were retained.
- Pilot/comparison implementation delivered: existing Ktor price-ingest service, seven-request stateful replay, and 65 pure comparison inputs. Fifty business inputs execute on each of three real engines; fifteen wire-domain rejects exercise the common adapter. Independent review caught string coercion, decimal rounding before validation and interior-whitespace divergence; corrected adapters/expressions and adversarial cases now pass. See [recorded pilot evidence](../evidence/branchline-price-pilot.md).
- Independent acceptance: final integrated gate passed after the corrections above. Local P0/P1 acceptance is complete; the overall plan remains In Progress because external P2 is unmeasured.
- Controlled authoring experiment subsequently completed under explicit user authorization: 30 tasks, three languages, three repetitions, 270 GPT-6.1 Sol/high sessions with equal budgets and hidden tests. Branchline 90/90, JSONata JVM 87/90, Kotlin 90/90; no general correctness-and-efficiency advantage established. See [experiment report](../experiments/authoring-2026-10-17/REPORT.md). The measurement is complete; the competitive-advantage gate and external adoption remain open.

## Final local acceptance — 2026-10-17

The parent ran this integrated command after the final runtime changes, exit 0 (`BUILD SUCCESSFUL`, 48 seconds):

```sh
./gradlew :interpreter:jvmTest :interpreter:jsTest :vm:jvmTest :vm:jsTest :conformance-tests:jvmTest :conformance-tests:jsTest :cli:jvmTest :cli:jsNodeTest :jsonata-benchmarks:priceIngestComparison docsBuild :vm-benchmarks:jmhJar
```

Native JUnit XML evidence at acceptance:

| Surface | JVM tests | Node.js tests | Failures / errors | Skips |
| --- | ---: | ---: | ---: | ---: |
| Interpreter | 208 | 57 | 0 | 0 |
| VM | 89 | 23 | 0 | 0 |
| Conformance | 359 | 109 | 0 | 1 existing JVM skip |
| CLI | 41 | 5 | 0 | 0 |
| Independent Ktor service | 24 | — | 0 | 0 |

The root total is 891 tests, with one skip. The separately verified Ktor composite build adds 24 tests (915 combined, 914 passed). Applicable unchanged Gradle tasks were up-to-date; this is not a clean rebuild. The comparison again passed 65 named inputs, and the intentionally corrupted Kotlin run previously exited 1 as required. Documentation and playground assets built successfully. Browser tests were skipped because Chrome was not configured; no browser UI or standalone server/curl session was manually exercised.

Because the VM instruction loop changed, the parent also built the existing JMH jar and ran a bounded bytecode smoke on JDK 21.0.11, Mac OS X aarch64:

```sh
/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin/java -jar vm-benchmarks/build/libs/vm-benchmarks-v0.11.0-SNAPSHOT-jmh.jar 'io.github.ehlyzov.branchline.vm.VMTransformBenchmark.typicalTransform' -p dataset=small -wi 1 -i 1 -w 100ms -r 100ms -f 1 -foe true -rf json -rff /tmp/branchline-vm-smoke.json
```

Exit 0, one fork completed without an execution failure. The existing benchmark directly calls `VM.execute`; it does not use interpreter fallback. This deliberately short run only establishes benchmark executability, not performance stability or an advantage/regression ratio. Full comparative performance measurement remains open.

The service contour was refreshed with `TZ=Europe/Moscow bin/refresh_contour.sh`; its audit passed with no failures or warnings. `git diff --check` passed.

No deployment, release, commit or push was performed. JVM exact-number semantics, the restricted JS number profile and compilation-only VM fallback are the documented compatibility boundaries. External adoption, production reliability, untrusted-code isolation and productivity superiority remain unverified.
