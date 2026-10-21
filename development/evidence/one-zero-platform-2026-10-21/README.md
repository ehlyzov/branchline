---
status: Reviewed
depends_on: ['planning/one-zero-platform-2026-10-21']
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-10-21
---
# Platform acceptance: bounded macOS and Linux matrix accepted

**The bounded macOS arm64 and Debian arm64/Linuxkit container matrix passes.**
Docker recovery enabled 144 additional Linux packaged checks on the identical
candidate. This is not a 1.0 release decision, public deployment verification or
completed external pilot; the actual pilot hosts still require qualification.

Three GPT-6.1 Sol High agents handled packaged CLI execution and a discovered
repair, browser acceptance, and an independent platform/pilot policy review.
The parent verified source identity, reviewed the repair and test oracles, ran
the builds and CLI regression gate, inspected raw results/screenshots and executed
independent UTF-8/cross-artifact cases. The browser agent's initial model-capacity
failure was retried with the same requested model.

## Candidate identity

Base commit: `9de86a0cec25328896a98f909fceec73df0da781`, with the accepted uncommitted
contract/API/stdlib/v3-codec changes and the Node I/O correction described below.
All 54 paths in the preceding acceptance manifest matched at entry. The 31 prior
source/build paths still match after this slice; the new product delta is confined
to CLI `JsPlatform.kt`, with a new Node regression suite. Documentation/evidence
also changed. The earlier 1,280-pass semantic gate is reused for its unchanged
scope; it was not rerun and relabelled as new platform evidence.

The artifacts still display development version `0.11.0-alpha.1`. They are **not**
the published artifact of that name. Use the checksums and source manifest below;
an external pilot requires its own immutable, uniquely identified candidate.

| Built artifact | SHA-256 |
| --- | --- |
| JVM standalone jar | `e3b726e448be86bb9daafa7c5925ef8f8efc763a4ae4b6a400a52c5f0ff85812` |
| Node tarball after I/O repair | `82ee715e5d100bf130c9291a9bdb7c5e4749a6a510e65bb2a1bc37676c3b1a1f` |

Browser acceptance uses the actual freshly built MkDocs site at
`http://127.0.0.1:18765/playground/`, with its assets hashed in the browser evidence.
The subsequent CLI-only repair does not change that browser runtime. Later
platform-documentation edits do not change the accepted editor bundle.

## Actual matrix

Host: macOS 27.0.1, build 26A434, Darwin 27.0.0, arm64.

| Runtime / engine | Results | Scope |
| --- | --- | --- |
| Temurin Java 21.0.11 | 35/35 | Fresh standalone jar |
| Liberica Java 21.0.9 | 35/35 | Fresh standalone jar |
| Liberica Java 25.0.1 | 35/35 | Fresh standalone jar |
| Node 18.20.8 | 36/36 | Extracted executable launcher; legacy compatibility only |
| Node 22.23.3 | 36/36 | Extracted executable launcher; maintained target |
| Node 24.22.0 | 36/36 | Extracted executable launcher; maintained target |
| Node 25.3.0 | 36/36 | Extracted executable launcher; incidental EOL compatibility |
| Retained v2 JVM reader | 2/2 | Rejects actual v3 artifacts written by JVM and Node |
| Chrome 155.0.8059.40 | 14/14 | Actual local MkDocs editor and runtime |
| Playwright WebKit 26.6 | 14/14 | Actual local editor; not Safari application certification |
| Playwright Firefox | Unverified | Failed before application startup: profile/sandbox launch errors |
| Debian 13.7 arm64, Linux 7.0.12-linuxkit, glibc 2.41 | 144/144 | Temurin JRE 21.0.12.1+1 / 25.0.4.1+1, Node 22.23.3 / 24.22.0, retained v2 reader; container only |
| Windows / Intel macOS / mobile / Safari app | Not run | No claim of support from these results |

The [platform support policy](../../../docs/guides/platform-support.md) fixes the
intended Java 21/25 and Node 22/24 targets, exact tested cells, maintenance versus
launch-compatibility distinction, and 1.x support-transition rule. The Linux
cell was closed only after actual execution following Docker recovery.

## Packaged acceptance and discovered repair

The final CLI harness passes **251/251** cases with independently fixed expected
JSON, statuses and error kinds. It uses fresh installation directories containing
spaces and the shipped Node executable/shebang with each selected runtime. It
requires no repository dependency installation in those directories. Cases cover
stdin, defaults/null/falsy values, projection/filtering, numeric regressions,
contracts, inspect, source/runtime/input failures, compile/exec, crossed JVM/Node
artifacts, genuine v1/v2 readers and v3 rejection by the actual retained old reader.
XML checks exercise the bundled Node parser dependency.

Before repair, all four Node versions escaped missing-file `ENOENT` as native JS
errors with exit 1, violating documented exit 74 and structured I/O output. JVM
returned the expected result. Native Node errors are not Kotlin Exceptions, so
common `catch (Exception)` handlers missed them.

