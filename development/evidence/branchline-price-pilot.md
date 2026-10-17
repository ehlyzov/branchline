---
status: Implemented
depends_on: ['planning/branchline-next-stage']
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-10-17
changelog:
  - date: 2026-10-17
    change: "Recorded local price pilot, three-engine correctness and negative sensitivity evidence; external readiness remains open."
---
# Local price-ingest pilot and comparison evidence

Recorded 2026-10-17 Europe/Moscow (initial tool clock 2026-10-16T22:02Z; precision correction 2026-10-16T22:05Z), from working tree based on `ce58599df24a16cb7ffd1f5d4165bdbf84d90ad0`, including the concurrent next-stage changes. This is local synthetic evidence, not release or production acceptance.

## Reproduce and observed results

| Gate | Exact command and directory | Observed result |
| --- | --- | --- |
| Pure transformation comparison | Repository root: `./gradlew :jsonata-benchmarks:priceIngestComparison` | PASS after precision-review correction, exit 0. 65 named inputs: 50 business inputs executed by each of three required engines (150 actual transform evaluations), plus fifteen shared wire-schema rejections checked for each baseline. Every declared result matched losslessly parsed explicit independent expected JSON. Exact-decimal comparator sensitivity passed (`0.10000000000000001` differs from `0.1`). |
| Wrong-output sensitivity | Repository root: `./gradlew :jsonata-benchmarks:priceIngestComparison -PpriceIngestCorruptEngine=kotlin` | Required failure, exit 1: `kotlin / ordinary-create` expected accepted event, actual injected `INPUT_SCHEMA` rejection. No incorrect baseline was filtered out or credited as a win. |
| Stateful service/codegen/contract pilot | `ktor-service/`: `./gradlew -Pbranchline.useLocal=true test` | PASS, exit 0. JUnit XML: 24 tests, zero failures/errors/skips, including the new `PricePilotReplayIT` and all existing service suites. |
| Whitespace/format check | Repository root: `git diff --check -- jsonata-benchmarks ktor-service` | PASS, exit 0. |

The comparison printed: local Branchline JVM interpreter from current repository source; Kotlin 2.3.0; genuine Dashjoin JVM JSONata `com.dashjoin:jsonata:0.9.10`; JVM `21.0.11+10-LTS`, Mac OS X `aarch64`. The loaded JSONata jar origin was checked to end in `/jsonata-0.9.10.jar`; Maven artifact cache content identifier was `91ffe439d7792ac05d5b01081b41029678216039`. This is the Java port, not the original JavaScript JSONata runtime.

Corpus: [`cases.json`](../../jsonata-benchmarks/src/jmh/resources/price-ingest/cases.json), SHA-256 `2d5bd05511ac844fc3fec56fcd903319da03c9cbc28caadc3319e7f20f18ebca`. These are 65 examples for **one** common specification, not 65 authoring tasks. Error order, explicit nulls, missing defaults, wrong wire types, fractional/negative/maximum cents, ack choices, preserved interior whitespace and v2 currency requirements are visible in fixtures and expected outputs.

During first execution, the independent oracle rejected three wrong-wire-type examples in all baselines: the reused benchmark input parser coerced numeric/boolean-looking JSON strings into primitives. The new gate now uses Jackson parsing that preserves JSON types for both inputs and explicit expected objects; existing general benchmark behavior was not changed. The corrected gate passed all initial 60 cases. Independent review then identified that default Jackson numeric parsing could round decimal input and oracle values before validation. The gate now enables `USE_BIG_DECIMAL_FOR_FLOATS` for both tree and untyped parsing. Five added raw numeric fixtures reject `1000000000.00000001`, `1.00000000000000001`, `0.10000000000000001`, `9007199254740993` and `1e400` as `INPUT_SCHEMA` at the common boundary, before any Double conversion. Supported numeric values must be finite as Double and compare equal as exact decimals after `BigDecimal.valueOf(number.toDouble())`. The corrected 65-case positive gate passed, and the corrupt-output gate again exited 1. This is an explicit interoperable-domain restriction; arbitrary-precision inputs are not credited to any baseline. Ktor code was unchanged by this correction, so its prior 24-test result remains applicable. Comparison only ignores map ordering and numeric JSON formatting; it preserves absent/null, array order and type differences.

## Delivered scope

- [Common specification, exact rules and limits](../../jsonata-benchmarks/src/jmh/resources/price-ingest/PROTOCOL.md), semicolon-free Branchline transform, real JSONata expression, ordinary Kotlin implementation and required-engine Gradle task.
- [Local service guide](../../ktor-service/docs/LOCAL_PILOT.md) with composite local dependency mode, executable API example, expected responses and reset behavior.
- A seven-request service replay asserts final price `0.02`, final transaction `tx-003`, three history entries/events and six acknowledgements after zero-price creation without ack, one-cent update, stale input, changed duplicate, excessive scale, negative input and equal-time ordering.

The common wire adapter validates JSON types and a declared finite numeric domain preserving decimal values through Double-to-decimal roundtrip; the 50 accepted wire shapes execute business rules independently in every engine. Native contract inference/enforcement and arbitrary-precision capabilities are outside this comparable integer-cents profile. The service keeps its existing decimal-string Branchline contracts and generated DTOs. Persistence, duplicate detection, ordering and outbox effects remain Kotlin-hosted; the pure comparison does not exercise or claim them.

A contour trigger fired because a new reproducible verification workflow and pilot evidence now exist. The integration owner updates the service verification/navigation records. No product runtime code changed in the pilot slice, and no root build/settings, interpreter, VM or CLI source was edited by this slice.

## Limits and P2

The standalone server/curl demonstration was documented but not manually run; HTTP behavior was exercised through real Ktor `testApplication` routes, with local Branchline dependency resolution and actual build-time contract extraction/DTO generation. No deployment, persistence durability, outbox delivery, representative production replay, load benchmark, two-week observation, independent integrator trial, paid model run or blind authoring experiment occurred. Existing historical performance documents are not new measurements.

No speed, user-effort or AI advantage follows from these green functional gates. External P2 still requires two independent integrators, representative replays/change scenarios, at least two weeks of observed operation, explicit operational/rollback limits, and a separately authorized fair authoring experiment with 30 independent tasks, hidden tests, equal tools/model/budget, repetitions and uncertainty reporting.
