# JVM-local loop experiment and release assessment

2026-10-17. Continuation authorized by the user, GPT-6.1 Sol High implementation,
independent tests/review and harness agents; parent-owned acceptance and serial
measurements. No commit, default enablement, tag or publication.

## Mechanism and scope

`JvmLocalLoopCompiler.prepare(bytecode)` and `VM.executeLocalLoops(plan, env)` add
a separate JVM-only backend. The previous `JvmLoopCompiler`/loopBody source stays
unchanged. The generated projection run method has 1472 JVM Code bytes (classfile
3751 bytes), 21 lowered values, 3 primitive arithmetic/comparison sites, 15
source-opcode spill continuations and 34 guard branches. Static counts are not
runtime counts or physical local-slot counts. Saved javap confirms JVM locals,
LADD/LMUL/LCMP and native branches, without body opcode dispatch.

Supported shape: one <=128-instruction comprehension, static paths, optional
filter, scalar/object mapping with literal String/Int/Long keys. Values live in
JVM reference locals, primitive long locals with Int/Long tags, or Boolean locals.
Exact boxed Int/Long operands/results within ±(2^53−1) take direct arithmetic;
Int results narrow only when both input tags and result range permit it. This is
a conservative fast-path eligibility limit, **not** a new JVM language numeric
limit. Full Long range, overflow/bignum, floating/decimal/string/custom Number
semantics continue at the original opcode in ordinary VM. No universal unboxing
or optimized DIV/mod/logical/dynamic-key/nested-loop/call support is claimed.

Per-PC compiler stack maps retain the complete live virtual stack. A failed guard
spills original values bottom-to-top above the untouched canonical base, sets the
current opcode PC and returns. Ordinary dispatch executes that opcode once and
the rest of this invocation; there is no completed-field/row/callback replay.
Each nested path hop is guarded before custom code; exact safe built-in maps and
lists are read in emitted order, including access-order map mutations. No input
pre-scan, field CSE or fixture/value-specific recognizer. Literal-key objects use
canonical field evaluation and insertion order. Append/NEXT retain canonical
builder/binding/index state; callbacks and tracing at NEXT see the original PC
and partial output. Initial tracing/custom environment/unsupported shape has
explicit whole-plan fallback. Empty loops do not claim entry.

A conservative stack-height guard selects ordinary execution without throwing an
early overflow. Generated method code is capped at 6000 bytes, with explicit
fallback above it. Plans own bytecode/metadata structure, retain constant/IR node
identity, and own one loader/class/kernel/classfile array without global cache.
VM context is reused and releases its plan reference finally; canonical VM last
state still retains last env/result/error state for snapshots. Use is sequential
and nonreentrant, including fallback and tracer close. Fatal JVM resource errors
such as allocation OOM inside generated boxing/object creation are outside the
verified exact-state claim. See [independent-review.md](independent-review.md).

## Correctness and artifacts

`final-gate.log`: 1156 reported tests, **1155 passed, one existing conformance skip,
zero failures/errors** across interpreter/VM/conformance/CLI JVM and Node. This
includes docsBuild, JMH jar and CLI JVM/Node package build; unchanged tasks may be
up-to-date. `test-counts.json` gives per-module counts. New JVM coverage is 17 tests
(6 plan, 11 observation). First focused run exposed an expected-value Kotlin type
inference error: a large Long literal promoted other expected map literals to
Long. Explicit Any? values plus a baseline assertion repaired the oracle; product
code did not change. Preserve `focused-gate1.log` for this history.

Parent `LocalLoopAcceptance.java`: **588 comparisons** with seeded independent
outputs/interpreter, non-fixture program shapes, exact ordinary-VM errors, env
key order/snapshots, failure/reuse, and 289 raw numeric cross-product cases with
Int/Long boundaries, floating NaN, BigInteger and BigDecimal. Raw equality checks
wrapper types rather than normalizing them away. `parent-acceptance1.log` passed.
Thirty-two fixed fixture configurations passed three executions each with actual
mode/output/input-immutability checks. Projection reports local entry and primitive
work with no fallback; paths/reduction/closure explicitly use canonical fallback.
Prior-jar baseline links through new harness (`old-jar-validation.log`).

Independent observation tests cover custom collection/numeric callbacks, primitive
live-value spill and no replay, dynamic tracing and branch Exit snapshots, NEXT
failure with advanced index, access-order maps, tree-bin lookup keys, raw wrapper
identity semantics, source-array ownership and reentrancy through fallback/close.
The tests do not establish arbitrary concurrent mutation or all program shapes.