`withNodeFileErrors` now normalizes only native system-error-shaped objects at
supported read/write/stdin boundaries. Existing common helpers produce the I/O
envelope. Unexpected native TypeError and Kotlin exceptions retain their identity;
the patch does not broadly turn arbitrary failures into I/O errors. Experimental
shared-directory traversal and the unused append adapter are outside this fix.

NodeFileIoJsTest covers five real CLI failure paths, ordinary read/write success,
failure normalization and the non-I/O negative controls. The final packaged
matrix adds the same missing-source/compile/artifact/input and blocked-output
cases on every runtime. Original product failures remain in `cli/pre-fix/`;
separate initial harness syntax/XML/envelope mistakes are retained in
`cli/initial-harness-run/`, not presented as product defects.

## Browser and parent verification

Both accepted engines load a 40-example catalog, run three examples and independent
transform/projection oracles, update output after program/input edits, mark stale
results, retain drafts across example switches, produce numeric/JSON results,
show syntax errors and recover after correction. Each final recorded run has zero
page exceptions, failed requests or HTTP errors; WebKit's font preconnect message
is informational. Initial uncached navigation also observed an external GitHub
latest-release metadata 404, documented separately by the browser agent. This
does not establish that every initial load is console-clean.

Browser harness corrections are disclosed: Monaco replacement initially retained
tokens, a newline-stripping helper removed a required LET separator, and an
expected JSON value initially ignored SAFE explicit-number string rendering.
Final assertions validate editor text before Run and use independent correct
numeric/string expectations. These fixes did not change the product. Screenshots
and raw failure/final logs are retained.

The parent independently ran **8 UTF-8 and cross-artifact checks** on Java 25 and
Node 24: Cyrillic/emoji paths, multilingual input/output, preserved zero, filtered
row ordering, actual compilation and execution of each producer's artifact by both
readers. All passed. Parent inspection also verified the final 251-case count,
both 14-case browser results, source identity and actual CLI JUnit XML.

## Commands and evidence

```sh
CHROME_BIN='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' ./gradlew \
  :cli:blShadowJar :cli:packageJsCli playgroundBuildAssets --console=plain
# After the bounded Node I/O repair:
CHROME_BIN='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' ./gradlew \
  :cli:jvmTest :cli:jsNodeTest :cli:blShadowJar :cli:packageJsCli --console=plain
```

Both builds succeeded. The second gate has **73 JVM + 26 Node tests**, zero
failures/errors/skips; unchanged JVM tests were up-to-date. The new Node suite
executed. Playground build includes its worker/catalog/XML/wrapper checks.
The strict MkDocs build and contour/diff checks are recorded separately.

- [CLI report and reproduction](cli.md), [portable matrix harness](cli-matrix.py).
- [Browser report](browser.md), [independent pilot review](pilot-review.md).
- [Linux matrix and reproduction](linux.md), [Docker recovery](docker-recovery.md).
- Raw CLI, build, parent probes, source manifests and Docker evidence:
  `output/one-zero-platform-2026-10-21/`.
- Browser logs, screenshots, actual results and asset identity:
  `output/playwright/platform-2026-10-21/`.

## Unclosed gates and external handoff

Docker initially blocked Linux execution. At the user's request, diagnosis found
host ENOSPC followed by VM disk I/O errors and a wedged backend. A supported forced
Desktop stop/start recovered the engine without reset/prune or deleting user data.
New isolated containers passed storage checks. Existing application containers
remain stopped; their application/data integrity was not audited.

The unchanged packaged harness then passed **144/144 Linux cases**: 35 per JRE,
36 per Node runtime and two old-reader rejections. The parent independently checked
all raw statuses/JSON oracles, candidate/harness hashes and runtime archive/binary
hashes (`parent-linux-audit.json`). With the 251 macOS cases this is **395 packaged
checks**. Linux tests use a pinned image, read-only repository/runtime mounts,
network disabled and disposable temporary storage. Host space after setup/run was
about 6.3 GiB; limited disk headroom remains an operational constraint.

This closes the planned container platform cell, not native Linux or x86 coverage.
Firefox remains unqualified after a launch failure; real Safari was not tested.
No additional product change or repeat semantic/browser gate was needed.

The external pilot still needs:

1. An immutable release candidate and two named independent integrators with
   separately owned installations; agent runs do not count as independent users.
2. Approved representative/redacted datasets, immutable domain-authored expected
   outputs, one explicit numeric/wire profile and named domain/operations owners.
3. Predeclared host-enforced size/concurrency/queue/memory/timeout/p95/p99 limits,
   baseline, alerts, retention and admission replay with predictable limit failures.
4. A measured rollback drill with in-flight reconciliation and no lost/duplicate
   effects, plus independent maintenance exercises with withheld cases.
5. At least 14 consecutive representative days for **each** integration, daily
   evidence and a named reviewer's go/no-go decision. Windows may run in parallel;
   zero traffic and synthetic replay do not replace observation.

Use [the pilot plan](../../../docs/guides/pilot-plan.md) and the still-unfilled
[per-integration record](../../planning/one-zero-pilot-run-record.md). These
passing platform cells do not supply the missing people,
production data, immutable release identity or elapsed observation period.
