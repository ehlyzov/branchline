---
status: Implemented
depends_on: ['planning/one-zero-platform-2026-10-21']
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-10-21
---
# Packaged CLI platform acceptance

The final bounded gate passed **251/251 actual CLI invocations**, with zero
failures, at `2026-10-21T09:12:09Z`. This is a local candidate acceptance on
macOS 27.0.1, build 26A434, Darwin 27.0.0, ARM64. It does not admit unexecuted
operating systems, CPU architectures, runtime versions, or every future patch.

## Executed matrix

Every runtime used a separately installed package in a new temporary directory
containing spaces. Each JVM executed a copied standalone jar with its actual
`java -jar` launcher. Each Node runtime executed the executable `bin/bl.cjs`
extracted from the actual release tarball, with the exact runtime selected through
`PATH` and the shipped `#!/usr/bin/env node` shebang. No npm installation was run
in any extracted package; XML input also exercised its bundled dependency.

| OS / CPU | Actual runtime | Final invocations | Result |
| --- | --- | ---: | --- |
| macOS 27.0.1 / ARM64 | Temurin OpenJDK 21.0.11+10-LTS | 35 | Pass |
| macOS 27.0.1 / ARM64 | Liberica OpenJDK 21.0.9+11-LTS | 35 | Pass |
| macOS 27.0.1 / ARM64 | Liberica OpenJDK 25.0.1+11-LTS | 35 | Pass |
| macOS 27.0.1 / ARM64 | Node 18.20.8 | 36 | Pass; historical minimum compatibility |
| macOS 27.0.1 / ARM64 | Node 22.23.3 | 36 | Pass; maintained LTS candidate line |
| macOS 27.0.1 / ARM64 | Node 24.22.0 | 36 | Pass; maintained LTS candidate line |
| macOS 27.0.1 / ARM64 | Node 25.3.0 | 36 | Pass; additional compatibility only |
| Same host, Temurin 21 | Retained actual v2 CLI reader | 2 | Rejects both new writer artifacts |

Node 18 and 25 compatibility is not a recommendation to deploy an EOL runtime.
The parent checked lifecycle status separately. The evidence supports listing
the exact macOS ARM64 cells above, with JVM 21/25 and Node 22/24 as candidate
runtime lines. A declaration such as all `Java 21+`, all `Node 18+`, all macOS
versions, or every architecture is wider than this gate.

The Node 18/22/24 distributions came from official versioned `nodejs.org/dist/`
URLs. Their archive SHA-256 values matched the official SHASUMS256.txt entries
resolved before execution. Versioned URLs, hashes and the retained distributions
are in `output/one-zero-platform-2026-10-21/cli/runtimes/downloads.json`.
The JDKs and Node 25 were already installed. Exact binary paths, binary hashes,
complete runtime version output, install paths and host identity are in
`cli/summary.json`; no architecture emulation was used.

## Assertions and compatibility boundaries

The semicolon-free programs and expected JSON are specified independently in
[cli-matrix.py](cli-matrix.py). One engine never supplies another engine's oracle.
Each runtime checks:

- Version/help, real file input and stdin, string projection, missing/null/falsy
  defaults, filter/projection including zero and negative quantities, and empty
  input. The filter result is two ordered rows with totals 14 and 3.
- The previous Node corrections: ordinary fraction 1.5, signed FLOOR/CEIL and
  signed half-even ROUND ties at precision -2. Expected results are literal
  independent values, including 250 → 200 and -250 → -200.
- Explicit strict contracts succeeding and rejecting a wrong input kind in both
  source execution and a real saved compiled artifact. Compile writes actual
  files; exec reads them through the shipped CLI and VM path.
- Structured inspect success and normalized source; malformed inspect returns
  status 70 and a structured stdout diagnostic envelope with `success=false`,
  `parse_error` and subset `UNKNOWN`.
- Exact status/error-category assertions for usage (64), malformed input and
  rejected artifact versions (65), runtime/assertion/strict-contract failure (70)
  and I/O (74). Five real I/O paths cover absent run/compile source, absent input,
  absent compiled artifact, and compiled output blocked by an ordinary parent
  file. Failed execution commands emit no stdout result.
- Genuine legacy v1 and v2 fixtures, v2 fraction 1.5 and integer 42, v3 BigInt
  exact JVM output and explicit Node rejection outside signed Long, rejection of
  v3 numeric tags relabelled as v2, and rejection of a future version.
- Current source compile/save/load/exec for filter and numeric programs on every
  runtime, and strict VM execution of crossed artifacts. All JVMs consume the
  Node 18 writer's files; all Node versions consume the Temurin 21 writer's files.
  This is a bounded crossed-producer check, not every pair of seven producers.
  The actual retained v2 reader rejects both writers' new v3 artifacts.

