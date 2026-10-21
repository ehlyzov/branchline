---
title: Release Readiness & Stability
---

# Release Readiness & Stability

How we define stability and readiness for Branchline releases.

## Stability levels

- **Alpha:** evaluation releases; source, APIs and artifacts may change between
  prereleases. Pin the complete release version and retain source.
- **Experimental:** explicitly excluded from compatibility commitments, including
  when packaged alongside a stable runtime.
- **Release candidate:** a versioned prerelease for evaluating the documented
  candidate surface; the label does not close stable adoption or operational gates.
- **Stable:** an explicitly listed surface with a defined compatibility policy.
  This label is a candidate 1.0 commitment; v1.0.0-rc.1 is a prerelease.

## Current release posture

Branchline v1.0.0-rc.1 is a **release candidate**. Stable 1.0 adoption and production
readiness remain open; the external operational pilot has not started.
Passing local tests qualifies the tested surface for
evaluation; it does not certify production readiness or prove an advantage over
another language. The first operational pilot targets JVM transformations. JS
numeric limitations must remain explicit until equivalent precision is implemented.

## Readiness checklist

| Gate | Required evidence |
| --- | --- |
| Supported language surface | Every advertised standard-library function has defined domains, error behavior and executable tests; unsupported operations fail explicitly. |
| Numeric behavior | Exact-value and boundary assertions for the supported numeric domains, plus documented JVM/JS differences; wrapper-type checks alone do not establish precision. |
| Runtime errors | VM execution failures propagate without replaying the transform; compilation fallback is explicit and can be disabled for verification. |
| Deterministic profile | Ambient clock/randomness and orchestration are excluded or supplied through explicit inputs; subset inspection is not a sandbox or resource-limit guarantee. |
| Compatibility | JVM/JS conformance and regression corpus pass for the promised common subset; contract and machine-envelope changes follow the documented compatibility policy. |
| Pilot correctness | Shared inputs and independent expected results pass on Branchline and the comparison implementations; missing engines and incorrect results fail the comparison gate. |
| Operational pilot | Independent installation and maintenance trials, representative shadow replay, bounded resource behavior and a tested rollback procedure are recorded. |
| Performance claims | Equivalent workloads, correctness validation, runtime versions, preparation/execution separation, JMH methodology and uncertainty are recorded; functional tests do not establish speed. |
| Authoring claims | Equal task/model/tool budgets, withheld tests, failures and repair effort are reported; convenient syntax or fewer lines alone do not establish productivity. |

The equal-budget authoring study is complete and did not establish general
superiority over JSONata or Kotlin. Keep the external operational pilot gate open.
In-process execution of arbitrary untrusted programs additionally needs an explicit
capability and resource-isolation policy; the local pilot uses repository-controlled
transforms.

## v0.11.0-alpha.1 scope

This prerelease includes compiled CLI worker isolation, explicit VM compilation
fallback, numeric/domain fixes and ordinary VM scaling improvements. The JVM and
Node CLIs use the build version; JVM distributions require Java 21 or newer.

Research execution backends are packaged as an **unstable preview**. Kotlin
callers must explicitly opt in to their experimental API. Ordinary VM execution
remains the default; the CLI and playground do not automatically select generated
loops. JVM loop generation ships the pinned `org.ow2.asm:asm:9.9` dependency in
VM runtime metadata and in the standalone CLI jar. Java callers must follow the
same preview contract although Kotlin opt-in diagnostics cannot enforce it there.

The measured local-loop projection benefit applies to repeated prepared JVM
transformations. Preparation, retained memory and generated-class costs increase;
there is no claim of universal acceleration or a new default execution engine.
See [VM overview](../vm.md) and the repository's
`development/evidence/vm-local-loop-experiment/` for reproducible measurements.

## Frozen evaluation contract E1

For this stage, E1 fixes the following acceptance obligations for the candidate
revision. It is a bounded Alpha evaluation contract, with the fixtures as its
executable specification; it does not grant stable compatibility to every program
using the same constructs. The tested release SHA and results must be recorded
before declaring the candidate accepted. Defining these obligations does not mean
the current integrated gate has already passed.

