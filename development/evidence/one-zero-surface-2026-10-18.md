---
status: Implemented
depends_on: ['planning/one-zero-surface-codec-2026-10-18']
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-10-18
---
# Stable-candidate inventory and exact-number persistence acceptance

Accepted locally against the working tree based on
`9de86a0cec25328896a98f909fceec73df0da781`. Three GPT-6.1 Sol High agents owned API,
stdlib and codec implementation. The parent reviewed the source and scope,
ran serialized integration gates and independent persisted-number probes.
The earlier contract/onboarding changes remain part of this working tree.
This stage does not publish a release or declare Branchline 1.0 ready.

## Delivered contracts

- [API allowlist](../../docs/guides/api-support.md): 27 exact declaration blocks,
  including constructors, parameters/defaults, result DTOs and enums; supported
  inspect and interpreter execution entrypoints, CLI command/option behavior,
  explicit exclusions and no whole-jar binary ABI promise. Consumer tests compile
  and execute on JVM/Node; JVM inventory checks compare the documented signatures
  with source and exercise CLI compile/save/load/execute.
- [Stdlib inventory](../../docs/language/stdlib-support.md): all 68 default registry
  names, including COUNT, classified as 50 stable candidates and 18 experimental.
  Each row fixes arity, admitted domain, result/null/error behavior and platform.
  JVM/Node tests compare full documented rows, names and metadata, exercise all
  50 candidates with independent expected language results in interpreter and
  strict VM, and cover invalid inputs, callbacks and negative inventory controls.
  Gradle tracks inventory documents and API source files as test inputs.
- [Artifact v3 migration](../../docs/guides/migrations.md): explicit `bi`/`bd`
  textual tags. JVM BigInt value/kind and BigDec value/kind/scale survive nested
  bytecode values and real suspended snapshots. JS admits signed Long integers
  and finite decimal text that roundtrips through its Double decimal rendering;
  unsupported values fail explicitly. Ordinary i64/f64 meanings remain unchanged.

These are bounded candidate commitments, not universal guarantees for all public
symbols or every possible argument. The execution facade is actually in package
`playground`; its accepted domain excludes tracing/shared mutable global state.

## Compatibility matrix

| Producer/payload | New JVM reader | New Node reader | Older reader |
| --- | --- | --- | --- |
| Genuine legacy v1 fixture, ordinary supported values | Accepted | Accepted | Historical fixture retained |
| Genuine v2 fixture with f64 1.5 and i64 42 | Accepted, independent CLI oracle | Accepted, independent CLI oracle | Fixture produced by retained v2 writer |
| v3 arbitrary BigInt | Exact kind/value | Accept within signed Long; otherwise reject | Actual saved v2 reader rejects version 3 |
| v3 BigDec, including trailing zeros/negative scale | Exact value/kind/scale | Decimal-rendering admission; no scale guarantee | Strict original v1 decoder rejects format marker; v2 rejects version |
| New numeric tags relabelled as v1/v2, also nested | Reject | Reject | Not a valid migration |
| Missing/aliased/malformed bi/bd payload | Reject | Reject | Not an accepted artifact |

The genuine v2 fixture SHA-256 is
`83044ebb3cac1da6364e2ca23d74f8e3cf4eb8d11a79f0ddddabd09204534c35`.
The retained v2 reader SHA-256 is
`5d70cd71cede65b20a4ddb31cb749efb60f335a2287205ec948647ff8e672125`.
It was built before the v3 edit from the prior-stage working tree based on
`9de86a0`, not from a pristine checkout of that commit. Prior-stage hashes are in
`output/one-zero-2026-10-18/source-hashes.json`. The genuine fixture is checked into
the CLI test resources and embedded for common tests; the saved reader is retained
as a local acceptance artifact under the output directory below.

Legacy readers cannot restore precision, explicit numeric kinds or scale that an
old writer already discarded. Source decimal literals are parsed before this
codec and may already have Double precision; the exact decimal artifact test
therefore also uses a typed AST. Closures, arbitrary host objects, cyclic values,
map key identity and universal snapshot migration are outside this repair.

## Defects exposed by acceptance

1. The genuine v2 fraction decoded and executed correctly on Node, but pretty and
   compact JSON converted 1.5 to 1. Primitive type checks overlapped in Kotlin/JS.
   JsonOutput now classifies ordinary numbers before integer conversion; tests
   cover fractions, finite bounds, modes/formats and nonfinite rejection.
2. Ordinary Node FLOOR/CEIL returned fractions unchanged for the same type-check
   reason. Canonical integrality classification now precedes rounding, with eight
   signed fractional oracles in interpreter and strict VM.
3. Node ROUND(250,-2) returned 300, and ROUND(-250,-2) returned -300. BigInt wrapper
   reference equality misclassified an even quotient. Numeric comparison repairs
   half-even rounding. Precision now rejects fractions/nonfinite values rather
   than passing an overlapping Int test. The new suite exercises 52 signed ties
   spanning every admitted precision -12..0 through both engines.
4. Initial API fixtures assumed the wrong SHARED delimiter/CLI inspect exit code.
   Fixtures and inventory were reconciled with actual behavior, without changing
   product behavior to satisfy those assumptions.

The inventory also records RANGE zero/overflow and empty PAD termination issues
as experimental exclusions in the durable gap registry. The nonterminating PAD
path was identified from source and was not executed. No promotion is implied.

## Verification

```sh
CHROME_BIN='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' ./gradlew \
  :interpreter:jvmTest :interpreter:jsNodeTest \
  :conformance-tests:jvmTest :conformance-tests:jsNodeTest \
  :vm:jvmTest :vm:jsNodeTest :cli:jvmTest :cli:jsNodeTest :cli:blShadowJar \
  --continue --console=plain
python3 output/one-zero-surface-2026-10-18/accept-codec.py
```

Final Gradle gate: BUILD SUCCESSFUL, with JUnit XML independently inspected:

| Module | JVM executions | Node executions | Failures/errors |
| --- | ---: | ---: | ---: |
| interpreter | 219 | 67 | 0 |
| VM | 238 | 128 | 0 |
| conformance | 394 (one existing skip) | 139 | 0 |
| CLI | 73 | 23 | 0 |

Total: 1,281 executions, **1,280 passed**, one pre-existing
TreeReconstructionLoadTest skip, zero failures/errors. Up-to-date unaffected
tasks retain their preceding valid results; changed tasks reran. This is a
JVM/Node gate, not a fresh browser/platform release matrix.

Independent parent checks:

- **31 actual CLI compatibility cases**: new numeric values and rejects on JVM
  and Node, malformed inputs, legacy-version relabelling, genuine v2 expected
  output, real source compile to v3 and actual old-reader rejection.
- **532 JVM exact value/kind/scale checks**, including seeded random BigInteger
  and BigDecimal pairs, extreme scales, zeros and nested values. Source is
  `ExactNumbersProbe.java`; compiled against the built CLI jar using `javac
  -proc:none`, then executed via Java. No expected value comes from another
  Branchline engine.
- MkDocs strict build passed using the existing pinned documentation environment;
  `git diff --check` passed. No performance claim or deployment/browser acceptance
  is made by this stage.

Raw logs, saved JUnit XML, probes, fixture, old reader, counts and artifact/source
hashes: `output/one-zero-surface-2026-10-18/`. Final gate is `final-gate.log`, CLI
results `cli-codec-oracles.json`, exact-value result `exact-probe.log`, and docs
result `mkdocs.log`.

The stable-surface inventory gate is now closed for the declared allowlist.
Actual supported platform/release validation and the independent operational
pilot remain required before 1.0; arbitrary-precision JS parity remains open.