Packaged JVM/Node launchers execute the Hello fixture, compile it and consume
same-runtime/cross-runtime current-format artifacts. The initial artifact oracle
wrongly required an explicitly serialized default version; the format omits its
default1. Only the oracle was corrected and rerun on the same files, successfully
(`artifact-oracles.log`). Stderr for all CLI commands was empty. Class major65
confirms Java21. Experimental compilers and ASM are physically packaged. Both CLI
launchers print v0.10.0 while metadata says v0.11.0-SNAPSHOT, a confirmed release
alignment blocker. Current-format roundtrips are not old-version compatibility.

## Measurement protocol

Four engines: interpreter, direct ordinary VM, previous generated loopBody, new
localLoop. Unchanged VMFactorialFixture inputs/sources: small10x5, large500x25;
projection reads first item, paths/reduction are controls. Identical fresh env
construction plus raw-output reusable execution is timed. Parsing/compilation/
JSON serialization are outside this boundary. The treatment combines local value
storage, guarded integer arithmetic, fewer PC writes and direct object building;
this four-engine comparison does not isolate each component's contribution.

Primary JMH: two forks, one thread,3x1s warmup,5x1s measurement, GC allocated B/op,
G1,Xms256m/Xmx768m. Both sizes plus reversed-engine large confirmation. Previous
jar precedes new harness for an ordinary-VM baseline check. Preparation and
closure fallback use separate2x500ms warmup/4x500ms measurement, two forks. All own
heavy processes are serial on a shared workstation; background/thermal/JIT noise
is not eliminated. `environment.json` records JDK/machine/source identity;
`commands.json` records every actual invocation and exit.

Memory: three fresh processes per engine/lifetime, one reused executor or1000
independent prepared executors, shared fixture,1000 calls. Five requested-GC
stages separate fixture, prepared-only ownership, executed state/results, executor
release and final output release. Record used/committed nonheap pools, process
class/unload counts, weak wrapper collection and external peak RSS. These are
approximate process-heap observations, not exact retained graphs. Executed VM
state can retain a result tree per executor; it is not plan memory. Classfile
bytes are neither heap-total nor Metaspace size. JFR samples are diagnostics and
its instrumented scores are excluded from performance comparisons.

Predeclared promising-result gate: >=15% lower large projection time than previous
loopBody in both orders, demonstrated interpreter advantage with uncertainty,
no execution allocation growth and no repeatable >5% control regression. Default
promotion additionally requires real workload eligibility, cold/JIT amortization
and lifetime policy outside this bounded experiment.

## Results

**The bounded local/typed experiment succeeds on projection. Keep default VM
unchanged; recommend only an explicit research/preview backend pending release
boundary and wider evidence.** All30 serialized commands exited0;250 Kotlin source
hashes,5 build-file hashes and JMH jar remained frozen. There are51 unprofiled JMH
states plus1 instrumented profile,72 validated primary fork setups,24 fresh memory
processes. Raw-derived [TABLES.md](TABLES.md), effects.csv and memory summaries
retain all cases, not just projection wins. Freeze evidence is in
freeze-verification.json.

Large execution time, microseconds/op; cells show forward / reverse means:

| Workload | Interpreter | Ordinary VM | Previous loopBody | New localLoop |
| --- | ---: | ---: | ---: | ---: |
| paths | 0.306 /0.284 | 0.421 /0.444 | 0.414 /0.451 | 0.448 /0.454 |
| reduction | 25.92 /27.32 | 40.19 /39.47 | 40.33 /37.58 | 39.91 /40.41 |
| projection | 118.35 /113.93 | 126.06 /124.51 | 103.53 /108.72 | 71.23 /71.59 |

Projection time falls31.2% /34.2% versus previous loopBody,43.5% /42.5% versus
ordinary VM,39.8% /37.2% versus interpreter. JMH99.9% intervals do not overlap for
these pairs in either order. New-upper /comparison-lower endpoint ratios are
0.740/0.704 versus previous loop,0.612/0.616 versus VM,0.656/0.725 versus interpreter.
These are conservative sensitivity checks, not formal confidence intervals for
ratios or independent-replication/product guarantees. The >=15% large projection
gate repeats. Small projection improves from interpreter1.044,VM1.352,previous
loop1.342 to new0.848us; its single-order intervals also separate, but no reverse
small-input repeat was planned.

