---
status: Proposed
depends_on: ['planning/vm-hotpath-optimization']
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-10-17
---
# Validation of selective input, insertion, addressing and multicore ideas

## Scope and verdict

Read-only design assessment against the current working tree. This is a separate
proposal, not a change to the in-progress trace-only VM optimization. No parser,
VM, compiler, bytecode, benchmark or CLI implementation was changed here, and no
new performance or concurrency test was run. Thresholds below are proposed
experiment decision rules, not measured results or guarantees.

| Idea | Practical verdict | Existing boundary |
|---|---|---|
| Keep structurally only JSON fragments actually used | Promising for large sparse inputs. Requires a new input representation or selective parser; cannot be obtained by changing VM allocation alone. | JSON entrypoints currently materialize the entire tree before execution. Raw backing storage can still dominate retained memory. |
| Insert a key/value in O(1) | Useful goal for an owned mutable builder, already partly implemented. Hash insertion is an expected/amortized model under suitable keys/capacity, not an unconditional bound for every update. | Private VM SET mutates; observable/shared values use whole-container copies. Key hashing, resize, path copying and output serialization remain costs. |
| Instructions already contain absolute addresses | True for resolved control-flow instruction indices; partly true for local slots. It does not remove object-key lookup. | Compiler resolves labels and assigns local slots; VM also uses string-named environment variables and Map-based property access. |
| Use multiple cores | Appropriate first for independent records or independent transforms with separate runtime instances. It primarily targets batch throughput. | JVM CLI already has a thread-pool fan-out surface, but the compiled VM path appears to share one mutable VMExec; isolation needs validation before relying on it. JS fan-out is serial. |

Recommendation: first measure sparse input end to end, and separately establish
worker isolation for VM batch execution. Do not combine all four ideas into one
replacement VM. The current dispatch bottleneck and the proposed input-memory
bottleneck occupy different measured boundaries.

## Source evidence and the limits of existing results

- `interpreter/.../json/JsonInput.kt`: `parseJsonValue` invokes a recursive parser;
  `readObject` creates `LinkedHashMap`, `readArray` creates `ArrayList`, and strings
  and numbers are decoded during that pass. `parseJsonObjectInput` additionally
  copies root entries into another map. Numeric-key conversion can walk and
  rebuild nested containers. This is eager materialization, not deferred parsing.
- `cli/.../JsonInterop.kt` delegates JSON text input to that parser.
  `BranchlineRuntime.buildEnv` installs the input alias and copies root entries
  into the environment. A future lazy Map can therefore be forced by ordinary
  `size`, `putAll`, enumeration, coercion or contract validation, even before a
  transform explicitly accesses a property.
- `vm/.../VM.kt`: `ACCESS_STATIC` and `ACCESS_DYNAMIC` perform `Map` lookup or list
  indexing. `setStatic`/`setDynamic` use in-place mutation only for compiler-marked
  private roots when ordinary execution and lack of an active tracer permit it;
  otherwise they copy the map or list. `begin` disables private mutation.
- `vm/.../Compiler.kt`: `compileLet` and `compileSetVar` mirror locals into the
  environment using `DUP`, `STORE_VAR`, `STORE_LOCAL`; identifier reads can use
  `LOAD_LOCAL`. `resolveJumps` replaces labels with numeric instruction targets.
  `Env.kt` is the interpreter's linked lexical scope, while the VM uses its own
  mutable environment, locals and call-frame machinery. They are distinct costs.
- `vm/.../Integration.kt`: `VMExec` retains one VM and execution reporting state;
  VM execution mutates stack, pc, environment, locals, outputs, call/loop/try stacks,
  prepared numeric sites and caches. Sequential reuse does not imply concurrent
  reuse. Service-map documentation explicitly makes no thread-safety claim.
- `cli/.../BranchlineCli.kt`: the compiled-artifact path creates `vmExec` once and
  captures it in `runTransform`; `runWithFanout` calls that closure for entries.
  `cli/.../JvmPlatform.kt` runs parallelMap blocks in a fixed thread pool and
  retrieves futures in input order. `JsPlatform.kt` uses `items.map(block)`.
  This source path is a concrete concurrency risk, not a reproduced failing test.
- Ambient `Debug.tracer`, shared-store providers, host callbacks, metrics and
  contracts need an isolation audit in addition to creating a VM per worker.

Full source roots for the abbreviated paths above are
`interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/`,
`vm/src/commonMain/kotlin/io/github/ehlyzov/branchline/`, and
`cli/src/{commonMain,jvmMain,jsMain}/kotlin/io/github/ehlyzov/branchline/cli/`.

The existing evidence in
`development/evidence/vm-interpreter-comparison/README.md` measures prepared
execution over already-built Map/List input. Parsing, input I/O, compilation and
serialization are excluded; input remains resident between invocations. B/op is
allocation per operation, not retained heap, minimum heap or peak RSS. Those JMH
pairs cannot validate selective parsing end to end.

