---
title: API Support Inventory
---

# API support inventory

Branchline v1.0.0-rc.1 is a prerelease candidate. This is the complete **intended 1.0 stable-candidate
allowlist**, frozen against the current source, rather than a claim that a stable
release has shipped. Compatibility concerns Kotlin source consumers on JVM and
Kotlin/JS, with separately built consumer tests. It does not promise whole-jar
binary ABI, generated JavaScript export names/module layout, Java-friendly
synthetic overloads, serialization-plugin generated APIs, or Kotlin compiler
synthetics.

**Default exclusion rule:** every published public declaration, member, overload,
constructor, enum value, package, artifact or capability not explicitly admitted
below is internal or experimental and has no 1.0 compatibility promise. A public
visibility modifier or inclusion in a Maven/npm artifact alone never admits a
symbol. The allowlist includes only the signatures below, their constructor
properties and ordinary Kotlin data-class value semantics. Adding an unrelated
public symbol does not expand this contract.

In particular `BranchlineProgram`, `ArtifactCodec`, `CompiledArtifact`, parser,
lexer, AST (`TransformDecl` included), IR, contract-builder/shape models, compiler,
bytecode/VM classes, execution-engine factories, numeric implementation wrappers,
debug/tracer internals, shared-store providers, prepared/fused/native loop APIs,
experimental JSON pipelines, and CLI option/helper DTOs are excluded. Compiled
artifact wire compatibility is a separate [migration contract](migrations.md),
not a promise of Kotlin API or opcode stability. Language functions have their
own [complete registry inventory](../language/stdlib-support.md).

## Supported embedding behavior

All declarations in the first three groups below are from the `interpreter`
artifact. `BranchlineFacade` and its DTOs are in `io.github.ehlyzov.branchline`.
Despite its source directory, the execution facade and its result DTOs are in
**`playground`**. Use these fully qualified packages when importing them.

- `BranchlineFacade.inspect` accepts a complete program. A null transform name
  selects all transforms; a name selects matching transforms. Defaults omit debug
  metadata, witnesses and normalized source. Parse/semantic failures return
  `success=false`, empty transforms, diagnostics and subset `UNKNOWN`. Lexer errors
  such as an illegal character can throw `IllegalArgumentException`; this method
  does not turn every host failure into a result.
- `BranchlineSubsetCompatibility.COMPATIBLE` means the analyzed program is in the
  canonical AI subset; `INCOMPATIBLE` means supported analysis found constructs
  outside it; `UNKNOWN` means inspection failed. It does not mean a runtime or
  platform compatibility check. Successful inspection may contain subset
  diagnostics, so consumers must read `subsetCompatibility` as well as `success`.
- Inspect result JSON contains `success`, `diagnostics`, `warnings`, `featureUsage`,
  `subsetCompatibility`, `normalizedSource` and `transforms`. `contractsJson`
  returns one merged contract for a single transform, otherwise a `transforms`
  array. A single-transform `inspectJson` additionally copies the merged contract
  fields to the top level. Read structured fields; human message wording, inferred
  contract precision, feature ordering and debug metadata are not frozen.
- `PlaygroundFacade.run` executes in the interpreter. It accepts either a complete
  program or a body, wrapping the latter in `TRANSFORM Playground`. It selects the
  transform named `PLAYGROUND` ignoring case, otherwise the first transform.
  Input must be a JSON object and uses SAFE numeric policy. `outputJson` contains
  formatted JSON on success; on failure it is null, `errorMessage` is populated,
  and parse/semantic errors can carry one-based line/column. It catches execution,
  parsing and host failures into the result. It offers no VM or fallback selector.
- `runWithContracts` uses the same execution path. `off` disables validation,
  `warn` returns warnings and continues, `strict` returns failure on violations.
  Case and surrounding whitespace are ignored; `warning` aliases `warn`, `error`
  aliases `strict`, empty or **unrecognized** modes fall back to `off`. This
  fallback is specific to the playground facade; CLI invalid modes fail with 64.
  `includeContracts` requests input/output contract strings even in `off` mode;
  `includeContractSpans` adds debug spans. `outputFormat` accepts `json`,
  `json-compact`, `json-canonical`, `xml`, `xml-compact`; compact/canonical names
  also accept underscores, matching ignores case, and unknown names fall back
  to pretty JSON. The property remains named `outputJson` even for XML output.
- `PlaygroundFacade.inspect` applies body wrapping and returns inspect JSON fields
  as strings. It catches failures into `PlaygroundInspectResult`. `isBodyOnlyProgram`
  recognizes full programs beginning with TRANSFORM, SHARED, FUNC or TYPE after
  lexical comment/whitespace handling; it is not a syntax validator.

