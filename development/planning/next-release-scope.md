---
status: Implemented
depends_on: ['planning/branchline-next-stage', 'planning/vm-local-loop-experiment']
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-10-17
---
# Next Branchline release: bounded Alpha/preview scope

The proposal below was selected and hardened as `v0.11.0-alpha.1`; see
[execution plan](release-0.11.0-alpha.1.md) and
[release acceptance](../evidence/release-0.11.0-alpha.1/README.md).
Historical proposal and pre-hardening findings follow.

This is a candidate selection proposal, not publication or a claim that the entire
dirty worktree is ready. Current project version is `v0.11.0-SNAPSHOT`; latest
local version-style tag is `v0.11.0-RC`, on an older commit. Remote release state
has not been queried. Choose final version/tag after compatibility review; retain
Alpha maturity. Local version/workflow evidence: [inventory](../evidence/vm-local-loop-experiment/release-inventory.md).

## Proposed stable-in-this-release contents

| Priority | Candidate | User-visible outcome | Ready criterion / evidence |
| --- | --- | --- | --- |
| P0 | Compiled CLI worker isolation and regex cache synchronization | `--jobs` entries do not exchange VM state; ordered results and cleanup survive failures/interruption | Preserve coordinated overlap/order/error/interruption tests on JVM and Node, artifact smoke; [accepted isolation](../evidence/vm-json-pipeline/jobs-isolation.md). Does not promise isolation of independent CLI invocations sharing process-global providers. |
| P0 | Explicit compilation fallback and strict VM policy | Execution failures propagate once without replaying host effects; execution path is reported | Strict/compatibility and effect-count regressions pass; migration notes explicitly describe changed failure behavior; [VM policy](../../docs/vm.md). |
| P0 | Numeric and deterministic-subset fixes | POWER/SQRT have specified domains/errors; reachable clock/random uses are rejected by the deterministic profile | JVM/Node exact-value/domain tests and executable docs pass; keep JS Long/Double limits explicit. Inspection is not a sandbox; [accepted core work](branchline-next-stage.md). |
| P1 | Ordinary VM scaling/lifetime fixes | Private append/write avoids repeated copying where ownership is proven; preparation caches stay bounded; overlay enumeration and compiler scopes stop explosive growth | Retain ownership/alias/observer/snapshot tests and previous scaling evidence; [first audit](../evidence/vm-performance/README.md), [second audit](../evidence/vm-performance-audit2/README.md). |
| P1 | Unused tracing bookkeeping removal | Default VM avoids trace lookups when no tracer observes execution | Trace activation/custom-callback parity tests and baseline comparison; [accepted native JMH](../evidence/vm-hotpath-optimization/README.md). Scoped 14–27% earlier VM improvement, no general interpreter superiority claim. |
| P1 | Reproducible pilot and authoring examples | Users can run price-ingest comparison and learn a tested syntax subset | Keep real three-engine expected-output/negative-sensitivity check and runnable examples; publish limits and failed advantage gates honestly. External pilot is still pending. |

## Research disposition

JVM-local-loop correctness and measurements are accepted. On fixed projection,
localLoop took71–72us versus interpreter114–118us, ordinaryVM125–126us and prior
loopBody104–109us in two orders. It reduced VM B/op6.1%, but preparation rose to
95.9us versus4.0us and1000prepared plans use11.605MiB heap versus6.095MiB, plus
5.655MiB incremental Metaspace versus0.269MiB. Treat this as a promising opt-in
preview candidate for reused JVM transformations, not a default replacement.
Exact eligibility, cold cost and workload limits are in the
[completed report](../evidence/vm-local-loop-experiment/README.md). The old
slots/fusion, prepared blocks and whole-loop experiments have failed their stated
joint/default promotion gates. Selective JSON has a real sparse-input benefit but
a large dense-input regression. None should replace ordinary execution by default.

