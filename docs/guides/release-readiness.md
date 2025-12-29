---
title: Release Readiness & Stability
---

# Release Readiness & Stability

This page defines the production readiness gate for Branchline, including versioning policy, backward compatibility rules, deprecation expectations, and release note requirements. It also documents stability levels for language features and the SLA-style targets we use to evaluate readiness.

## Production Readiness Checklist

### Versioning policy
- **Semantic versioning** (`MAJOR.MINOR.PATCH`) for all published artifacts.
- **Pre-1.0.0 policy:** until the first stable release, breaking language or runtime changes are allowed. Semantic versioning guarantees begin at **1.0.0**.
- **MAJOR:** Breaking language or runtime changes, incompatible CLI flags, or removal of deprecated features.
- **MINOR:** Backward-compatible language additions, new standard library functions, or new tooling capabilities.
- **PATCH:** Bug fixes, performance improvements, doc updates, and non-breaking tooling changes.

### Backward compatibility
- **Stable features** must remain backward compatible across MINOR and PATCH releases (starting at 1.0.0).
- **Beta features** aim for compatibility, but may evolve before becoming Stable; changes require a release note callout.
- **Experimental features** can change freely; they are excluded from compatibility guarantees.

### Deprecation plan
- Deprecations must be announced in **release notes** and tagged with a **target removal version**.
- A deprecation should remain for at least **one MINOR release** before removal.
- Deprecated syntax or functions should emit diagnostics in the CLI and interpreter (warning-level).

### Release notes
Every release must include:
- **Compatibility summary:** breaking vs non-breaking changes.
- **Stability updates:** promotions/demotions of feature stability levels.
- **Behavioral changes:** observable runtime or parsing differences.
- **Performance summary:** benchmark deltas and regressions (if any).

## Stability Levels Matrix

The table below maps language and runtime areas to explicit guarantees. The matrix is updated alongside release notes.

| Area | Examples | Stability | Guarantees |
| --- | --- | --- | --- |
| Core syntax & parser | `TRANSFORM`, `OUTPUT`, expressions, basic control flow | **Stable** | Backward compatible across MINOR/PATCH releases. |
| Standard library (core) | `JOIN`, `UPPER`, `LOWER`, `NOW` | **Stable** | Stable signatures and behavior across MINOR/PATCH releases. |
| Standard library (extended) | Higher-order functions, advanced array helpers | **Beta** | Changes possible with release note callout; backward compatibility targeted. |
| Diagnostics & tracing | `EXPLAIN`, `CHECKPOINT`, `ASSERT` output formats | **Beta** | Minor format tweaks allowed; breaking changes require major release. |
| Shared memory & suspension | `SHARED`, `SUSPEND` | **Experimental** | No compatibility guarantees. |
| Contract inference | Input/output shape synthesis | **Experimental** | APIs and outputs may change without notice. |

## Compatibility Test Suite

Branchline protects compatibility with a **fixed corpus** of programs that must pass on all supported runtimes. The corpus is implemented inside the conformance test module so it runs on JVM and JS as part of the standard conformance matrix.

- Location: `conformance-tests/src/commonTest/kotlin/v2/conformance/CompatibilityCorpusTest.kt`
- Scope: representative programs covering core syntax, control flow, standard library usage, and tracing behaviors.
- Rule: a corpus failure blocks readiness unless the change is documented as a breaking change.
- Coverage requirement: every **Stable** feature must appear in the fixed corpus so its behavior is locked.
- Motif: the corpus is a **curated, stable subset** of conformance tests that guards against breaking changes while the full conformance suite continues to grow.

## Readiness Gate & SLA Targets

The readiness gate is a set of minimum thresholds that must be met before a release is labeled **Production Ready**.

### Gate requirements
- ✅ **Conformance suite green:** all conformance tests, including the compatibility corpus, must pass on JVM and JS.
- ✅ **No open regression tickets** for Stable feature areas.
- ✅ **Release notes published** with compatibility/stability annotations.

### SLA-style targets
- **Error rate:** < **0.1%** uncaught runtime errors across the compatibility corpus and benchmark scenarios.
- **Performance regressions:** no more than **5%** slowdown vs the previous MINOR release on the standard benchmark suite.
- **Compatibility drift:** zero breaking changes for **Stable** features in MINOR/PATCH releases.

These targets are updated over time as the benchmark suite evolves.