That evidence already shows a useful distinction: repeated dynamic object SET at
1,000 and 4,000 rows scales roughly linearly for the private VM case, while the
copy-heavy interpreter case grows approximately quadratically in those samples.
The same report has lower VM allocation but slower VM time for array accumulation.
Therefore allocation savings do not establish faster execution. The trace analysis
in `development/evidence/vm-hotpath-optimization/trace-analysis.md` also describes
about eight dispatched instructions per order in the native reduction; selecting
less input does not remove that cost when every order is needed.

## 1. Selective materialization is different from retaining raw JSON

Three mechanisms should be measured separately:

1. A projection parser scans and validates all input but constructs only a proven
   required subset. With a sound access plan and small output it can release the
   original text after parsing. It cannot skip validation merely because a field
   is unused without changing the current acceptance boundary.
2. A lazy value references raw text/byte ranges and decodes on access. It avoids
   unused decoded strings/containers but may retain the entire input buffer as
   long as one leaf, output, closure or cache references it. Repeated access needs
   caching or an explicit decision to reparse; both choices affect memory/time.
3. Streaming records can release completed independent records. That bounds live
   working data only if the output sink, queues and aggregation state are bounded.
   Collecting all results into a list still retains an output proportional to the
   batch. Arbitrary random access, sorting or later reuse may require materializing
   or indexing the batch and defeat that benefit.

Static property access is a candidate for projection planning, not proof of a
complete access set. Dynamic keys, enumeration, spreading/merging, full-object
OUTPUT, equality, host calls receiving objects, aliases and contract validation
can require more data. Keep an eager fallback for unsupported analysis; report its
rate instead of counting it as successful selective execution.

Preserve duplicate-key rejection even inside skipped objects, malformed/trailing
content rejection, Unicode and escape validation, safe/strict number behavior,
numeric key conversion, missing versus explicit null, iteration order, access
errors, unchanged caller input and fully usable detached outputs. In particular,
the present parser rejects errors before execution. Delaying such errors until a
field is accessed could change whether host effects run, or whether TRY/CATCH can
observe the error. Treat that as a semantic change unless full validation remains.
Validation can still require scanning all bytes; selective materialization does
not promise sublinear input reading.

## 2. Constant-cost insertion requires ownership and an operation definition

Define the measured operation as one insertion into an exclusively owned mutable
object. Replacing an existing key differs from adding a new one; long string keys,
collisions, capacity growth, key conversion and nested path updates differ too.
Even if table insertion is cheap, copying a k-entry visible map costs work
proportional to k in the current implementation; n successive growing-map copies
can accumulate quadratic work. Conversely, a builder with n entries still needs
proportional total storage and eventual serialization work.

Do not remove copying globally. Private ownership must stop before aliasing,
capture, host exposure, output, stepping/snapshot observation, tracing or custom
environment callbacks can observe a previous value. The current compiler/runtime
already has that boundary. A persistent structurally shared map would be another
representation with its own lookup/update/order costs; the present copy path is
not evidence that every immutable representation must copy the whole object.
First validate and extend a missed private-builder case only if one is found.

## 3. Addressing has three meanings

Control-flow targets are positions in the instruction sequence, already resolved
at compilation. Local variable indices address entries in a runtime local array;
the array and its contents belong to each call/execution. Neither is an absolute
address of a JSON property. Static field keys are known constants, but the same
program executes on different object instances with different fields/layouts.

The compatible extension to investigate is resolving more bindings to frame slots
or guarded field-layout indices, with a general Map fallback. It must account for
scope shadowing, closure capture, missing values, custom maps, functions compiled
with `useLocals=false`, host observation, environment export, trace/snapshot state
and the existing local/environment mirror. Indexing JSON by shape requires a new
representation or guards against changed shape; heterogeneous records and dynamic
keys may erase the benefit. Raw process pointers are neither operands supplied by
the current common Kotlin Map API nor portable durable bytecode/snapshot addresses.
Do not serialize runtime references as if they were stable input locations.

## 4. Parallelism belongs outside dependent instruction execution first

Independent records can use separate VM/VMExec instances and fresh environments,
with immutable shared program/input data only after ownership is established.
The worker must own mutable numeric-site preparation and caches too. Share neither
a running VM nor its stack/pc, mutable outputs or tracker. Verify the existing
compiled CLI fan-out risk before treating `--jobs` as a safe VM scaling route.

Not every SET prevents parallelism: worker-local result construction is suitable.
SET of a shared result, order-dependent accumulation, cross-record state, shared
resources and effectful host functions require a dependency/effect contract.
Reductions need a defined combination order and numeric semantics; reassociation
can change results or overflow/error timing. Preserve ordered OUTPUT and the
specified failure selection; future retrieval in input order alone does not make
concurrent host side effects occur in that order. Cancellation and partial effects
also need a decision before exposing concurrent execution as equivalent.

