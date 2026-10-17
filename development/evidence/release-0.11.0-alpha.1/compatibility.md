# Compiled artifact compatibility gate

Scope: release plan `development/planning/release-0.11.0-alpha.1.md`, item 3.
The new writer emits format 2 and a mandatory `branchline-vm` format marker.
The reader accepts legacy format 1 (including omitted version), rejects future
versions before parsing the embedded program, and reserves PRIVATE_* operations
for format 2. Tests run in commonTest on JVM and Node.

Legacy fixture generation and validation results will be recorded below.

## Wire policy

- New writer: version 2 and explicit `format: "branchline-vm"`, including compact
  and canonical JSON output. No version-1 writer/downgrade path is offered.
- New reader: omitted version means legacy 1, explicit 1 and 2 are supported;
  non-integer/unsupported versions and missing/wrong v2 marker are rejected with
  upgrade/recompile guidance before embedded program construction. Version 1
  rejects PRIVATE_* instructions. Unknown bytecode operations/value tags reject
  rather than silently deserializing an unknown value tag as a string.
- Previous reader: strict Json decodes the complete envelope before bytecode
  execution, but did not check `version`. The extra v2 marker deliberately causes
  its decoder to reject. Its historical error is `unknown key 'format'` (exit 70),
  without a tailored migration explanation. We cannot retrofit that old binary.
- Migration: upgrade the reader, or extract/save the embedded script and compile
  it with the target CLI's `blc`. Removing the marker/changing the version is not
  a supported downgrade. This gate does not introduce snapshot migration.

## Honest previous-writer fixture

Previous source revision: `ce58599df24a16cb7ffd1f5d4165bdbf84d90ad0`.
`git archive HEAD` was extracted into an isolated temporary directory, then parent
ran `./gradlew :cli:blShadowJar` there successfully. Log:
`/tmp/branchline-old-writer-build.log` (local reproducibility evidence, not a
release artifact). The temporary checkout was
`/var/folders/l0/2_7b6j512jjgg1_qwx96frd40000gp/T/branchline-old-writer-5v8snmee`.

Run the old JAR's compiler main against the checked-in source:

```sh
java -cp "$OLD_CLI_JAR" io.github.ehlyzov.branchline.cli.BlcJvmMainKt \
  cli/src/jvmTest/resources/fixtures/compatibility/legacy-hello.bl \
  --output cli/src/jvmTest/resources/fixtures/compatibility/legacy-hello.blc \
  --output-format json-compact
```

Output fixture SHA-256:
`477414dd0f4e06c628a75cafcd522c7f6e4355c677c7f3bad1e2a7217d929e03`.
The actual old writer omitted `version` and emitted six instructions (PUSH,
PUSH, LOAD_VAR, ACCESS_STATIC, ADD, OUTPUT_1), plus its inferred contract.
`LegacyArtifactFixture.kt` embeds these exact bytes for both common targets;
a JVM resource check protects the duplicate from drifting.

Standalone old-reader checks with input `{"name":"Kotlin"}`:

| Artifact | Exit | Output / error |
| --- | --- | --- |
| Actual old-writer fixture | 0 | `{"greeting":"Hello, Kotlin"}` |
| Fixture changed only to explicit version 2 | 0 | `{"greeting":"Hello, Kotlin"}` |
| Version 2 + marker + PRIVATE_SET_DYNAMIC | 70 | `Encountered an unknown key 'format'` |

The latter two JSON files were intentionally synthetic compatibility probes, not
claimed as old-writer output. Frozen old envelope tests independently preserve
its exact strict-decoder/version behavior on JVM and Node. New-writer tests use
all three PRIVATE_* instructions and assert old-envelope marker rejection.

## Gate commands

Parent runs Gradle serially after all release edits are ready:

```sh
./gradlew :cli:jvmTest --tests '*CompiledArtifactCompatibility*' --tests '*BranchlineRuntimeTest' :cli:jsNodeTest
```

Tests have been prepared; JVM/Node pass/failure must be recorded from the parent
release gate before publishing. The old standalone binary checks above passed.
