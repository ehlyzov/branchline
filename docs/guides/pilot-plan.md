---
title: Operational Pilot toward 1.0
---

# Operational pilot toward 1.0

This is a plan, not a completed operational trial. Branchline remains Alpha.
The pilot asks whether two independent integrators can install, maintain and
operate a bounded JVM transformation workload with explainable results and a
working fallback. It does not require Branchline to outperform every alternative.

## Workload and boundary

Use **price-ingest validation, normalization and event composition** first.
The repository already has a reproducible comparison with independent expected
outputs and a separate Ktor integration replay. Retain those as admission checks;
they are synthetic tests, not representative field evidence.

Start with repository-controlled transforms on JVM, a pinned runtime and source
revision, and the ordinary interpreter. Evaluate ordinary VM separately, with
compilation fallback disabled and actual execution mode recorded. Generated-loop
backends and selective JSON remain outside the pilot's required path.

The pure comparison uses integer cents, bounded to 0 through 1,000,000,000, with
an explicit wire adapter. The Ktor example uses a different decimal-string
contract. Choose one profile per integration and record conversion and rejection
rules before accepting data; do not silently compare the two as equivalent.
Follow [Numeric Semantics](../language/numeric.md), including intermediate bounds.
JVM decimal workloads must supply decimal text before conversion to floating point.

Kotlin/host code owns ordering, idempotency, persistence, delivery and retries.
Branchline owns pure transformation. The demonstration service uses in-memory
stores; it is not a durable production reference. Shadow outputs must not send
acknowledgements, mutate the primary store or publish real events.

## Owners and admission record

Assign the following people before starting the observation clock. Names and
production datasets are deliberately pending; the repository cannot supply them.

| Role | Required responsibility and artifact |
| --- | --- |
| Integrator A | Independent installation and deployment in one host integration; record commands, assistance and maintenance exercise. |
| Integrator B | A separately owned installation/integration; same exercises without copying A's answers. Two runs by the language authors do not qualify. |
| Domain owner | Approve field meanings, missing/null behavior, time ordering, error precedence and independent expected results. |
| Operations owner | Set resource ceilings, alerts, incident response, retention and rollback procedure for each integration. |
| Release reviewer | Freeze candidate identity and inspect evidence; make the go/no-go decision independently of implementation authors where possible. |

For each integration, complete a run record containing:

- Runtime/artifact version and checksums, repository commit, transform/input
  schema versions, JVM/OS/CPU/heap, host configuration and execution engine.
- Data provenance, permission to use it, redaction, corpus hash and coverage of
  real sizes, skew, missing/null fields, malformed values, duplicates and ordering.
  Keep sensitive records outside the public repository; preserve reproducible
  redacted fixtures and hashes.
- Expected outputs/errors authored from the domain specification **before**
  execution. Preserve array order and distinguish null, missing and JSON types;
  normalize only irrelevant key order/number spelling without precision loss.
- Named owners; start/end times; completed numeric-profile and capability review.
- Explicit values for max input/output bytes, records per request, concurrency,
  queue size, process memory, timeout, allowed p95/p99 latency and baseline
  regression budget. These are chosen from host capacity and workload requirements
  before replay; they are not universal Branchline limits or measured SLAs.

An incomplete record blocks live shadow observation. Enforce limits in the host,
including process termination where cancellation cannot interrupt computation.
Subset inspection is not a sandbox. Do not accept arbitrary untrusted programs.

## Execution and evidence

| Priority / stage | Action | Acceptance evidence |
| --- | --- | --- |
| P0 — candidate admission | Run the frozen JVM/JS compatibility/numeric/onboarding gates; run local price comparison and its deliberate wrong-output control. | Correctness gates have no failures; the corrupt baseline fails. Save exact commands, versions and logs. |
| P0 — installation | A and B install independently from the pinned distribution, run introductory examples, then integrate their own representative fixtures. | Both reproduce expected output; record time, assistance, failed attempts and changes. Author assistance is allowed but disclosed. |
| P0 — replay | Replay representative valid and invalid inputs with independent business oracles. Include maximum admitted sizes, parallel jobs, malformed input and resource-limit breaches. | Zero unexplained output differences, silent drops or duplicate effects; every input has a recorded terminal disposition; exceeded limits fail predictably without host corruption. |
| P0 — fallback drill | Exercise the procedure below before live observation. | Recover the baseline within the predeclared recovery-time objective, reconcile all in-flight IDs and verify no double delivery. |
| P1 — maintenance | Each integrator adds the v2 currency requirement and one independently specified field/default change. | Held-out cases pass; record elapsed effort, assistance and rollback/redeployment steps. Existing public fixtures are practice, not blind holdouts. |
| P1 — observation | Observe both integrations for at least **14 consecutive days** on representative traffic, including peak/load windows. | Daily traffic and error summaries, resource trends, baseline comparisons and incident dispositions. No unexplained corruption, breached agreed limits or unclosed blocker. |
| P1 — decision | Review both records and incidents against the frozen thresholds. | Signed go/no-go record with evidence links and explicitly excluded use cases. |

