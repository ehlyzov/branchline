# Independent JVM-local loop review

The reviewer inspected `JvmLocalLoopCompiler`, `LocalLoopSupport`, the new VM
entry/context, and the canonical numeric/iteration helpers. The reviewer added
17 independent JVM tests and ran no builds, JVM processes, profiles, or
benchmarks. Execution results below are parent-owned evidence. No speed or
memory advantage is asserted here; the previous `loopBody` backend remains a
separate control.

## Lowering and supported proof

The compiler accepts one bounded compiler-shaped comprehension: one INIT/NEXT
pair, the expected backedge/exit, one builder/append, a builder finish, and at
most one filter jump to NEXT. The body is limited to 128 source instructions.
The supported subset includes static paths, literal-key object construction,
scalar ADD/SUB/MUL/NEG, integer comparisons, and filtered/unfiltered projection.
Unsupported operations, calls, closures, nested loops, control flow, operands,
or lowering shapes get an explicit plan fallback before execution.

Preparation tracks a virtual operand stack and assigns fresh JVM local locations
for reference, integer, and Boolean results. Integers use a JVM long plus a tag
that preserves whether canonical boxing should produce Int. This is real local
lowering with JVM arithmetic/branches, not a canonical-stack operation bridge
for each body instruction. It is not a general SSA, liveness, alias, escape, or
register-allocation optimizer. Prefix/suffix execution, row append, and NEXT
still use canonical VM operations. There is no path common-subexpression cache.

The parent-inspected primary artifact has a 1,472-byte `run` method and a
3,751-byte class. Its plan reports 21 value records, three primitive sites,
15 spill sites, and 34 guards; these are plan metadata, not timing or allocation
measurements. See [kernel-method-sizes.json](kernel-method-sizes.json),
[generated-bytecode.txt](generated-bytecode.txt), and
[fixture-validation1.log](fixture-validation1.log).

## Primitive and state-reconstruction review

Integer conversion accepts Integer/Long rather than coercing arbitrary Number
objects. Primitive execution conservatively caps operands/results at
±(2^53−1); this is an optimization guard, not a JVM language-number limit.
Other values and magnitudes resume canonical semantics. Multiplication checks
overflow before accepting its result. ADD/SUB and NEG check the result before
consuming the virtual operands. An Int result promotes to Long when required;
an existing Long does not narrow to Int merely because its value fits.

Each source PC has a spill label capturing the original virtual values before
that instruction's pop effects. Arithmetic guards run while those values remain
available. On rejection, the context establishes the current source PC and
pushes the live values in canonical order, then ordinary execution performs the
current opcode once. Completed fields, prior rows, and prior lookups are not
replayed. Boxing uses each value's original reference or integer tag. Tests
exercise a custom nested container only after an earlier field and arithmetic
result are live, and an unsafe-magnitude operation after a completed row.

This preserves the compiler's reverse field-evaluation order and source object
insertion order. It also preserves error operand pops, missing/null handling,
String ADD, nonnumeric comparison fallback, NaN ordering, generic numeric
conversion, and bignum subclass callbacks. Unsupported values are not permission
to pre-scan rows or restart a projection. Unhandled errors retain the current
loop binding and partial builder; normal completion restores an outer binding.

The capacity precheck uses the canonical base-stack size plus maximum virtual
height, with long arithmetic. It conservatively resumes canonical execution
before entering the generated body when spilling might exceed the stack limit;
it does not manufacture a StackOverflow at the precheck. Negative/oversized
list indices likewise resume the original ACCESS so original I32/I64 bounds
error details and pop order remain intact. Numeric map keys retain map lookup
semantics, including negative keys. Opaque constants retain identity and are
not hashed, compared, or formatted during lowering.

## Containers, callbacks, and tracing

