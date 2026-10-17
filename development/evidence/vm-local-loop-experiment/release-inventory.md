# Local next-release inventory

Recorded 2026-10-17 before the JVM-local-loop implementation. This is a local,
read-only assessment of release inputs, not approval to tag/publish or a new
full-release verification. Existing dirty/untracked work is the candidate;
committed HEAD alone does not contain these accepted changes. No builds, JVM
processes, remote tag queries, workflow dispatches or publication ran here.

## Version and source identity

- Root `build.gradle` sets all projects to **`v0.11.0-SNAPSHOT`**. CLI npm package
  and lock templates receive that literal project version from `cli/build.gradle`;
  JVM/Node artifact names also use it.
- Local HEAD is `ce58599df24a16cb7ffd1f5d4165bdbf84d90ad0`, dated 2026-06-11,
  `Expand JSONata representative benchmarks +jsonata`. Its local exact tag and
  `git describe` result are `perf-ce58599`, a performance marker.
- Latest local version-style tag is **`v0.11.0-RC`**, pointing to
  `d7413b6c711d3f940ef44927a2285650f2ffc11a` (2026-01-13). The prior
  `v0.10.0` points to `17ee52544865399c00fe4210448257bf5aac0da5`
  (2026-01-03). Other local version tags include `v0.9.1-alpha` and
  `v0.9.0-alpha`; numerous snapshot/performance tags are not version releases.
  Remote tags and published asset availability were not checked.
- The current release-readiness guide explicitly retains **Alpha** posture.
  Neither the RC tag name nor passing local tests establishes production
  readiness. Choose and align the next project version/tag only after scope and
  compatibility review; no version change is made by this inventory.

## Candidate changes with accepted evidence

These are candidates for a release proposal, not an instruction to include every
dirty file. Evidence belongs to each recorded source snapshot and must be tied
to the final candidate after local-loop work.

| Surface | Accepted work | Evidence and material limits |
| --- | --- | --- |
| Numeric behavior and deterministic authoring subset | POWER/SQRT domains and exact JVM behavior, documented JS restrictions, recursive clock/random rejection, conservative callable-shadowing handling | `development/planning/branchline-next-stage.md`; `docs/language/numeric.md`; `docs/language/ai-canonical-subset.md`. JS arbitrary precision is not delivered. |
| VM fallback correctness | Explicit compilation-only fallback/strict policy and path reports; runtime failures do not replay host effects through interpreter; nullable/shadowing, implicit return and public error-category fixes | `docs/vm.md`; `development/architecture/vm-interpreter-split.md`; next-stage integrated acceptance. The removal of runtime-error recovery needs migration/release notes; do not silently call all behavior changes backward compatible. |
| Existing VM performance and lifetime defects | PC-indexed numeric sites, live host lookup, caller-state reuse, proof-limited private collection writes, bounded preparation/evaluation caches | `development/evidence/vm-performance/README.md`; `independent-audit.md` in that directory. Targeted scaling wins are not a general interpreter/JSONata/Kotlin advantage. |
| Nested environment/compiler scaling | Iterative overlay enumeration and ownership rollback logs remove reproduced exponential/quadratic costs | `development/evidence/vm-performance-audit2/README.md`; plan final acceptance records equivalent bytecode/output and JVM/Node gates. |
| Trace bookkeeping | Skip unused trace-map lookups and safe old-value reads while retaining observer boundaries | `development/evidence/vm-hotpath-optimization/README.md`. Matched native VM improvements are 14–27%; interpreter still wins these native timings. Rejected dispatcher variants were discarded. |
| Compiled CLI fan-out | Worker-owned runtime/cache state, ordered completion/error handling through interruption, serial collecting traces; synchronized bounded JVM regex cache | `development/evidence/vm-json-pipeline/jobs-isolation.md`, before/after XML and independent review. Same-process independent CLI invocations still share the provider; arbitrary custom tracers remain caller responsibilities. |
| Functional pilot/tooling | Required real Branchline/JSONata/Kotlin engines, explicit independent expected JSON and corrupt-output sensitivity; local Ktor replay/contracts/codegen | `development/evidence/branchline-price-pilot.md`: 65 inputs, 50 business inputs on each engine plus 15 common boundary rejects; Ktor 24 tests. Synthetic local correctness is not external operational acceptance. |

The next-stage record includes an integrated interpreter/VM/conformance/CLI JVM/
Node, pilot and docs gate. Later experiments ran narrower VM/conformance/CLI
gates. The latest completed loop-body snapshot records 862 passing tests and
one existing skip, 299 independent parent comparisons, 32 fixture configurations
with three calls each, and an old-jar baseline check. These counts are not an
entire fresh packaging/CI acceptance for a future local-loop candidate.

## Opt-in research: no default promotion

| Prototype | Recorded result | Release implication |
| --- | --- | --- |
| Selective JSON pipeline | Large sparse inputs improve; dense 1 MiB cases regress 44–56% | Explicit experimental sparse eligibility only; `development/planning/vm-json-pipeline.md` and `development/evidence/vm-json-pipeline/pipeline-harness.md`. |
| Slots/fusion | Apparent short-run gain did not repeat; allocation and executor memory increased | No default promotion; `development/evidence/vm-slots-fusion-experiment/README.md`. Also has an unresolved numeric-subclass observation risk in the gap registry. |
| Prepared blocks | Large reduction improves about 26% versus ordinary VM but remains slower than interpreter; joint paths/projection gate fails, prepared memory grows | No default promotion; `development/evidence/vm-prepared-block-experiment/README.md`. Huge-method defect was corrected before final refreeze, rather than pooled with the first candidate. |
| Generated whole loop | Body dispatch is removed, but projection improvement is about 11–16% versus ordinary VM without a repeatable 15% target or proven interpreter advantage; preparation/plan memory grows | JVM-only opt-in; `development/evidence/vm-loop-body-experiment/README.md`, `PROFILE.md`, `freeze-verification.json`. One class/loader per plan, no global cache. |
| JVM locals/typed loop | New authorized experiment, not measured at inventory time | Defer its disposition until independent correctness, fixed long comparisons, preparation and five-stage heap/class/RSS observations complete. |