Zero traffic does not count as representative observation. A correctness or limit
failure stops admission to promotion; fix it, rerun the relevant replay and rollback
gates, and restart the 14-day window for the changed candidate. Do not edit
thresholds after seeing results to turn a failure into a pass.

Record request correlation ID, transform/runtime/schema version, actual engine,
outcome/error category, input/output sizes, queue and execution time. Aggregate
throughput, latency quantiles, allocation rate, heap after GC, process RSS and
restarts separately. Never infer retained memory from allocated bytes. Error text
and payloads may contain input values; use redacted diagnostics and bounded retention.

## Rollback drill

1. Retain the previous host implementation, its source, artifacts, schema and
   configuration. Test it on the same immutable replay before rollout. Keep
   Branchline source for recompilation; do not rely on portable compiled artifacts.
2. Run in shadow mode with a switch back to the baseline. Pause new work or route
   it to the baseline, then drain or explicitly quarantine in-flight records by ID.
3. Disable Branchline execution, restore the pinned baseline and replay quarantined
   records through the host's idempotency mechanism. Retry only classified transient
   host failures; syntax, contract and numeric/domain errors require correction.
   VM runtime failure must not trigger automatic interpreter replay of effects.
4. Compare final state and event/ack disposition with the independent oracle and
   baseline. Record elapsed recovery, lost/duplicate counts and rejected records.
   A process restart of the in-memory demo is not a persistence recovery test.
5. Re-enable the candidate only after the incident is understood and the admission
   gates pass. A schema migration needs its own backward-compatible deployment or
   restoration plan; an engine switch cannot undo irreversible host side effects.

## Comparison and release decision

If speed or memory is a pilot objective, run Branchline interpreter, strict ordinary
VM, Kotlin and pinned Dashjoin JVM JSONata on the **same admitted pure transform**.
Keep input parsing/output serialization, numeric validation, data and concurrency
equivalent. Report cold preparation separately from reused execution and measure
the full pipeline. Use JMH with warmup, multiple forks, consumed outputs and GC
allocation profiling; save machine/runtime versions, raw distributions and an
uncertainty interval. Randomize or reverse engine order. Measure process peak RSS
and retained heap separately. All engines must pass the oracle; a missing or
incorrect engine invalidates the comparison rather than improving another score.
The Java JSONata port must not be labelled as the original JavaScript runtime.

No new timing result or general authoring advantage is claimed here. The existing
30-task study did not establish general superiority. Maintenance exercises provide
qualitative adoption evidence; a new productivity claim needs new withheld tasks,
equal budgets and independent adjudication.

Completion of the repository contracts and examples permits a **Beta/RC candidate
review**, not automatic 1.0. Stable release additionally requires both field trials,
the full [release gates](release-readiness.md), a verified supported-surface
inventory, and explicit acceptance of unresolved limitations. A failed optional
speed objective may narrow the recommended workload; an unresolved correctness,
compatibility or rollback blocker is a no-go.

## Local starting points

From the repository root:

```sh
./gradlew :jsonata-benchmarks:priceIngestComparison
./gradlew :jsonata-benchmarks:priceIngestComparison -PpriceIngestCorruptEngine=kotlin
```

The second command **must fail** with an injected `kotlin / ordinary-create`
mismatch. From `ktor-service/`, run `./gradlew -Pbranchline.useLocal=true test`.
See repository `jsonata-benchmarks/src/jmh/resources/price-ingest/PROTOCOL.md`,
`ktor-service/docs/LOCAL_PILOT.md` and
`development/evidence/branchline-price-pilot.md` for existing scope and evidence.
