# Local authoring evaluator

This research-only adapter runs the locally accepted Branchline JVM interpreter, Dashjoin JSONata **0.9.10**, or Kotlin **2.3.0**. It does not call authors/models, discover task files, or add business logic. Product/build sources are unchanged. The interpreter JAR is copied into the private runtime on first preparation and its SHA-256 is checked on each evaluation. Dependency hashes and paths are in the private runtime `config.json`.

Prepare once before starting the author clock:

```sh
python3 development/experiments/authoring-2026-10-17/evaluator/evaluate.py --prepare
python3 development/experiments/authoring-2026-10-17/evaluator/selftest.py
```

The first preparation runs the repository `./gradlew` with `runtime.init.gradle` and compiles `EvaluatorHost.kt` with the pinned cached compiler. Generated runtime artifacts default to `/tmp/branchline-authoring-private/runtime`; set `AUTHORING_RUNTIME` to use another private directory. Candidate compilation and execution require macOS `sandbox-exec` and fail closed if it is unavailable. Their filesystem allowlist contains system runtime files, the exact dependency JARs, the trusted host classes, and only the current evaluation directory. Other submissions, private fixtures, and the repository cannot be read. Network access is denied, writes are limited to the current directory, and subprocess execution is limited to the Java binary under the inherited sandbox. Java receives a minimal environment rather than inherited credentials/options. The host deletes the copied fixture file before candidate class initialization. These restrictions supplement the orchestrator's separate author-process sandbox and source audit; candidates must not attempt filesystem, network, reflection, process, or host-protocol access.

Invocation (paths can be absolute):

```sh
python3 development/experiments/authoring-2026-10-17/evaluator/evaluate.py \
  --language branchline --source /absolute/submission.bl \
  --cases /absolute/public-cases.json --output /absolute/result.json
```

Use `jsonata` or `kotlin` for the other engines. A fixture is a JSON array of `{ "input": ..., "expected": ... }` objects. The importable Python interface is `evaluate(language, source, cases, output)`, returning the same exit status as the CLI.

The output envelope is `{compile_ok, cases:[{ok,actual,error}], error?, compile_seconds, execute_seconds}`. Compiler/runtime errors and mismatches return status **0**; infrastructure failures return **2** and `infrastructure_error: true`. Compiler errors have an empty case list. Exceptions are per-case failures. A 30-second evaluation timeout fails all cases and is recorded explicitly; Kotlin compilation has a 60-second limit. Bootstrap has a separate 300-second bound and runs outside the author clock. Successful output numbers are copied directly from Jackson's JSON output, without a Python float round trip.

Jackson **2.20.1** parses decimal inputs as `BigDecimal`; ordinary integral inputs use `Int`/`Long`, with `BigInteger` when needed. Comparison preserves missing keys, JSON null, Boolean/string/number distinctions, array order and array cardinality. Numeric comparison uses exact decimal values (so `1` and `1.0` agree, while `"1"` and `1` do not). Map key order does not matter. No epsilon or numeric-string coercion is applied. JSONata's undefined root result is an error and differs from explicit JSON null. JVM JSONata's own numeric operations retain its native semantics.

`compile_seconds` measures engine preparation plus external Kotlin compilation; `execute_seconds` measures the case loop. JVM launch and runtime bootstrap are excluded from these fields. The orchestrator must record complete author/evaluator wall time independently; these fields are not runtime benchmark evidence.

## Fixed author surfaces

Branchline: submit a complete semicolon-free program with exactly one `TRANSFORM Main { ... }`, using the `input` binding. Standard library functions and top-level `FUNC` declarations are available. The evaluator runs the interpreter, not the VM, and performs semantic analysis. It does not enforce inferred contracts. Native `OUTPUT` supports objects/list-of-objects: root scalar outputs are rejected and root `OUTPUT []` returns null. Nested JSON arrays/scalars/null remain available. Example:

```branchline
TRANSFORM Main {
  OUTPUT {value: input.value}
}
```

JSONata: submit only an expression, with the input at `$`. Use native JSONata array construction when cardinality must be preserved. No helper business functions or external bindings are installed. Example:

```jsonata
{"value": value}
```

Kotlin: submit top-level `fun transform(input: Any?): Any?` with any ordinary helper declarations/imports, no package or `main`. The host adds `import java.math.*` and `import kotlin.math.*` as two initial lines. Compiler line numbers therefore include these two lines. Inputs contain ordinary `Map<String, Any?>`, `List<Any?>`, `Number`, `String`, `Boolean`, and null. Return JSON-compatible values using the same ordinary types; parsing/serialization belongs to the host. Example:

```kotlin
fun transform(input: Any?): Any? =
    mapOf("value" to (input as Map<*, *>)["value"])
```

## Adapter verification

`selftest.py` uses only synthetic, non-scored data. In each engine it checks eleven wrapped identity values including `{}`, `[]`, singleton lists, zero, null, false, string/number distinction, nested data, an exact large integer and an exact decimal. Four deliberate mismatches check missing-vs-null, string-vs-number, array order, and Boolean-vs-number. Invalid source must produce compiler diagnostics in all engines. Extra JSONata checks distinguish undefined from null and preserve root null. An adversarial Kotlin probe attempts to read a sibling submission, private sentinel, and repository source, using both `/tmp` and `/private/tmp` spellings; it must fail on every read. It must also fail to connect to a live loopback listener, and the copied expected-fixture file must already be gone.

No service contour trigger fired: this adds a research evaluator under `development/` without changing service APIs, integrations, topology, or production verification commands. The parent experiment record and `development/INDEX.md` provide navigation.