Preferred release boundary: keep research backends outside the supported core API
and normal distributable dependency surface, in an explicit experimental module
or separate research branch. That extraction is proposed follow-up work, not
implemented by this experiment. If an Alpha preview deliberately packages one,
it needs an unstable API label, pinned ASM dependency disclosure, explicit opt-in,
own tests and documented eligibility/lifetime costs. Current placement in `vm`
means **off by default is not excluded from the published artifact**.

Do not ship the legacy `executeExperimental` as an advertised supported API until
the recorded JVM bignum-subclass observer issue is reproduced and fixed, or that
prototype is removed from the distributable. Keep prior evidence for audit even
if the code is retired; do not delete benchmark history to improve the story.

## Release gates in priority order

1. **P0 — select source/API boundary.** Make the final diff reviewable, preserve
   unrelated user reports, choose research exclusion or explicit preview scope.
   Resolve the legacy experimental observer risk for every API actually shipped.
2. **P0 — compatibility.** Add old-writer/new-reader fixtures and define the
   new-writer/old-reader policy for PRIVATE_* instructions. Appended opcode values
   preserve old ordinals but do not make an old reader understand new names.
   Verify compiled-artifact version/rejection/migration behavior and runtime-error
   fallback release notes. No general closure snapshot persistence guarantee.
3. **P0 — final candidate verification.** Bind interpreter/VM/conformance/CLI JVM
   and Node results, archive smoke and docs to the chosen candidate hashes; run
   CI browser/Java matrix. Local tests below are not the entire release gate.
4. **P1 — packages.** Build CLI JVM/Node and runtime libraries, inspect metadata,
   verify representative compiled/source transformations from packaged artifacts.
   If generated code ships, test ASM resolution for consumers and fat-jar loading.
5. **P0 — source/version/tag alignment.** Verify workflow ref, project/package
   version, commit and tag agree. Manual release workflow tag input currently
   labels artifacts without selecting checkout ref; review/fix before dispatch.
   Artifact smoke confirms CLI reports v0.10.0 while package version is
   v0.11.0-SNAPSHOT; fix this mismatch before release. Entrypoint class major65
   confirms Java21 target while install.md says Java17+; align docs/target.
   Registry permissions and actual publication need separate authorization.

Documentation examples deserve an executable gate: the prior blinded authoring
study had to discard an initial series because supplied syntax examples were
wrong. This is concrete release quality evidence, not a new general AI claim.

## Deliberately deferred

- Default JVM code generation, production code caches and universal performance
  claims: need independent real transforms, cold/JIT break-even, broader profiles
  and retained-state policy.
- Default selective JSON: require a validated eligibility policy and repair or
  avoidance of the confirmed dense regression.
- JS arbitrary precision, general executable closure snapshots and untrusted-code
  resource/capability isolation: separate architectural deliverables.
- Production maturity or superiority over Kotlin/JSONata: two external integrators
  and operational evidence remain open. The 270-session authoring experiment is
  complete and did not establish general superiority; do not present it as unrun.

## Current experiment evidence

Current local candidate passed 1155 tests with one existing skip across
interpreter/VM/conformance/CLI JVM and Node; docsBuild and CLI JVM/Node packaging
passed. Parent 588 comparisons and 32 fixture configurations x3 passed. Packaged
JVM/Node source transforms and same-version cross-runtime compiled roundtrips
passed; these do not establish old-version format compatibility. The initial
artifact oracle assumed default version=1 was explicitly serialized; current
format omits it, and the corrected default-aware oracle passed on the same files.
No application repair was needed for that oracle error. Source/jar bindings and
raw logs are in ../evidence/vm-local-loop-experiment/.

Serial timing/memory work is complete:30 commands,51 unprofiled JMH states,
1 diagnostic profile,24 fresh-process memory probes, frozen source/jar verified.
The projection advantage repeats; general/default promotion is not established.
Browser/CI, full clean build, old-version compatibility and publication remain
unverified for this candidate.
No commit, version change, tag, workflow dispatch or publication is performed.