The prior-stage 1,280 passed semantic executions, codec malformed-input probes
and 532 exact JVM value/kind/scale checks are reused as recorded in
[the preceding evidence](../one-zero-surface-2026-10-18.md). This gate adds
packaged runtime/platform evidence; it does not rerun that entire semantic gate.
Native stdin failure was not forced; ordinary stdin success was executed, and its
native file operation uses the same narrowly tested error normalization helper.

## Defect found and repaired

The corrected pre-fix harness ran 223 cases: 219 passed and all four Node
versions failed the same real missing-file check. The native Node `ENOENT`
escaped Kotlin `catch (Exception)` in `readTextFileOrThrow` and then escaped
`BranchlineCli.run`, producing an uncaught JavaScript stack and exit 1 instead of
the documented structured I/O failure and status 74. The original output and
commands are preserved in `cli/pre-fix/results.json`; the parent retained the
pre-fix tarball as `cli-before-node-io-fix.tgz`.

The coordinated repair changes only `JsPlatform.kt` and adds
`NodeFileIoJsTest.kt`. At covered read/write/stdin native filesystem boundaries,
an object with string `code` and `syscall` plus numeric `errno` becomes a Kotlin
RuntimeException. Existing common I/O helpers then provide the established I/O
classification. Unexpected native errors and Kotlin exceptions retain their
identity; no general catch-all classification was added. Directory enumeration
and append have no common I/O wrapper and remain outside this bounded repair.

Three new JS regression tests cover five actual CLI I/O failures with status 74
and JSON envelopes, successful filesystem operations, native read/write failures,
and negative controls for a native TypeError and a Kotlin exception. The parent
reviewed the change and ran `:cli:jvmTest :cli:jsNodeTest :cli:blShadowJar
:cli:packageJsCli`; BUILD SUCCESSFUL is recorded in `cli-fix-gate.log`.
The final 251-case gate adds four I/O scenarios per runtime to the corrected
223-case harness and uses the rebuilt package. All 251 pass.

The first exploratory harness had three incorrect assumptions: LET and OUTPUT
were placed on one line without a separator; inspect failure was assumed to emit
no diagnostic stdout; XML leaf content was assumed to be a plain string rather
than accessed through `["$"]`. These harness errors were corrected without
product changes and their raw output is retained in `cli/initial-harness-run/`.
They are separate from the reproducible native I/O defect.

## Frozen artifacts and reproduction

| Artifact | SHA-256 |
| --- | --- |
| `branchline-cli-0.11.0-alpha.1-all.jar` | `e3b726e448be86bb9daafa7c5925ef8f8efc763a4ae4b6a400a52c5f0ff85812` |
| Final `branchline-cli-js-0.11.0-alpha.1.tgz` | `82ee715e5d100bf130c9291a9bdb7c5e4749a6a510e65bb2a1bc37676c3b1a1f` |
| Pre-fix Node tarball | `0d878b48d7ba05139bed08c8a923b6171b5bcb88fccf1977ad2e41016f9d2533` |
| Frozen `cli-matrix.py` | `4de87bcb9a3042e85577bff9e1b499dff41f7a451c89c48e1e332fa46aa7e15f` |
| Genuine v1 fixture | `477414dd0f4e06c628a75cafcd522c7f6e4355c677c7f3bad1e2a7217d929e03` |
| Genuine v2 fixture | `83044ebb3cac1da6364e2ca23d74f8e3cf4eb8d11a79f0ddddabd09204534c35` |
| Retained v2 reader | `5d70cd71cede65b20a4ddb31cb749efb60f335a2287205ec948647ff8e672125` |

From the repository root with the recorded runtimes present:

```sh
python3 development/evidence/one-zero-platform-2026-10-21/cli-matrix.py \
  --jar cli/build/libs/branchline-cli-0.11.0-alpha.1-all.jar \
  --tarball cli/build/distributions/branchline-cli-js-0.11.0-alpha.1.tgz
```

`--config` accepts a JSON array of `{id, kind, executable}` runtime records for
another actual host; `kind` is `jvm` or `node`. It still requires repository
fixtures and at least one runtime of each kind for crossed-artifact checks.
Generated scripts/artifacts remain under the recorded temporary work directory.
Every real command, cwd, elapsed time, stdout, stderr and assertion result is
retained in `output/one-zero-platform-2026-10-21/cli/results.json`.
`summary.json`, `run.log` and `frozen-hashes.json` identify the final run and inputs.

## Unexecuted cells

Linux x86-64 and ARM64, Windows x86-64 and ARM64, macOS x86-64, other macOS
versions, and other JVM/Node versions were not exercised by this agent. In
particular, existing Java 23 CI configuration is not a fresh Java 23 execution.
Parent diagnostics found that Docker info/status and its socket ping timed out;
opening the existing app did not yield a usable daemon. No container was run and
no Linux claim is inferred from a macOS process. A real Linux server gate remains
required before admitting Linux; native Windows needs its own execution or must
be explicitly excluded from the candidate matrix. This evidence does not replace
the separate operational pilot or authorize a stable release.
