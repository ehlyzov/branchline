---
status: Implemented
depends_on: ['planning/vm-performance-goal']
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-10-17
---
# Second VM speed and memory audit

User explicitly requested another goal cycle: investigate P0/P1 speed/memory, record a repair plan, fix confirmed problems, and stop after a final audit finds no new confirmed P0/P1. The interrupted request had not started; this cycle was activated after the status check. Prior accepted VM sources and jar match the earlier after-manifest exactly. Existing local changes are preserved.

## Bounded new coverage

Investigate nested environment lookup/enumeration, compiler and bytecode preparation, retained memory, and supported snapshot costs beyond the previous accepted scenarios. P0 is pervasive catastrophic growth; P1 is a reproduced material cost with a safe remedy on supported use. Required output/copy size and speculative constant-factor tuning are not findings by themselves. No universal optimality claim.

Use the native complexity-optimizer workflow. GPT-6.1 Sol/high agents investigate and implement; parent controls acceptance. No duplicate broad verification without a material change. No commit or push.

## Investigation and repair sequence

1. Freeze current source/jar and probe new coverage without product edits.
2. Record each confirmed finding, priority, complexity, reproducer, minimal repair, semantic risks and readiness criteria here before implementation.
3. Delegate disjoint product changes; coordinate one Gradle slot. Preserve all prior changes.
4. Independently rerun reproductions, relevant JVM/Node tests and adjacent audit. Close only measured findings with semantic gates passing.
5. Update documentation/contour/evidence and stop when the scoped final review has no new confirmed open P0/P1.

## Findings and implementation plan

Pending executable investigation. Initial lead: OverlayEnv.entries recursively requests parent.size and parent.entries, potentially multiplying enumeration at nesting depth. This is not yet an accepted finding.

## Confirmed P1-A: exponential nested environment enumeration

Evidence: `/tmp/branchline-vm-audit2/OverlayProbe.java` and `overlay-results.csv`, frozen accepted jar. A supported nested CALL_LAMBDA/SUSPEND program with one root binding reaches 531,441 (=3^12) root entry enumerations at depth12 and allocates122,111,688bytes for a30,960-character snapshot; restored execution returns7. Depth8/10 allocate1,819,328/14,036,488bytes. Plain LOAD_SCOPE does not enumerate.

Root cause: OverlayEnv.entries obtains parent.size, then putAll(parent) requests size and entries again. An overlay parent materializes each recursive request. Current enumeration is exponential in overlay depth; full snapshot repeats that for active frames.

Repair plan before product edits: traverse overlay parents iteratively, collect local layers, materialize the base map once, then merge locals from outer to inner. Preserve parent-first insertion order, local shadowing including present null, detached entry-view behavior, live parent/capture references and local-only removal. Do not change snapshot schema. Enumeration should be O(total bindings across the chain); snapshot still pays its explicit per-frame output cost.

Readiness: semantic common tests for nested shadow/null/order/live bindings plus deterministic root-enumeration bound; snapshot/restore reproducer yields same result and removes exponential allocation/count growth; JVM/Node gates and independent adjacent review. Owner vm_overlay_audit2: VM.kt and a new focused common test only. Parent owns evidence/docs. Priority P1 because supported suspension becomes impractical at shallow nesting.

## Confirmed P1-B: quadratic compiler ownership bookkeeping

Evidence: `CompileScopeCostAudit.java`, frozen accepted VM baseline. Final representative fixture: N fresh literal-array declarations followed by N independent shallow IF blocks, each appending to its corresponding array, then output of all arrays. Compilation allocates 2.017/25.407/96.972 MB at N=128/512/1024; N=1024 emits 14,338 instructions. Median diagnostic times≈1.70/8.33/23.99 ms. Every resulting array is checked as ["ok"]. Input depth is only1; this is not a pathological deep AST.

Root cause: compileNodes copies the complete privateCollections map on every child block and clears/reinserts it on exit. With N live candidates and N blocks, bookkeeping becomes O(N²) even when the branch changes no candidate.

Repair plan before product edits: replace full-map scope snapshots/restoration with scope checkpoints and reversible logs of changed eligibility bindings; centralize candidate insert/remove/clear so all scope changes restore exactly. Skip invalidation traversal when no candidate remains if proven safe. Preserve whole-branch/loop invalidation, shadowing, aliases and conservative arbitrary-call handling. No new language syntax or runtime semantics.

Readiness: existing13private-collection tests plus focused common scope restoration/branch/loop/alias cases; same compile probe removes quadratic allocation growth and yields equivalent bytecode/output; JVM/Node gates and independent review. Owner vm_memory_audit2: Compiler.kt and a new common test only. Parent owns plan, docs and final verification. Priority P1: routine generated wide transforms incur tens of MB of avoidable compile allocation.

## Final acceptance

Both P1-A/P1-B are fixed. Final paired rebuilt-jar probes show depth12 snapshot allocation122,111,688→951,848bytes with unchanged30,960-character snapshot/result7; compiler1024-accumulator allocation96,971,640→4,648,661bytes with unchanged14,338instructions and checked outputs. Empty-candidate depth128 list reads536,639→4,223.

Final combined VM/interpreter/conformance/CLI JVM/Node tasks plus benchmark jar pass:994recorded tests,993passed, one existing skip; VM143JVM/72Node. Independent500-case compiler-bytecode comparison is identical. Independent source/probe review reports no new confirmed open P0/P1 within new overlay/snapshot and compiler/memory scope. Evidence, manifests and commands: `development/evidence/vm-performance-audit2/README.md`.

Snapshot payload cost, existing unsupported executable-closure serialization, and unconfirmed deep-live-candidate observation remain explicit limits; they are not silently counted as fixed. Stop after documentation/contour packaging gates; no speculative extension of scope.

Packaging gates passed: `./gradlew docsBuild`, contour refresh/audit (zero failures/warnings), and `git diff --check`. Final measured product hashes remain unchanged. The bounded goal is complete.