The stable candidate execution domain uses `enableTracing=false` and
`sharedJsonConfig=null`. Parameter names/types/defaults remain in the inventory,
but tracing payloads, non-null shared configuration, shared asynchronous commits,
`runWithShared`, `SharedStorageSpec`, concurrent/reentrant facade use and a generated
JS FFI ABI are experimental. Optional explanation fields therefore normally remain
null. This finite domain supplies a practical execution API without admitting
mutable global runtime state. Numeric/platform limits remain those in the
[numeric contract](../language/numeric.md).

Diagnostic severity values are ERROR and WARNING. Category IDs are exactly the
lowercase strings in the enum below and are the JSON representation; subset enum
JSON values use uppercase names. Source spans are one-based with inclusive ends.
Payload fields are optional machine context; additional diagnostic codes may be
introduced, so branch on codes/categories of interest and retain an unknown case.
DTO nullable properties carry absence as null. `contractsJson`/`inspectJson`
default to pretty output; `pretty=false` changes whitespace only.

## Exact inspect declarations

The marked Kotlin blocks are the frozen source signatures. Parameter names,
order, nullability, defaults, result types, constructor properties and enum values
are part of this list. All type names use the source imports: `JsonObject` and
`JsonElement` mean `kotlinx.serialization.json` types. Method blocks are members
of the named containing facade/result type, not new top-level functions.

<!-- api-signature interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/BranchlineFacade.kt | data class | BranchlineInspectRequest -->
```kotlin
public data class BranchlineInspectRequest(
    val programText: String,
    val transformName: String? = null,
    val includeDebugMetadata: Boolean = false,
    val includeWitness: Boolean = false,
    val includeNormalizedSource: Boolean = false,
)
```

<!-- api-signature interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/BranchlineFacade.kt | data class | BranchlineFeatureUsageSummary -->
```kotlin
public data class BranchlineFeatureUsageSummary(
    val features: List<String>,
)
```

<!-- api-signature interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/BranchlineFacade.kt | enum class | BranchlineSubsetCompatibility -->
```kotlin
public enum class BranchlineSubsetCompatibility {
    COMPATIBLE,
    INCOMPATIBLE,
    UNKNOWN,
}
```

<!-- api-signature interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/BranchlineFacade.kt | data class | BranchlineInspectTransform -->
```kotlin
public data class BranchlineInspectTransform(
    val name: String,
    val contractSource: String,
    val explicitContract: JsonObject?,
    val inferredContract: JsonObject,
    val mergedContract: JsonObject,
    val witness: JsonObject?,
)
```

<!-- api-signature interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/BranchlineFacade.kt | data class | BranchlineInspectResult -->
```kotlin
public data class BranchlineInspectResult(
    val success: Boolean,
    val transforms: List<BranchlineInspectTransform>,
    val diagnostics: List<BranchlineDiagnostic>,
    val warnings: List<BranchlineDiagnostic>,
    val featureUsage: BranchlineFeatureUsageSummary,
    val subsetCompatibility: BranchlineSubsetCompatibility,
    val normalizedSource: String?,
)
```

<!-- api-signature interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/BranchlineFacade.kt | object | BranchlineFacade -->
```kotlin
public object BranchlineFacade
```

<!-- api-signature interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/BranchlineFacade.kt | fun | inspect -->
```kotlin
public fun inspect(request: BranchlineInspectRequest): BranchlineInspectResult
```

<!-- api-signature interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/BranchlineFacade.kt | fun | contractsJson -->
```kotlin
public fun contractsJson(pretty: Boolean = true): String
```

<!-- api-signature interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/BranchlineFacade.kt | fun | inspectJson -->
```kotlin
public fun inspectJson(pretty: Boolean = true): String
```

<!-- api-signature interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/BranchlineFacade.kt | fun | inspectJsonPayload -->
```kotlin
public fun inspectJsonPayload(): JsonObject
```


## Exact diagnostic declarations

<!-- api-signature interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/diagnostics/BranchlineDiagnostics.kt | enum class | BranchlineDiagnosticSeverity -->
```kotlin
public enum class BranchlineDiagnosticSeverity {
    ERROR,
    WARNING,
}
```

<!-- api-signature interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/diagnostics/BranchlineDiagnostics.kt | enum class | BranchlineDiagnosticCategory -->
```kotlin
public enum class BranchlineDiagnosticCategory(public val id: String) {
    SYNTAX("syntax"),
    SEMANTIC("semantic"),
    CONTRACT("contract"),
    RUNTIME("runtime"),
    UNSUPPORTED_SUBSET("unsupported-subset"),
    NORMALIZATION("normalization"),
    UNKNOWN("unknown"),
}
```

<!-- api-signature interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/diagnostics/BranchlineDiagnostics.kt | data class | BranchlineSourceSpan -->
```kotlin
public data class BranchlineSourceSpan(
    val startLine: Int,
    val startColumn: Int,
    val endLine: Int,
    val endColumn: Int,
)
```

