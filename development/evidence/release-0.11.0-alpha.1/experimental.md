# Experimental VM alpha boundary and numeric observer repair

Scope: `development/planning/release-0.11.0-alpha.1.md`, release item 2.
The parent owns release documentation, contour/index reconciliation and serialized
Gradle verification. This slice does not change ordinary VM selection.

## Packaging decision

Ship the existing research preview in `:vm`, with an explicit Kotlin
`@RequiresOptIn(level = ERROR)` boundary named `ExperimentalVMApi`. Its KDoc
states that source/binary compatibility is not promised and APIs may disappear.
Opt in at the call site or file using `@OptIn(ExperimentalVMApi::class)`.
Java callers must accept the documented preview contract themselves: Kotlin's
opt-in diagnostic does not enforce Java source usage.

The marker covers options/reports/plans, VM research entrypoints and their public
status properties, the two JVM code generators, and the generated-code support
interfaces/helper object. Ordinary `VM`, `execute`, bytecode/compiler integration
and default dispatch retain their existing API. Internal implementation and
explicit research tests/benchmarks opt in locally; no module-wide consumer
compiler flag suppresses diagnostics. The JVM module already pins and ships
`implementation 'org.ow2.asm:asm:9.9'`; this is a shipped JVM runtime dependency,
not an excluded development tool. Parent documentation must disclose that scope.

## Observed pre-fix failure

A retained standalone Java probe, `LegacyNumericObservationProbe.java`, invokes
ordinary and experimental execution against the existing pre-fix built JMH jar.
It overrides `BigInteger.add`, observes and mutates named binding `x` after a
mirrored local/named store, and compares the final result and observations.

Commands actually run (2026-10-17; no Gradle execution):

```sh
javac -cp vm-benchmarks/build/libs/vm-benchmarks-v0.11.0-SNAPSHOT-jmh.jar -d /tmp/branchline-release-numeric-probe development/evidence/release-0.11.0-alpha.1/LegacyNumericObservationProbe.java
java -cp /tmp/branchline-release-numeric-probe:vm-benchmarks/build/libs/vm-benchmarks-v0.11.0-SNAPSHOT-jmh.jar LegacyNumericObservationProbe
```

Observed nonzero exit, with:

```text
ordinary: result=[7, 11], observed=[7], x=11
experimental: result=[7, 7], observed=[null], x=7
java.lang.AssertionError: Legacy numeric observer mismatch
```

This confirms the earlier source-review residual against the retained built
prototype. It is not a new performance measurement or a claim about unbuilt
candidate bytes.

## Repair and regression scope

The legacy runner now uses the existing prepared runner's exact-class scalar
checks, and its truth guards before AND/OR/NOT and conditional truth branches.
Encountering a JVM BigInteger/BigDecimal subclass materializes aliases and
flushes named writes, then resumes canonical execution at the current source PC
before any custom numeric call. Completed operations are not replayed.
Static IBig keys containing a subclass receive whole-plan fallback before
preparation/fused-path dispatch can invoke their numeric methods.

`ExperimentalBigNumberObservationTest` compares ordinary execution with slots,
fusion, and their combination. It exercises raw BigInteger and BigDecimal
subclasses for five truth operations, trace activation within a callback,
callback-time canonical PC/stack/local/environment snapshots, arithmetic
observation and mutation, exact thrown exception identity/partial final state,
one callback per ordinary operation, and static-key preparation without
callbacks. Existing common experimental tests retain JVM/Node coverage.

## Parent-owned verification remaining

Do not interpret static inspection or pre-fix reproduction as candidate test
success. The parent should run these serially after all agents finish editing:

```sh
./gradlew :vm:jvmTest --tests '*ExperimentalBigNumberObservationTest' --tests '*PreparedBlockBigNumberObservationTest' --console=plain
./gradlew :vm:jvmTest :vm:jsNodeTest :vm-benchmarks:jmhJar --console=plain
```

After building, rerun the Java probe above using the actual newly emitted JMH
jar path rather than the recorded SNAPSHOT path. Expected: both lines contain
`result=[7, 11], observed=[7], x=11` and exit 0.

Also verify the consumer boundary with the repository's Kotlin compiler: a
consumer calling `VM().prepareBlocks(...)` without opt-in must fail with the
preview diagnostic; adding `@OptIn(ExperimentalVMApi::class)` must compile.
Ordinary `VM().execute(...)` must compile without opt-in. This is a separate
compiler acceptance check, not established merely by source annotations.

Static check run: `git diff --check` passed. No Gradle commands, commits, pushes,
publication, dependency upgrades or timing benchmarks were run by this agent.

## First candidate gate correction

The first parent JVM gate executed the new regression and found a fixture
limitation before its experimental comparison: `snapshotJson()` cannot encode
`Instruction.NOP` in the two branch programs, so their ordinary baseline failed
inside the callback. The arithmetic and static-key tests passed. Replaced each
NOP filler with serializable `JUMP(7)`, which retains the same source PC and
stack-neutral branch continuation. This preserves callback-time full-state
comparison without changing product codec behavior. The corrected focused gate
remains parent-owned.
