---
status: Implemented
depends_on: ['planning/one-zero-contracts-2026-10-18']
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-10-18
---
# 1.0 preparation: independent local acceptance

Accepted locally on 2026-10-18 against the working tree based on
`9de86a0cec25328896a98f909fceec73df0da781`. This is not a new published version or
completed operational pilot. Three GPT-6.1 Sol High agents implemented/reviewed
the compatibility, numeric and onboarding slices; the parent reviewed source,
ran the integrated gates and exercised actual CLI distributions independently.

## Delivered contract and artifacts

- [Frozen evaluation contract E1 and proposed 1.0 policy](../../docs/guides/release-readiness.md):
  precise bounded fixtures, inspect request/result inventory, error/fallback
  obligations, versioning/deprecation and explicit exclusions. E1 fixes expected
  evaluation behavior; it does not make every use of its constructs stable.
- [Numeric semantics](../../docs/language/numeric.md): common integer profile
  bounded at every intermediate, JVM explicit exact operations, JS signed-64-bit
  integer and Double-decimal limitations; conversion, rounding and JSON boundaries.
- [Executable first-use guide](../../docs/guides/getting-started.md): three complete
  programs, inputs and independently pinned expected results. JVM/Node tests read
  marked guide blocks directly and require interpreter + strict VM execution.
  Negative controls detect wrong output, stale documentation and missing markers.
  The guide is an explicit input to both Kotlin JVM and JS Gradle test task types.
- [Pilot plan](../../docs/guides/pilot-plan.md) and
  [unfilled run record](../planning/one-zero-pilot-run-record.md): two independent
  integrations, named roles, immutable domain oracles, predeclared resource
  thresholds, maintenance exercises, rollback reconciliation and at least 14
  consecutive representative days. Independent agent review found no material
  contradiction with the existing price-comparison and Ktor protocols.

## Correctness defects found and repaired

| Defect | Independent evidence / correction |
| --- | --- |
| JVM decimal FLOOR/CEIL truncated | Previous CLI gave `FLOOR(DEC("-1.5"))=-1` and `CEIL(DEC("1.5"))=1`; corrected scale-zero rounding gives `-2` and `2`. |
| Asymmetric Long multiplication overflow | Previous CLI gave negative wrapped minimum for `-1 * NUMBER("-9223372036854775808")`, positive promoted value for reversed operands. Both now produce exact `9223372036854775808`. |
| VM integer literal folding overflow | New oracle caught `1000001 * 1000001` yielding `-725379967` in compiled VM while interpreter returned `1000002000001`. Folding now uses canonical numeric helpers and excludes non-integer JS values. Unreachable invalid division remains deferred to execution. |
| Folded BigInt could not reload | Independent review and CLI repro caught a promoted Long+Long constant stored under the existing i64 codec. Folding now leaves BigInt/BigDec results as runtime arithmetic. Two JVM CLI artifact roundtrip tests cover addition and multiplication beyond Long. |
| JS INT accepted fractional numbers | Node tests caught primitive Int checks swallowing fractional doubles. Strict integral conversion now validates ordinary Number values; fractional and non-finite errors are covered. |
| JS DEC(number) lost fractional digits | Previous Node CLI serialized `DEC(1.5)` as `"1"` and `DEC(0.5)` as `"0"`. Numeric-kind classification preserves `"1.5"` and `"0.5"`; explicit string parsing remains unchanged. |
| JS bytecode mis-tagged fractions | Parent CLI probe saved `1.5` as i64 and reload failed. Primitive serialization now uses existing f64 for fractions/unsafe primitive integers and preserves actual Long i64 values. Four common tests cover signs, Long bounds, large finite floats and strict compiled DEC roundtrips. |

The JS defects arose from primitive-number type checks; this is not a new
arbitrary-precision implementation. No syntax changed. Migration notes explicitly
require recompiling previously incorrect constants/artifacts from source.

## Verification

Environment: macOS aarch64, Temurin Java `21.0.11+10-LTS`, Node `v25.3.0`.
Node 18 CI and other OS/JVM combinations were not rerun here; the observed local
environment is not a broad platform support certification.

```sh
CHROME_BIN='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' ./gradlew :interpreter:jvmTest :interpreter:jsNodeTest :conformance-tests:jvmTest :conformance-tests:jsNodeTest :vm:jvmTest :vm:jsNodeTest :cli:jvmTest :cli:jsNodeTest :cli:blShadowJar --continue --console=plain
python3 output/one-zero-2026-10-18/accept-cli.py
/tmp/branchline-release-mkdocs/bin/mkdocs build --strict --site-dir /tmp/branchline-one-zero/site
bin/refresh_contour.sh
git diff --check
```

The final Gradle gate passed. JUnit XML was inspected rather than trusting exit
status alone: **1222 test executions, 1221 passed, 1 skipped, 0 failures/errors**.
Counts include JVM/Node executions of common tests, not 1222 distinct scenarios.
The existing skip is in `TreeReconstructionLoadTest`. New suites account for 43
test executions across targets. Compatible unchanged tasks may be up-to-date;
changed modules and dependent tests reran after the final source changes.

The independent CLI probe passed **24 cases**: all three guide programs through
JVM/Node source and compiled execution (12), four JVM numeric regressions through
both paths (8), Node decimal preservation through both paths (2), and Node strict
fraction rejection through both paths (2). Expected JSON comes from parent-owned
literal oracles, not agreement between engines. Compiled checks save then reload
actual files. Source-level tests additionally require successful VM_BYTECODE
reports, so interpreter fallback cannot masquerade as VM acceptance.

MkDocs strict build, contour audit and whitespace checks passed. Existing MkDocs
informational notices about older benchmark links/navigation remain; no new strict
build failure occurred. No browser UI changed, new deployment occurred, or public
site acceptance is claimed in this slice.

Raw evidence in [output/one-zero-2026-10-18](../../output/one-zero-2026-10-18/):
`gate-final-verified.log`, `test-counts.json`, `cli-oracles.json`, `accept-cli.py`,
`mkdocs-final.log`, `contour.log`, source hashes and saved failed-gate logs. Initial
failures were retained and repaired; they were not removed from the final gate.

## Remaining 1.0 decision

This stage closes the bounded E1/numeric/onboarding implementation and prepares
the pilot. It permits a Beta/RC candidate review, not stable 1.0 approval.

- Freeze and test the complete admitted stdlib/API/CLI surface and actual runtime
  support matrix; E1 is deliberately smaller than those proposed commitments.
- Arbitrary BigInt constants and BigDec snapshots are not losslessly portable in
  the current codec. The i64 reader is Long and f64 reloads as Double. Primitive
  tagging and avoiding new unsupported folds do not resolve that encoding limit;
  it remains in the gap registry and migration guide.
- Real integrators, representative datasets, agreed limits, rollback exercises
  and both 14-day observation records remain pending. No synthetic test is counted
  as field evidence, and no new performance/productivity advantage is claimed.

A contour trigger fired for guide-backed verification, numeric behavior fixes and
pilot operation boundaries; SERVICE_MAP, VERIFY, knowledge gaps and generated
navigation overlays were updated. Historical user/research artifacts were left
untouched. No release tag, commit, push or deployment was performed in this slice.