Decide explicitly whether research APIs belong in the distributable VM library
or in a separately bounded experimental surface. Keeping a mode off by default
does not erase its public API, dependency or known-correctness obligations.
The JVM experimental classes are currently under the ordinary `vm` module's
`jvmMain`, and its ASM implementation dependency ships with that module's runtime
dependency metadata. They are not excluded merely because execution is opt-in.
The release proposal must explicitly choose exclusion/a separate module or a
labelled unstable API/dependency. The inventory does not rewrite these sources.

## Known gaps to carry into the release proposal

Canonical unresolved facts remain in `development/service/knowledge-gaps.yaml`:

- Earlier `executeExperimental` scalar guards accept JVM BigInteger/BigDecimal
  subclasses without the new runners' corresponding observation protections.
  Reproduce/fix or retire that opt-in API before advertising arbitrary JVM value
  support; its old bounded acceptance is not a blanket correctness guarantee.
- JS BigInt/BigDec remain Long/Double backed. Preserve the supported numeric
  profile and explicit JVM/JS differences.
- Closure/template values are not universally executable after persisted
  snapshots; no general snapshot-schema migration guarantee exists.
- New `PRIVATE_*` opcodes were appended so old numeric opcode ordinals are
  preserved, but BytecodeIO writes new instruction names. An older reader's
  acceptance of newer compiled artifacts is not established. Require an explicit
  old-writer/new-reader and new-writer/old-reader gate with a declared support/
  rejection policy, artifact fixtures and migration notes before calling the
  compiled format backward compatible or the release nonbreaking.
- No capability/resource-isolation contract establishes safety for untrusted
  externally supplied programs.
- External adoption, operational reliability and authoring advantage are open.
  The completed 270-session authoring study does **not** show general superiority:
  see `development/experiments/authoring-2026-10-17/REPORT.md`. Its execution is
  complete even though the advantage gate failed. Earlier pilot prose saying a
  blind study had not run is historical, superseded by that report.
- Cold/JIT amortization, wider workload eligibility, cache/lifetime policy and
  production relevance remain separate from warm JVM microbenchmarks.

## Packaging and publication contract

- `.github/workflows/tests.yml`: Java 23, Node 18, configured Chrome; interpreter,
  VM and conformance each run `clean` + `allTests`, CLI runs JVM/Node tests, and a
  separate job builds `:cli:blShadowJar`. Local no-Chrome gates do not establish
  the browser/CI result.
- `.github/workflows/release-artifacts.yml`: release published/prereleased or
  manual dispatch can build JVM CLI fat jar, Node CLI tarball, interpreter/VM JVM
  jars and interpreter JS tarball, then upload tag-labelled assets. Main pushes
  only run its performance branch when the commit message has `+performance`
  or `+jsonata`. Manual dispatch can create a prerelease and upload/clobber
  assets; it is a write operation, not a harmless validation command.
- The release workflow's manual `tag` input chooses the label/release context;
  its checkout has no matching `ref` input. Also, tag-based output renaming does
  not rewrite project/package versions. Before dispatch, independently verify
  that selected workflow ref, commit, project version, package metadata and tag
  agree. This is a workflow-source observation, not a reproduced failed release.
- Manual Maven CLI publication uses `:cli:publish` to GitHub Packages; manual npm
  publication prepares the package then calls `npm publish`. Each accepts a
  source ref. Credentials, permissions and actual registry behavior are untested
  by this inventory and require explicit publication authorization.
- ASM 9.9 is now a JVM-only runtime dependency of the experimental compiler.
  Check its inclusion in the CLI fat jar and consumer dependency resolution/
  instructions for the standalone VM jar if that API ships. A raw library jar
  upload is not a bundled dependency distribution.

## Remaining candidate gates (not run here)

1. Finish local-loop acceptance and disposition, select the release source scope,
   and bind every result to its final source/jar hashes. Preserve the Alpha label
   and compatibility/migration limits in release notes.
2. Run the appropriate final interpreter/VM/conformance/CLI JVM/Node gate and
   promised browser/CI matrix against that candidate. Existing local accepted
   logs remain evidence for their earlier snapshots, not a substitute.
3. Packaging checks from the existing repository contract:
   `./gradlew :cli:blShadowJar :cli:packageJsCli :cli:jvmTest :cli:jsNodeTest`
   and release-library tasks
   `./gradlew :interpreter:jvmJar :vm:jvmJar :interpreter:packageInterpreterJs`.
   Inspect the actual archives, package metadata and launch/version behavior;
   run artifact-level smoke transforms rather than only source-classpath tests.
4. Run `./gradlew docsBuild`, appropriate contour refresh/audit and whitespace
   checks; keep docs/playground examples executable. `./gradlew clean build` is
   the documented full local build, with environment-specific browser limits
   disclosed. Re-run functional pilot/negative-sensitivity/Ktor gates if their
   dependency surface changed or as the final agreed release gate.
5. Check final version/tag/source/asset alignment and external CI/registry
   permissions before any separately authorized release. Neither tag creation,
   commit, push, workflow dispatch nor publication is authorized by this task.

Inventory commands were limited to `git status`, local tag/log/describe inspection,
`rg`, and reads of build/workflow/docs/evidence sources. No claim is made that
the future candidate is release-ready or that any external registry is current.
