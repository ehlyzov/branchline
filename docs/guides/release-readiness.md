---
title: Release Readiness & Stability
---

# Release Readiness & Stability

How we define stability and readiness for Branchline releases.

## Stability levels
- **Experimental:** subject to change without notice.
- **Stable:** compatible within a major version.

## Current release posture

Branchline remains **Alpha**. Passing local tests qualifies the tested surface for
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

## Compatibility expectations

During Alpha, compiled artifacts and experimental APIs may change between
prereleases. Keep Branchline source and recompile for the target runtime; see
[Migration Guides](migrations.md). Experimental API compatibility is not promised.
Stable API compatibility within a major version is a future stability commitment,
not a claim about the current Alpha surface.

## Related
- [Migration Guides](migrations.md)
- [Benchmarks](../benchmarks.md)