<!-- api-signature interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/diagnostics/BranchlineDiagnostics.kt | data class | BranchlineDiagnosticPayload -->
```kotlin
public data class BranchlineDiagnosticPayload(
    val operation: String? = null,
    val targetPath: String? = null,
    val expectedKind: String? = null,
    val actualKind: String? = null,
    val expected: JsonElement? = null,
    val actual: JsonElement? = null,
    val hint: String? = null,
)
```

<!-- api-signature interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/diagnostics/BranchlineDiagnostics.kt | data class | BranchlineDiagnostic -->
```kotlin
public data class BranchlineDiagnostic(
    val code: String,
    val message: String,
    val severity: BranchlineDiagnosticSeverity,
    val span: BranchlineSourceSpan? = null,
    val category: BranchlineDiagnosticCategory = BranchlineDiagnosticCategory.UNKNOWN,
    val payload: BranchlineDiagnosticPayload? = null,
)
```


## Exact execution declarations

<!-- api-signature interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/playground/PlaygroundFacade.kt | object | PlaygroundFacade -->
```kotlin
object PlaygroundFacade
```

<!-- api-signature interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/playground/PlaygroundFacade.kt | fun | run -->
```kotlin
public fun run(
        program: String,
        inputJson: String,
        enableTracing: Boolean = false,
        includeContracts: Boolean = false,
    ): PlaygroundResult
```

<!-- api-signature interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/playground/PlaygroundFacade.kt | fun | inspect -->
```kotlin
public fun inspect(
        program: String,
        sharedJsonConfig: String? = null,
    ): PlaygroundInspectResult
```

<!-- api-signature interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/playground/PlaygroundFacade.kt | fun | runWithContracts -->
```kotlin
public fun runWithContracts(
        program: String,
        inputJson: String,
        enableTracing: Boolean,
        includeContracts: Boolean,
        contractsMode: String,
        includeContractSpans: Boolean,
        sharedJsonConfig: String? = null,
        outputFormat: String,
    ): PlaygroundResult
```

<!-- api-signature interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/playground/PlaygroundFacade.kt | fun | isBodyOnlyProgram -->
```kotlin
public fun isBodyOnlyProgram(program: String): Boolean
```

<!-- api-signature interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/playground/PlaygroundFacade.kt | data class | PlaygroundResult -->
```kotlin
public data class PlaygroundResult(
    val success: Boolean,
    val outputJson: String?,
    val errorMessage: String?,
    val line: Int?,
    val column: Int?,
    val explainJson: String?,
    val explainHuman: String?,
    val inputContractJson: String?,
    val outputContractJson: String?,
    val contractSource: String?,
    val contractWarnings: String?,
)
```

<!-- api-signature interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/playground/PlaygroundFacade.kt | data class | PlaygroundInspectResult -->
```kotlin
public data class PlaygroundInspectResult(
    val success: Boolean,
    val normalizedSource: String?,
    val subsetCompatibility: String,
    val diagnosticsJson: String,
    val warningsJson: String,
    val featureUsageJson: String,
    val transformsJson: String,
    val inspectJson: String?,
    val errorMessage: String?,
)
```


## Exact CLI declarations

<!-- api-signature cli/src/commonMain/kotlin/io/github/ehlyzov/branchline/cli/BranchlineCli.kt | enum class | PlatformKind -->
```kotlin
public enum class PlatformKind { JVM, JS }
```

<!-- api-signature cli/src/commonMain/kotlin/io/github/ehlyzov/branchline/cli/BranchlineCli.kt | enum class | CliCommand -->
```kotlin
public enum class CliCommand { RUN, COMPILE, EXECUTE, INSPECT, SCHEMA, CONTRACT_DIFF }
```

<!-- api-signature cli/src/commonMain/kotlin/io/github/ehlyzov/branchline/cli/BranchlineCli.kt | object | BranchlineCli -->
```kotlin
public object BranchlineCli
```

<!-- api-signature cli/src/commonMain/kotlin/io/github/ehlyzov/branchline/cli/BranchlineCli.kt | fun | run -->
```kotlin
fun run(args: List<String>, platform: PlatformKind, defaultCommand: CliCommand? = null): Int
```

<!-- api-signature cli/src/commonMain/kotlin/io/github/ehlyzov/branchline/cli/CliFormatting.kt | enum class | ExitCode -->
```kotlin
public enum class ExitCode(val code: Int) {
    SUCCESS(0),
    USAGE(64),
    INPUT(65),
    IO(74),
    RUNTIME(70),
}
```

## CLI command support inventory

