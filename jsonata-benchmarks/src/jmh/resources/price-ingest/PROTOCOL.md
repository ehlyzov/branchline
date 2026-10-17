# Price-ingest functional comparison

This is one common transformation specification, implemented once in each language, with 65 named input examples. It is **not 65 independent authoring tasks**. It measures output correctness only. The explicit `expected` objects in `cases.json` are the oracle; neither Kotlin nor a consensus of engines supplies expected outputs at runtime.

Run from the repository root with JDK 21 and the repository wrapper:

```sh
./gradlew :jsonata-benchmarks:priceIngestComparison
```

The required engines are the local Branchline JVM interpreter (`BranchlineCompiler.compileInterpreter`, not the VM), ordinary Kotlin (build version 2.3.0), and genuine Dashjoin JVM JSONata `com.dashjoin:jsonata:0.9.10`, pinned from Maven Central. Dashjoin is a Java port, not the original JavaScript implementation. Its loaded jar origin is checked and printed. No configured local JSONata checkout is required. Resolution, compilation, evaluation, unavailability and mismatches fail the command. Existing optional-engine JMH skip rules do not apply here.

An intentionally corrupt engine must make the whole gate fail:

```sh
./gradlew :jsonata-benchmarks:priceIngestComparison -PpriceIngestCorruptEngine=kotlin
```

Expected: nonzero exit and `kotlin / ordinary-create` mismatch. The ordinary gate additionally checks that its first independent expected output differs from a known wrong output, and that losslessly parsed `0.10000000000000001` and `0.1` remain unequal under the actual comparator.

## Common specification

Input is a JSON object. A shared **wire-type and numeric-domain boundary adapter** is applied identically before every engine: present non-null identifier/time/currency fields must be strings, `priceCents`/`schemaVersion` numbers, and `needAck` boolean. Numbers in `priceCents` and `schemaVersion` must also be finite when converted to Double and preserve their exact decimal value through `BigDecimal.valueOf(number.toDouble())`. Parse the corpus tree, input and expected outputs losslessly with Jackson `USE_BIG_DECIMAL_FOR_FLOATS` before this check; only then convert supported decimal inputs to Double for the shared engine boundary. This explicitly excludes numeric values that would silently round (including `1000000000.00000001`, `1.00000000000000001`, `0.10000000000000001`, and `9007199254740993`) and values outside finite Double range such as `1e400`. This criterion is decimal roundtrip preservation, not a claim that binary floating point exactly represents every decimal fraction. Wrong types, unsupported numeric values or a non-object root yield exactly `{accepted:false, errors:["INPUT_SCHEMA"], ack:null, event:null}`. Fifteen of the 65 examples exercise this adapter; the remaining 50 execute each engine's own business rules. This is not a comparison of native schema enforcement, arbitrary-precision input support or generated contracts. The common numeric restriction is checked before business rules and errors.

The engine-owned rules, in error order, are:

1. Trim boundary whitespace (preserve interior whitespace) in `transactionId`, `warehouseId`, `skuId`, `source`, `effectiveTime`, `storedAt`, `eventId`. Missing, null or blank values append `<UPPERCASE_FIELD>_REQUIRED` in that field order. Times are opaque nonblank strings in this bounded profile; no ISO parser or UUID validation is claimed.
2. Missing/null `priceCents` appends `PRICE_REQUIRED`. Otherwise append `PRICE_NEGATIVE` for a negative number, `PRICE_FRACTIONAL` for non-integral cents, then `PRICE_LIMIT` above 1,000,000,000. All applicable errors are retained. Integral JSON numbers such as `1234.0` are valid. The comparable money domain is integer cents, not decimal dollar conversion or arbitrary precision.
3. Missing `needAck` defaults to true. Explicit null appends `NEED_ACK` and suppresses acknowledgement.
4. Missing `schemaVersion` defaults to 1. Only numeric values 1 and 2 are valid; other values, including null, append `SCHEMA_VERSION`.
5. Missing `currency` defaults to `USD` for v1 (and invalid versions). Version 2 requires explicit currency. Only exact strings `USD` and `EUR` are accepted; otherwise append `CURRENCY`. These fixtures demonstrate a bounded schema-change scenario, not a deployed service migration.

Unknown input fields are ignored. Output always has exactly `accepted`, `errors`, `ack`, `event`. Accepted means no errors. `ack` is explicit null unless `needAck` is true; otherwise it has trimmed `transactionId`, `ACCEPTED`/`REJECTED` status and the complete ordered errors. `event` is explicit null on rejection; on acceptance it contains only the seven normalized strings plus `priceCents`, effective `currency`, effective `schemaVersion`. Clock and event ID are supplied inputs, so outputs are deterministic.

Map key order and numeric JSON formatting alone are immaterial to equality. Missing keys versus null, ordered arrays, strings versus numbers and business differences remain material. The gate never drops a failing case or engine.

## Relation to the Ktor pilot

This comparable pure transform combines validation, normalization and outbound composition for a synthetic wire profile. It is separate from the service's existing decimal-string Branchline contracts and generated DTOs. It does not execute storage, duplicate detection, stale ordering, outbox effects or HTTP routes. Those belong to Kotlin and are checked separately:

```sh
cd ktor-service
./gradlew -Pbranchline.useLocal=true test
```

`PricePilotReplayIT` covers zero/cents, disabled ack, update, stale rejection, changed duplicate suppression, excess scale, negative price and equal-time transaction ordering with final state/history/outbox assertions. Existing service tests also cover contract codegen, strict validation, batch detection and routes. The service uses in-memory stores and random host event IDs; this local test gate is not an operational pilot.

## Further evidence

No speed, adoption, model, token, cost or authoring advantage is measured. Use existing JMH infrastructure only after correctness, with preparation separated from warm execution, pinned runtime/machine/data and consumed outputs. Arbitrary precision and native contract capabilities need separately labelled tracks. Thirty independent authoring/change tasks, blind holdouts, equal model/tools/budget, repeated attempts and paired uncertainty analysis remain an external experiment. Independent integrator adoption, representative replays and two weeks of observed operation remain P2.
