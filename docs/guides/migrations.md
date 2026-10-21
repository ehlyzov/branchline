---
title: Migration Guides
---

# Migration Guides

## v1.0.0-rc.1 Node CLI file-error correction

Native Node filesystem failures while reading a source/artifact/input file or
writing a compiled artifact now reach the CLI's documented I/O error boundary:
exit status 74 and the structured envelope with `--error-format json`.
Previously, a missing file could escape as an uncaught JavaScript `ENOENT`,
exit with status 1 and print a native stack. Unexpected non-filesystem errors
are not converted into I/O errors. This correction is included in v1.0.0-rc.1;
it does not alter previously published Alpha distributions.

## v1.0.0-rc.1 numeric corrections

The v1.0.0-rc.1 candidate corrects numeric results that differed from the documented
arithmetic rules: `FLOOR(DEC("-1.5"))` returns `-2` and
`CEIL(DEC("1.5"))` returns `2`; multiplication of the ordinary signed 64-bit minimum
by `-1` promotes to `9223372036854775808` in either operand order. Previously,
decimal floor/ceil truncated toward zero and one multiplication order wrapped.
VM constant folding now uses the same numeric helpers as execution: for example,
`1000001 * 1000001` produces `1000002000001` instead of a wrapped 32-bit value.
Recompile old artifacts from source; correcting execution cannot repair an
incorrect constant already stored in bytecode.
On JS, `INT(1.5)` (and numeric `PARSE_INT`) now rejects a fractional input instead of silently truncating
it; `DEC(1.5)` preserves the stored floating value in the decimal wrapper instead
of treating it as an integer. These fix primitive-number classification, not JS
arbitrary precision: decimal arithmetic remains Double-backed.
Node bytecode writers now tag fractional and unsafe primitive floating values as
`f64`, so saving and reloading `DEC(1.5)` no longer tries to parse `1.5` as a Long.
That primitive-tag correction reuses the existing tags. The separate explicit-number
persistence change below introduces artifact v3; recompile previously mis-tagged
artifacts from source.
Pretty and compact JSON output on Node now preserves ordinary fractional numbers
such as `1.5`, which primitive type checks previously truncated to `1`.
Ordinary JS `FLOOR`/`CEIL` now round fractions in the documented direction instead
of returning the fraction unchanged. These output/runtime corrections also apply
when executing a correctly encoded legacy artifact.
JS `ROUND(250, -2)` now returns `200`, and `ROUND(-250, -2)` returns `-200`,
following half-even rounding instead of rounding those ties away from zero.
Fractional precision arguments are rejected instead of being silently truncated.
Replay transforms that use these boundaries before upgrading. JS still has
Long-backed explicit integers and general overflow is not checked; see the
[numeric profiles](../language/numeric.md). These corrections are not a claim
that previously published Alpha artifacts have changed.

## v0.11.0-alpha.1

- Install Java 21+ for JVM distributions. Node distributions require Node 18+.
- Recompile stored transformations from source when moving between Alpha releases.
  New compiled artifacts declare a format/version marker; older runtimes reject
  them. This runtime accepts legacy version-1 artifacts within the documented
  serializable instruction surface. See [compiled compatibility](../vm.md).
- VM compatibility fallback now applies only to compilation failure before
  execution. A runtime exception propagates once; the interpreter does not replay
  the transformation. Code that relied on replay must handle the error explicitly.
- Use the strict VM policy when bytecode execution is required. A successful
  compatibility fallback does not demonstrate VM coverage.
- POWER and SQRT now implement the documented domains and errors. Check
  [numeric semantics](../language/numeric.md), especially JVM/JS precision limits.
- The deterministic authoring profile rejects reachable ambient clock/randomness.
  Supply time and random values explicitly as input when needed.
- Experimental VM backends require explicit Kotlin opt-in. They remain sequential
  and are not a stable API or a replacement for the default VM. Create independent
  VM contexts for concurrent calls; `--jobs` does this for compiled CLI entries.

Keep the previous executable and original source to roll back. Do not expect an
older runtime to read artifacts written by this prerelease, or use snapshots as
portable persistence for executable closures.

## Compiled artifact policy

The v1.0.0-rc.1 writer emits `version: 3` and `format: "branchline-vm"`.
Readers accept versions 1, 2 and 3; an omitted version still means legacy v1.
Versions 2 and 3 require the format marker. Version 1 cannot contain `PRIVATE_*`
instructions. The new explicit-number tags `bi` and `bd` require version 3,
including inside arrays and objects; relabelling an artifact as v1/v2 is rejected.

| Numeric value | v3 representation and guarantee |
| --- | --- |
| BigInt | `bi` with integer text. JVM preserves arbitrary-precision value and BigInt kind; JS accepts only signed 64-bit values and rejects values outside that storage domain. |
| BigDec | `bd` with decimal text. JVM preserves value, kind and scale, including trailing zeros and exponent notation, using the decimal representation rather than conversion through Double. |
| JS BigDec | Keeps the Double-backed decimal kind and supported stored value. Incoming decimal text must agree numerically with the finite Double's decimal rendering; inputs that lose digits, overflow or underflow are rejected. JS does not model JVM decimal scale. |
| Ordinary integers / floats | Existing `i64` / `f64` tags retain their historical meanings; ordinary numeric values do not silently acquire explicit-number kinds. |