Large projection allocations: interpreter193248,VM128280,previous loop128280,
new120472 B/op. The new change saves7808 B/op (6.1%) versus VM/previous loop.
The total37.7% advantage versus interpreter partly predates this change; do not
attribute it all to local lowering. Small projection saves64 B/op versus VM.

Controls are explicit ordinary fallback, not optimized reduction/paths. New large
paths means are6.2% /2.2% above direct VM; intervals overlap. Reduction is0.7%
below /2.4% above VM. No >5% regression repeats across large orders; a strict
<=5% upper bound is **not** certified. Small paths mean is6.3% higher, without a
second order; keep tiny-fallback overhead as a rollout concern. Closure fallback
new0.225us versus VM0.231us, both824 B/op. Prior-jar direct VM checks were
paths0.414,reduction40.43,projection126.32us, with unchanged allocation; there is
no established default-path degradation from adding this opt-in API.

### Preparation and memory tradeoffs

Source-to-executor preparation on projection, separate short protocol:

| Engine | Mean us/op | Allocated B/op |
| --- | ---: | ---: |
| Interpreter | 1.82 | 13520 |
| Ordinary VM | 3.97 | 20752 |
| Previous loopBody | 68.77 | 66442 |
| New localLoop | 95.93 | 120918 |

Preparation is much more expensive than ordinary VM. New/prior means suggest a
further increase, but their preparation timing intervals overlap. Per-plan class
creation and compiler/spill-map/ASM work are included. Warm preparation plus warm
execution algebra is not cold-first-call/JIT break-even evidence; no safe reuse
threshold for production is established here.

For1000 independently prepared but unexecuted executors, median incremental heap
is interpreter3.224MiB,VM6.095MiB,previous loop9.480MiB,new11.605MiB. New is about
90% above VM and22% above prior loop. Incremental Metaspace used is respectively
0.132/0.269/3.502/5.655MiB. New generated classfile arrays alone retain3.577MiB
across1000plans; retaining inspection bytes is a prototype choice, not an
unavoidable lower bound. New Metaspace committed delta10.375MiB and compressed
class-space used delta0.520MiB are separate raw pool observations.

After executing each plan once, heap deltas are3.701/117.159/120.532/122.475MiB:
VM canonical snapshots retain a result tree/state per executor. Those numbers
must not be called plan memory. Peak process RSS medians for the same many-plan
processes are192.9/265.0/308.9/337.0MiB. Reusing one executor gives prepared heap
roughly3.5/6.4/9.0/11.9KiB, executed heap0.115/0.120/0.122/0.129MiB, and peak RSS
163.8/162.5/166.0/161.6MiB. RSS is noisy and process-wide; no universal process
memory saving follows from fewer B/op.

Every weak wrapper was collected in every repeat (1 or1000). Generated-engine
process unload counters increased by1 or1000 after executor release in each
repeat, consistent with unloading but not a loader-specific proof. New final
many-plan heap residual was about54KiB and Metaspace residual0.70MiB; process
initialization/JIT metadata prevents interpreting residuals alone as a leak.

### Profile and decision

After-change JFR attributes31.1% of499 worker leaf samples to String.equals,
21.2% to HashMap.getNode and material remaining samples to map/output construction.
This supports the inference that field lookup/output work now dominates the
sampled kernel. Inlining/sampling caveats and prior-profile differences are in
[PROFILE.md](PROFILE.md); its instrumented timing is excluded above.

The projection speed/allocation gate passes within the fixed domain; no repeatable
large-control >5% regression was established. This is the first experiment in this
sequence with a clear interpreter advantage on this projection, including the
small case. It is still not a general Branchline/JSONata/Kotlin or JSON-pipeline
claim. More generated loops are not automatically faster, and plan-memory/cold
costs remain substantial.

For the next Alpha/preview release, prefer already accepted correctness, worker
isolation and ordinary-VM scaling/trace fixes. The new local backend can be a
labelled opt-in preview only after explicit experimental API/artifact separation
or acceptance of that dependency surface; it should not replace the default.
Older failed prototypes should not all become supported release APIs. Version/
Java requirements, compiled-format migration and the legacy numeric-observer gap
remain concrete release gates. See the [release proposal](../../planning/next-release-scope.md)
and [inventory](release-inventory.md). No publication was performed.