`BranchlineCli.run` is in `io.github.ehlyzov.branchline.cli` in the `cli` artifact.
It performs real I/O using the linked platform implementation. Pass JVM on JVM,
JS on Node; the enum is not a cross-platform emulator. `defaultCommand=null`
chooses `run` when no command token is present. All six command families and all
recognized aliases are accounted for here; a new command requires an explicit
classification. JVM launchers `bl`, `blc`, `blvm` default to run, compile, execute;
the Node launcher exposes commands through `bl`.

| Command and aliases | 1.0 classification | Input/result and supported core options |
| --- | --- | --- |
| `run` (also bare script path) | Stable candidate | Script to interpreter output; `--input PATH` or `-`, `--input-format json\|xml`, `--json-numbers strict\|safe\|extended`, `--json-key-mode string\|numeric`, `--transform NAME`, `--output-format json\|json-compact\|json-canonical\|xml\|xml-compact`, `--contracts off\|warn\|strict`. |
| `compile`, `c` | Stable candidate | Script to compiled artifact; `--output PATH`, `--transform NAME`, `--output-format json\|json-compact\|json-canonical`. |
| `exec`, `execute` | Stable candidate | Artifact to VM output; same core input/output/contract options as run. Artifact validation/version failures reject before executing. Runtime failures propagate to a failed command; execution is not replayed through interpreter fallback. |
| `inspect` | Stable candidate | Requires `--contracts`; `--transform NAME`, `--contracts-json`, `--contracts-debug`, `--contracts-witness`, `--normalized`; text by default, structured inspect envelope with `--contracts-json`. |
| `schema` | Experimental, explicitly excluded | Export one type or `--all`, or import JSON Schema with `--import PATH --name NAME`; `--nullable`, `--output PATH`. |
| `contract-diff` | Experimental, explicitly excluded | Compare two scripts/schema/contract files; `--type NAME`, `--fail-on-major`. Its report and special statuses below are not stable-candidate API. |

Run/exec defaults: JSON input, SAFE numbers, STRING keys, first transform,
pretty JSON output, an empty input object if no input path, contracts off. Use
`--input -` to read stdin. Compile defaults:
first transform, pretty JSON artifact to stdout unless `--output` is given.
Inspect defaults: all transforms, text contracts, no debug/witness/normalized
source. `--error-format text|json` is global and defaults to text. `--help`, `-h`,
`help`, `--version`, `-v` as the sole non-global argument return success.
Other flags (including `--jobs`, shared resources, tracing and output-file
routing) remain experimental under the default exclusion rule. Their presence
in `--help` does not extend this stable-candidate core. CLI accepts additional
mode aliases through its parsers; only the spellings listed in this table are
promised here. Human-readable help/error/report wording is not frozen.

Common statuses are 0 success, 64 usage/invalid mode or option, 65 invalid input,
74 I/O and 70 runtime failure. Inspect specifically maps `parse_error` and
`semantic_error` diagnostics to 70, while `no_transform_blocks` and
`transform_not_found` map to 65; a syntactically malformed inspected script is
therefore not a 65 case. Artifact version rejection is 65. For the excluded
contract-diff command, comparison
errors inside execution return 1; `--fail-on-major` returns 2 for major impact;
argument parsing still follows the common usage status. Inspect with valid
analysis and subset incompatibility can return 0: inspect is not an execution
or canonical-subset acceptance gate.

## Executable evidence and drift policy

The independent source consumer is
[`StableApiConsumerTest`](https://github.com/ehlyzov/branchline/blob/main/conformance-tests/src/commonTest/kotlin/io/github/ehlyzov/branchline/conformance/StableApiConsumerTest.kt).
It imports the published module surfaces, compiles the exact calls with defaults
and named arguments, asserts independently specified output, checks typed result
properties, subset/error behavior and unknown-mode/output fallback on JVM and
Kotlin/JS. It does not use parser/IR/VM internals to calculate its expectations.

[`StableApiInventoryJvmTest`](https://github.com/ehlyzov/branchline/blob/main/conformance-tests/src/jvmTest/kotlin/io/github/ehlyzov/branchline/conformance/StableApiInventoryJvmTest.kt)
compares every marked declaration with source, including defaults and complete
enums. It verifies the expected allowlist membership, checks every CLI family,
and runs real file-based run/compile/exec/inspect consumer examples with exact
output expectations, invalid modes and artifact rejection. This is a source
compatibility drift gate, not a binary ABI checker or a measurement of every
experimental branch. New unlisted public internals remain excluded by definition;
changing this allowlist requires documentation and consumer evidence together.

Run `./gradlew :conformance-tests:jvmTest --tests '*StableApi*'` and unfiltered
`./gradlew :conformance-tests:jsNodeTest`. The JVM inventory gate needs the checked
out repository. Test inputs include this catalog and the inventoried source files,
so source/catalog edits invalidate cached results. Parent release gates include
CLI JVM/Node suites for platform packaging and experimental command behavior.