New readers preserve the historical meaning of old `i64` and `f64` values.
They cannot recover BigInt/BigDec kinds, decimal scale, precision or correct
constants already lost by an older writer. Recompile authoritative source with
the target version when those distinctions matter. The new tags also preserve
numbers in supported nested containers and VM snapshots; closures and arbitrary
host objects remain outside the supported persistence domain.

The genuine v1 fixture comes from the previous writer at commit
`ce58599df24a16cb7ffd1f5d4165bdbf84d90ad0`. A separate genuine v2 fixture and saved
v2 reader establish the next migration boundary. These fixtures prove their
instruction/value domains, not readability of every historical artifact.
See [compatibility tests](https://github.com/ehlyzov/branchline/blob/main/cli/src/commonTest/kotlin/io/github/ehlyzov/branchline/cli/CompiledArtifactCompatibilityTest.kt).

A v2 reader rejects v3 by version before executing the program. The earlier v1
reader did not enforce version, but its strict decoder rejects the added `format`
field. Never strip the marker or alter the version to bypass this boundary.
Raw BytecodeIO/snapshot payloads are not CLI artifact envelopes: older decoders
reject unknown `bi`/`bd` value tags. There is no automatic downgrade or general
snapshot-schema migration. Unknown tags, malformed explicit numbers and unsupported
platform representations fail explicitly; they must not be silently rounded.

For every upgrade, retain the authoritative `.bl` source, pin the target release,
and compile with that release's `blc`. Release versions and artifact format
versions are independent. A future 1.x runtime may change its writer format or
accepted artifact set if it documents that boundary and supplies this
source-recompile path; the candidate source compatibility policy does not make
compiled artifacts portable throughout 1.x. Artifact removal must be called out
in release notes before rollout. If an integration requires retained artifacts
to remain directly executable, pin its reader/writer pair until that requirement
has a separately tested support commitment.

## Upgrade and rollback procedure

1. Archive the current executable/distribution, original source, explicit
   contracts, configuration, selected transform, input/output formats and number
   mode. Record the JVM/Node version. Retain representative inputs and independent
   expected outputs, including failures and numeric boundaries.
2. Review the target release's supported-surface and migration notes. Check the
   [numeric profile](../language/numeric.md) before moving between JVM and JS;
   serialization settings do not restore precision already lost in computation.
3. Recompile source with the target distribution. For an old artifact whose
   separate source was lost, extract its embedded `script` into a `.bl` file and
   review it before compiling; do not patch serialized opcodes or version fields.
   Compilation for an older target can fail if that source uses newer features.
4. Inspect using `--contracts-json --normalized`, then replay retained cases and
   compare actual outputs and errors with the independent expectations. Check
   `subsetCompatibility` separately from `success`. Rebuild Kotlin consumers;
   the candidate API policy does not promise a universal binary ABI.
5. Verify both interpreter behavior and strict VM execution wherever the workload
   requires VM coverage. For embedding, use `VMFallbackPolicy.NONE` and require a
   successful `VM_BYTECODE` report; CLI `exec`/`blvm` executes the compiled artifact.
   Compilation fallback is not VM evidence. Retry escaping execution failures
   only under an application policy that accounts for prior effects.
6. Before rollout, rehearse returning to the archived distribution and its own
   configuration/artifacts. Recompile the retained source with the old compiler
   if necessary and replay the same cases. Do not feed new `.blc` files or VM
   snapshots to the old runtime as a rollback strategy. Application state,
   external effects and data-schema rollback remain the host application's
   responsibility.

This procedure is a release acceptance gate, not a claim that an operational
upgrade or rollback drill has already happened.

## Candidate 1.0 change notices

For the current stage, replay the [frozen E1 evaluation contract](release-readiness.md#frozen-evaluation-contract-e1)
before accepting an upgrade. Its three introductory programs, explicit outputs,
inspect interface and failure/artifact boundaries are concrete acceptance
obligations, rather than a general stability claim. Keep the release SHA and
actual test results with the upgrade record. The [operational pilot](pilot-plan.md)
adds independent installation, representative replay and rollback requirements.

The [candidate compatibility contract](release-readiness.md#candidate-10-compatibility-contract)
separates future stable commitments from current prerelease behavior. Before stable 1.0,
release notes may announce breaking changes without a stable deprecation window;
always pin full prerelease versions and rerun the acceptance corpus.

After adoption at 1.0, deprecations of admitted source/API/CLI/envelope behavior
must give the replacement, affected version and migration example in a minor
release, and retain the old behavior for the rest of 1.x. A documented breaking
change requires a new major version, subject to the explicit security-exception
policy. Experimental APIs, generated plans, internal AST/IR/opcodes, snapshots and
unadmitted surfaces do not gain compatibility merely by being present in a stable
distribution. Each release must identify those changes separately.

## Related
- [Release Readiness & Stability](release-readiness.md)
- [Install Branchline](install.md)