A batch can improve throughput while adding queueing and per-record latency.
One small input does not gain a general multicore speedup. Measure scheduling,
worker preparation, synchronization, allocation/GC and ordered merge overhead;
do not attribute all occupied cores to useful VM work. JVM and JS need independent
implementations and evidence. JS's current map is deliberately serial in source.

## Proposed experiments, in priority order

Each slice has an independent baseline and decision; do not benchmark concurrent
with the active parent VM acceptance run. Record source/jar hashes, runtime,
hardware/core limits, heap limits, dataset and exact commands. Run repeated fresh
processes with baseline/candidate ordering alternated after warmup. A claimed win
must exceed uncertainty and reproduce; a point estimate alone is insufficient.
These are future harness requirements, not commands run by this assessment.

| Priority | Bounded experiment and workloads | Proposed success / rejection criteria |
|---|---|---|
| P0 | Establish an eager end-to-end baseline from JSON text through parse, env/contracts, execute, output encoding. Separate parse-only, prepared execute-only and full pipeline timings. Reuse native programs as controls. | Correct independent outputs and all input rejection semantics; no engine fallback hidden as VM. Publish bytes, visited fields and output sizes so timings compare equal work. This establishes a fact before a representation change. |
| P1 | Projection/materialization probe over 1, 16 and 64 MiB JSON with constant tiny output; select 1%, 10%, 100% of fields and vary whether unused bulk is early/late, nested containers or large strings. Include repeated reads, dynamic-key access, full-object output and contracts OFF/ON. Compare eager, validated selective projection and, only separately, raw-backed lazy access. | Target >=25% lower full-pipeline allocation and >=25% lower peak live heap on the sparse 16/64 MiB cases, with >=10% faster end-to-end time to claim a speed win. <=5% dense-path slowdown; otherwise retain only an explicit sparse mode if memory gains justify it. Reject a retained-memory claim if raw backing remains reachable after a tiny result escapes. |
| P2 | Validate private dynamic-key builder versus an alias-observable copying control at n=1k, 4k, 16k unique short keys; repeat with replacements, long keys and nested SET. Use existing supplemental SET results as baseline evidence rather than rerunning them without a delta. | For the private append-only builder, 4x n should remain within 5x time/B-op after fixed overhead; investigate larger growth. No alias/input/snapshot/trace regression. An optimization needs >=10% reproducible time or >=20% allocation benefit on a newly identified missed case; existing behavior alone needs no rewrite. |
| P3 | Worker-isolation correctness probe first, then independent pure transforms with workers 1/2/4, batch sizes 1/32/1024/16384 and cheap/path, dense projection and compute-heavy per-record work. Bound in-flight work and consume outputs. Include ordered error/effect probes separately from the pure speed corpus. | Worker-local state must pass identical outputs, input preservation and deterministic specified failures before timing. Target >=1.5x throughput at 2 workers and >=2.5x at 4 for substantial batches, while reporting CPU seconds per record and p95 latency. Reject default fan-out for a workload if CPU/record grows >25%, serial latency regresses >5% or live heap grows without the declared queue/worker bound. Scale thresholds are local proposal gates, not expected hardware guarantees. |
| P4 | Only after a profile attributes >=15% of execution CPU to environment/property lookup, test one additional slot-binding or guarded-layout case. Compare homogeneous/heterogeneous objects, dynamic keys, custom environments, functions, tracing and snapshot restore. | Require >=10% repeatable end-to-end execution-time benefit on the attributed case and <=5% regression on heterogeneous/dynamic controls; bound any shape/preparation caches. Reject if compilation/preparation cost or validation consumes the claimed benefit at representative batch sizes. |

Report allocation (bytes/record and bytes/transform), throughput and latency
separately. For memory also report peak live heap during the pipeline, retained
heap after releasing raw input and completing/retaining a small output, and peak
process RSS in separate process runs. State the capture method and its overhead;
RSS includes runtime/native/committed memory, so it is not decoded-tree size.
Inspect reachability when testing raw-buffer retention. Keep output retention and
input-generation lifetimes identical. Measure process CPU time per record and
wall time; their ratio helps distinguish parallel utilization from useful speed.
Do not use forced collection inside a timed operation or infer heap/RSS from B/op.

## Next concrete slice and completion record

The next proposed implementation task is P0 plus a small P1 static-projection
prototype behind an experiment boundary: validated scanning, tiny detached output,
explicit eager fallback and the sparse/dense controls above. It needs no bytecode
address or VM dispatch redesign. Independently, the source finding about one shared
VMExec in compiled CLI fan-out is a scoped correctness task for the coding owner;
it precedes multicore timing, not a reason to start broad orchestration work here.

This assessment ran only `git status`, source/evidence reads and a documentation
diff check. No Gradle, JMH, profile or stress command was started. Existing dirty
files were read without edits. The parent registered this new plan in
`development/INDEX.md` before creation. No contour trigger fired: topology, APIs,
entrypoints, verified commands and accepted runtime invariants are unchanged.
The proposed parser and concurrency behavior remains unverified.