| Included case | Required behavior and named evidence |
| --- | --- |
| `hello` in [Getting Started](getting-started.md) | `TRANSFORM`, `LET`, string concatenation, `input.name` and object `OUTPUT`: input `{"name":"Ada"}` produces exactly `{"greeting":"Hello, Ada"}` as JSON values. |
| `defaults` in Getting Started | Object access and `??` distinguish null/missing from `false`, zero and empty string. The documented input produces `{"name":"Guest","nickname":null,"active":false,"attempts":0}`; `{}` produces `{"name":"Guest","nickname":null,"active":true,"attempts":3}`. |
| `available-items` in Getting Started | Array comprehensions preserve row order, filter `qty > 0` and multiply the fixture's small integers: the documented input produces `{"items":[{"sku":"B","total":14},{"sku":"C","total":3}]}`. Missing/null/empty arrays and arrays with no positive quantities produce `{"items":[]}`. |
| Engines for these three programs | [GettingStartedExamplesTest](https://github.com/ehlyzov/branchline/blob/main/conformance-tests/src/commonTest/kotlin/io/github/ehlyzov/branchline/conformance/GettingStartedExamplesTest.kt) reads the actual guide and checks independently fixed expected outputs through interpreter and strict ordinary VM on JVM/Node. The VM must report successful `VM_BYTECODE`; fallback cannot satisfy this obligation. Wrong program output, stale advertised output and unmarked examples must fail the gate. |
| Numeric domain | The exact-value/boundary obligations and JVM/JS exclusions are the [numeric profile](../language/numeric.md), rather than an implied guarantee for arbitrary numeric inputs. The onboarding multiplication cases use only the specified small integers. |
| Inspect interface | The exact current request/result inventory below and the [inspect envelope rules](#inspect-envelope-rules) define the interface to check. Parse failure, incompatible subset, null normalization and CLI/facade parity are explicit cases in `CliIntegrationTest`. |
| VM failure and artifact boundary | `VMFallbackPolicyTest` requires escaping execution failures to propagate once without replay; strict compilation rejection performs no host work. `CompiledArtifactCompatibilityTest` requires the frozen legacy greeting, v3 marker, rejected unsupported formats/versions and no unsafe downgrade, as detailed in [migration policy](migrations.md#compiled-artifact-policy). |

E1 fixes the expected behavior for these inputs and boundaries; changes must
update the contract and its independent oracle visibly. It does not promise a
deprecation window across Alpha releases. Upgrades retain source, recompile with
the target release and replay E1 plus workload-specific cases. Performance,
arbitrary standard-library coverage, general binary ABI and untested OS/runtime
combinations are outside E1. Operational promotion additionally requires the
[pilot plan](pilot-plan.md).

### Current inspect inventory

The embedding entry point is
`io.github.ehlyzov.branchline.BranchlineFacade.inspect(request: BranchlineInspectRequest): BranchlineInspectResult`.
The request constructor fields, in order, are:

| Field | Kotlin type | Default |
| --- | --- | --- |
| `programText` | `String` | Required |
| `transformName` | `String?` | `null` |
| `includeDebugMetadata` | `Boolean` | `false` |
| `includeWitness` | `Boolean` | `false` |
| `includeNormalizedSource` | `Boolean` | `false` |

`BranchlineInspectResult` exposes `success: Boolean`,
`transforms: List<BranchlineInspectTransform>`,
`diagnostics: List<BranchlineDiagnostic>`,
`warnings: List<BranchlineDiagnostic>`,
`featureUsage: BranchlineFeatureUsageSummary`,
`subsetCompatibility: BranchlineSubsetCompatibility` and
`normalizedSource: String?`. Its JSON methods are
`inspectJson(pretty: Boolean = true): String`,
`inspectJsonPayload(): JsonObject` and
`contractsJson(pretty: Boolean = true): String`. The last method renders the
historical contract payload, not the complete inspect envelope. This inventory is
source-level; generated data-class methods and binary layouts are excluded.

For the CLI, the corresponding inventoried form is
`bl inspect <script.bl> --contracts-json [--transform <name>] [--normalized] [--contracts-debug] [--contracts-witness]`.
The fixtures require exit 0 for completed inspection, including an incompatible
subset, and exit 70 with `success: false` for a parse failure. A machine consumer
must inspect both the envelope and exit status. Other CLI command families are
not admitted to E1 merely because they appear in help output.

## Candidate 1.0 compatibility contract

The table below proposes extensions beyond E1 for a future 1.0 release. These are
not already guaranteed by Alpha or admitted by the E1 fixtures. Inclusion
requires both the documented behavior and executable evidence for the advertised
runtime. Being accepted by the parser, exposed as a public Kotlin declaration or
shipped in a jar does not by itself include a feature.

| Surface | Candidate commitment within 1.x | Boundary and remaining gate |
| --- | --- | --- |
| Source transformations | Preserve documented syntax and output/error semantics for the declared deterministic JSON transformation profile: `TRANSFORM`, `input`, literals, object/list construction and access, expressions, `LET`, `SET`, `MODIFY`, `+=`, conditionals, `FOR EACH`, comprehensions, functions, lambdas and `OUTPUT`. | Freeze the supported construct/domain inventory against the [AI canonical subset](../language/ai-canonical-subset.md), language reference and conformance cases. The AI subset is an authoring restriction, not proof of VM coverage or successful execution. Graph orchestration, `SHARED`, `AWAIT`, `SUSPEND`, ambient time/randomness and arbitrary host capabilities are excluded from this profile. |
| Standard library and numbers | Preserve each explicitly admitted function's arity, input domain, output meaning and documented errors; preserve only the numeric guarantees in the [numeric profile](../language/numeric.md). | The [complete stdlib inventory](../language/stdlib-support.md) classifies all 68 default names: 50 bounded stable candidates and 18 experimental exclusions. Registry/contract drift and interpreter/strict-VM probes are executable gates. JVM exact arithmetic does not imply equivalent JS precision. |
| Execution engines | Interpreter and ordinary VM must agree on the admitted cases, including failures. VM adapters permit compilation-only fallback under `COMPILE_FAILURE`; `NONE` requires bytecode execution. Escaping runtime errors propagate once, without interpreter replay. | Run strict VM checks for every case advertised as VM-supported. Successful compatibility fallback establishes interpreter behavior only. Effects and mutations before failure are not rolled back. Generated/prepared research backends remain opt-in and excluded. See [VM execution policy](../vm.md). |
| CLI | Preserve the admitted `run`, `compile`, `exec` and `inspect` command forms, option meanings, defaults, exit categories and machine output used by their fixtures. | The [API/CLI inventory](api-support.md) fixes the admitted run/compile/exec/inspect forms, options and exit behavior. Unlisted modes and schema/contract-diff remain excluded; human help text and diagnostic wording are not machine interfaces. |
| Inspect JSON | Preserve the root fields `success`, `diagnostics`, `warnings`, `featureUsage`, `subsetCompatibility`, `normalizedSource` and `transforms`, with their documented types and meanings. Retain existing single-transform contract aliases within 1.x. | Follow the [envelope rules below](#inspect-envelope-rules); gate parse errors, incompatible subset results, nullable normalization and CLI/facade equality. Contract inference can become more precise: inferred contracts, witnesses and normalized formatting are not frozen byte strings. Applications should author explicit contracts when validation must remain fixed. |
| Kotlin embedding | Candidate source compatibility is limited to the exact typed inspection and String/JSON execution facades and DTOs in the [API allowlist](api-support.md). | Typed consumer fixtures and exact declaration/default/enum drift checks freeze this allowlist. Every unlisted declaration is excluded, including low-level execution adapters, custom host integration, AST/IR and experimental backends. No general JVM binary ABI or generated Kotlin/JS export ABI is promised; recompile consumers on upgrade. |
| Distributions and runtimes | Publish the supported Java/Node versions and artifact names for each release; smoke-test installation and execution on that matrix. | The [platform policy and acceptance record](platform-support.md) distinguish Java 21/25 and Node 22/24 targets, legacy launch compatibility, exact tested OS/CPU cells and outstanding server qualification. Unavailable required cells keep this gate open. Raising an admitted minimum during 1.x requires a major release. |
| Compiled artifacts and snapshots | Source plus recompilation is the migration contract. The release states its artifact writer version, accepted reader versions and tested instruction/value domain. | Release SemVer and artifact format versions are separate. No all-1.x `.blc` portability, forward-reader compatibility, automatic downgrade or durable snapshot/closure schema is promised. See [artifact migration](migrations.md#compiled-artifact-policy). |

### Versioning and deprecation

After this contract is adopted at 1.0, patch releases correct behavior toward the
documented admitted contract without intentionally breaking it. Minor releases
may add opt-in features, functions, CLI options and additive machine fields.
Removing or renaming an admitted construct/API/option/field, changing its type or
meaning, changing a documented default, narrowing a valid input domain, or
changing a documented numeric/error guarantee requires a new major version.
Adding syntax must also preserve existing valid programs and identifier use.

An admitted feature must first be deprecated in a minor release, with the reason,
replacement, first affected version and migration example in the migration guide.
Keep it available for the remainder of 1.x; removal is allowed at the next major
release. Alpha and explicitly experimental surfaces have no such retention
window. An urgent security correction may need an exceptional breaking fix; it
must be identified explicitly in release notes with affected versions and a
migration path, rather than described as an ordinary compatible update.

Changes to undocumented behavior, internal optimization, exact diagnostic prose,
debug metadata, inferred-contract detail or canonical formatting are not stable
interfaces. A release must still disclose material observable changes and add
regressions; calling a change a bug fix does not make a documented incompatible
result compatible. Release notes must separately identify source, CLI/envelope,
embedding, numeric and compiled-format impact.

### Inspect envelope rules

The current envelope has no schema-version field. Consumers must ignore unknown
fields, accept optional additive diagnostic payload keys and handle unknown enum
values conservatively. New diagnostic codes, feature names or enum values are
additive only under this consumer contract; do not use an exhaustive list as an
execution authorization rule.

`success: true` means inspection completed, and can coexist with
`subsetCompatibility: "INCOMPATIBLE"`. Treat `UNKNOWN` or an unfamiliar
compatibility value as requiring review. `normalizedSource: null` can mean it was
not requested, the subset is incompatible, or normalization is unsupported.
Neither a successful inspect nor inferred contracts guarantee that an execution
will succeed. Use the documented structured diagnostic fields; messages are for
people.

Any incompatible envelope redesign needs an explicitly selectable new schema or
command with a documented migration. Do not silently repurpose an existing field
or remove the legacy single-transform aliases in a minor release.

### Evidence and adoption gates

Current implementation evidence is deliberately narrower than the candidate
contract:

- [CompatibilityCorpusTest](https://github.com/ehlyzov/branchline/blob/main/conformance-tests/src/commonTest/kotlin/io/github/ehlyzov/branchline/conformance/CompatibilityCorpusTest.kt)
  has explicit expected outputs for a small source corpus. It is not exhaustive
  standard-library coverage or strict VM acceptance.
- [BranchlineFacade](https://github.com/ehlyzov/branchline/blob/main/interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/BranchlineFacade.kt)
  constructs the inspect envelope;
  [CLI integration tests](https://github.com/ehlyzov/branchline/blob/main/cli/src/jvmTest/kotlin/io/github/ehlyzov/branchline/cli/CliIntegrationTest.kt)
  cover parse failure, incompatible subsets, null normalization and facade parity.
- [VMFallbackPolicyTest](https://github.com/ehlyzov/branchline/blob/main/vm/src/commonTest/kotlin/io/github/ehlyzov/branchline/vm/VMFallbackPolicyTest.kt)
  checks strict rejection and effects occurring once on execution failure.
- [CompiledArtifactCompatibilityTest](https://github.com/ehlyzov/branchline/blob/main/cli/src/commonTest/kotlin/io/github/ehlyzov/branchline/cli/CompiledArtifactCompatibilityTest.kt)
  checks the previous writer fixture, current marker, old-reader rejection,
  invalid versions and migration guidance.
- [ExperimentalVMApi](https://github.com/ehlyzov/branchline/blob/main/vm/src/commonMain/kotlin/io/github/ehlyzov/branchline/vm/ExperimentalVMApi.kt)
  explicitly excludes preview API source/binary compatibility.

The API/CLI and complete default-stdlib inventories are now explicit allowlists,
with executable drift and consumer checks. Before adopting this policy at 1.0,
complete all required cells in the [platform matrix](platform-support.md), run interpreter and strict VM cases on JVM/JS within the numeric profile,
verify old/new consumer and artifact fixtures, and complete the operational pilot
gate above. Store the release SHA, commands and actual results; the existence of
tests or this proposal is not evidence that a particular release passed them.

## Related
- [API and CLI support inventory](api-support.md)
- [Complete stdlib support inventory](../language/stdlib-support.md)
- [Migration Guides](migrations.md)
- [Operational pilot plan](pilot-plan.md)
- [Benchmarks](../benchmarks.md)