Every static hop checks the actual nested container. Admitted exact standard
map/list classes avoid user callbacks for guarded static reads; custom
containers resume before their current access. Static query keys are final
String/Integer/Long values.
The reviewed [OpenJDK 21 HashMap lookup implementation](https://raw.githubusercontent.com/openjdk/jdk21u/master/src/java.base/share/classes/java/util/HashMap.java)
uses the query as equality/comparison receiver, so an exact String query does
not invoke an existing custom Comparable key even in a tree bin. No full map
scan is needed. The collision-tree regression arms existing keys to fail on
such callbacks.

Exact LinkedHashMap can nevertheless have access-order behavior. Every emitted
get must still occur, including repeated reads of the same field; the dedicated
order test compares input key order with the canonical VM. This is why an
ordinary root does not justify caching paths or eliminating accesses.

Guard deoptimization reconstructs state before invoking a custom Map/List or
Number operation. The canonical operation then establishes its normal pop and
PC state before the callback. Independent snapshots from Map.get and numeric
methods compare that state with the ordinary VM, including prior field values.
Generic Number.toDouble is allowed by the baseline; narrowing it through
toInt/toLong would be wrong and is rejected by the test object.

NEXT delegates to the original helper. A size callback runs before advancement;
the index advances before reading the next element; the binding changes only
after that read succeeds. On tracer activation, the context clears activePc and
emits Exit at the canonical target-minus-one PC before incrementing. Tests cover
a thrown next-element read and an Exit callback after predicate deoptimization.
Initial tracing or tracing enabled by the canonical prefix bypasses the local
body explicitly. No callback or completed row is replayed.

## Lifecycle and ownership

The entry checks same-VM reentrancy before changing status. Initial fallback
uses a one-use canonical-entry permit while keeping the execution guard active.
The shared cleanup keeps tracer.close inside that guarded lifetime, even when
close throws. Independent tests attempt nested local execution, ordinary
execution, begin, and step from close in both initial fallback and spilled paths.

Plans own source arrays, constant-list structure, debug/event maps, and nested
path-write metadata collections. Constant values and IR nodes retain identity;
they are not deeply immutable. A context releases its plan reference in cleanup,
and JVM frame locals cease to be roots when the generated method returns. The
VM still retains its last canonical bytecode/environment/stack/output state.
Each eligible plan owns a child-loader class and its generated bytes; there is
no global generated-class cache. Concurrent VM use, concurrent constant/IR-node
mutation, unloading timing, and resource-failure equivalence are not established
by this review. Class preparation, retained heap, metaspace, and RSS require
separate parent measurements.

## Tests, corrected oracle, and execution evidence

`LocalLoopPlanTest` contributes six tests for supported scalar/object/filter
shapes with actual local/primitive execution, empty loops, explicit unsupported
fallback without duplicate calls, malformed/oversized plans, source-array
ownership, and path-write metadata ownership during traced fallback.

`LocalLoopObservationTest` contributes eleven tests covering raw Int/Long/
BigInteger wrappers at arithmetic boundaries; spilling after prior fields and
arithmetic; error snapshots and reuse; unsafe magnitude after a completed row;
generic Number and bignum subclass callbacks; access-order maps; collision-tree
string lookup; NEXT failure state; NaN/null comparison; predicate Exit-callback
snapshots; and close-time reentrancy. Numerical assertions compare raw classes,
not only JSON-equivalent output.

The first focused gate's sole failure was an expected-value construction issue:
Kotlin inferred Long for every numeric value in a map containing a Long result.
That changed expected `late`/`first` wrappers even though their input values were
Int. The test now explicitly uses `linkedMapOf<String, Any?>` and checks the same
expected output against the ordinary VM before the local backend. The corrected
oracle passed the broad gate; no product source change was made for this failure.
The original evidence remains in [focused-gate1.log](focused-gate1.log).

The parent ran [final-gate.log](final-gate.log). Actual XML counts in
[test-counts.json](test-counts.json) total 1,156 reported, 1,155 passed, one existing
skip, and zero failures/errors: VM JVM/Node 225/118, interpreter JVM/Node 214/62,
conformance JVM/Node 359/109 including the skip, and CLI JVM/Node 56/13. All 17
new independent JVM tests are included. The gate also built CLI/package/docs
artifacts; this review makes no broader release-readiness claim.

The separate parent probe reports 588 passing checks in
[parent-acceptance1.log](parent-acceptance1.log). Its
[source](LocalLoopAcceptance.java) includes 289 raw numeric combinations with
class-sensitive output, error, environment, and snapshot checks. The parent
validated 32 fixture/engine/size combinations three times each in
[fixture-validation1.log](fixture-validation1.log). Primary projection reports
actual local entry and primitive use in both sizes without fallback; the closure
control reports no generated entry and explicit fallback. Product source and jar
bindings are in [final-source-manifest.json](final-source-manifest.json) and
[environment.json](environment.json). The test-oracle correction followed the
first jar build without changing those product sources.

These results support the bounded correctness and execution-mode claims above.
They do not establish arbitrary CFG support, all callback behaviors, JavaScript
local lowering, class-unloading timing, or a speed/memory advantage. Benchmark,
memory, and next-release conclusions remain separate parent deliverables.
