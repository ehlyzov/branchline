# Independent prepared-block review

This review covers `PreparedBlockPlan`, the new prepared runner in `VM`, and
`PreparedBlockFrame`. No builds, JVM execution, or benchmarks were run by this
reviewer. Parent-owned JVM/Node and independent acceptance gates determine the
actual verification status.

## What preparation establishes

Both modes resolve operands once into the same prepared instruction format.
Control mode retains individual source instructions. Optimized mode assigns
named slots, proves a bounded class of local aliases, and emits explicit mirror
and direct-static-path operations. The runner dispatches those operations; it
does not rediscover the source patterns.

The effect analysis is an opcode classification, not SSA or general liveness
analysis. Preparation computes source control-flow entry points, basic blocks
and successors, and observation sites for known opcode categories. Mirror
eligibility uses a whole-program census: all writes to one name must be adjacent
`DUP; STORE_VAR; STORE_LOCAL` sequences for the same local, every write to that
local must be one of those pairs, no interior instruction may be a control-flow
entry, and loop binding names are excluded. Direct paths combine an eligible
load with consecutive static accesses inside one basic block. There is no
dominance analysis, general alias analysis, escape analysis, type inference,
numeric specialization, global value numbering, SSA, or dead-value elimination.
These proofs do not establish that input values are ordinary containers; runtime
guards still check the actual container before each direct access.

## Ownership and compatibility

Plans own copies of source opcode/operand/offset arrays, constant-list structure,
debug maps, and trace-metadata collection structure. Mutating those source arrays
after preparation does not change execution. Constant values and IR trace-node
objects retain identity; their concurrent mutation is unsupported. The plan's
internal arrays are read-only by implementation contract. Public bytecode and
serialization formats remain unchanged.

Unsupported or malformed programs have an explicit whole-plan fallback. Guards
deoptimize at the exact current source PC, after materializing aliases/named
bindings, without replaying a load or completed access. Both modes use the new
runner when supported, with optimization disabled or enabled respectively.

## Runtime review

- A direct path reads the original load before checking its stack-capacity
  failure, matching baseline evaluation order. It records each access's source
  PC before reading, and errors leave the container popped just as canonical
  dispatch does. Before a custom access it reconstructs the pending container
  on the stack and resumes canonical dispatch at that access.
- Host boundaries materialize canonical locals and named bindings before the
  callback. Local aliases detach permanently within the invocation so a host
  mutation changes live named values while existing locals stay stale. Host
  completion invalidates slot caches. Later mirrors update detached locals.
- Logical local capacity follows baseline geometric growth even while alias
  values are deferred. Final/error cleanup materializes locals, preserving
  snapshot shape. Existing canonical VM fields still retain the last execution
  state; clearing the reusable frame is not a claim that the VM retains no last
  input/output references.
- The reusable frame clears environment/value references and state in a nested
  `finally`; linked insertion-order bookkeeping stores only integer indices.
  One VM caches one frame by plan identity and supports sequential use.
- Review found that initial whole-plan fallback bypassed the prepared execution
  reentrancy guard. The runtime owner corrected it with a guarded one-use entry
  into canonical execution. Nested public execution attempts are checked before
  they can mutate state or effective-mode status. Thread sharing remains
  unsupported.
- Parent review identified a missing `NEXT_FOREACH` observation classification:
  a custom list can first enable tracing from its second-element read, after a
  quiet initialization. The planner now marks both iteration instructions as
  observable, preserving the current NEXT's exit metadata before canonical
  continuation. A new exact baseline regression compares operation order,
  trace events and instruction counts. `ARRAY_BUILDER_FINISH` only type-tests,
  pops and pushes the list reference; it does not invoke custom list methods.
- A follow-up logical-conversion review found that JVM bignums are aliases for
  non-final Java BigInteger/BigDecimal classes. A subclass can override signum
  or numeric/hash operations and execute callbacks. Logical conversions and
  conditional truth branches are therefore observation sites as well. The
  runtime owner was asked to guard bignums by exact class before optimized
  scalar/truth operations; static IBig keys containing a subclass now trigger
  whole-plan fallback during preparation. The general custom-object truth case
  still simply returns true, but that does not justify omitting the bignum case.
  The override possibility is supported by the public class/method declarations
  in the [Oracle Java 21 BigInteger API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigInteger.html).
- Parent's first gate exposed a JS difference in malformed operand arrays:
  unchecked out-of-range reads can produce undefined instead of a JVM bounds
  exception. Preparation now explicitly validates operand offsets/ranges and
  constant indices before resolution, preserving whole-plan fallback in both
  runtimes. The regression includes missing, oversized, and truncated operands.

The earlier separate `executeExperimental` prototype still contains broad
`is BLBigInt/BLBigDec` scalar guards and no corresponding logical-subclass guard.
The same subclass observation risk therefore remains there by source review;
it was not executed or repaired in this prepared-block slice. This is a residual
limitation of that opt-in prototype, not a claim about the new runner's tests.

## New independent test contribution

`PreparedBlockObservationTest` adds baseline comparisons for direct-path errors
at successive source instructions (including missing load and array bounds),
exact snapshot/stack state, custom nested access that enables tracing and mutates
a named binding, and execution after caller mutation of source bytecode arrays.
`PreparedBlockPlanTest` checks operand resolution, blocks/successors, control-flow
boundaries, alias safety, ownership, unsupported/malformed programs, and inert
constant values. Runtime-owner tests and parent independent probes provide the
remaining acceptance evidence; this document does not claim their results.

`PreparedBlockBigNumberObservationTest` adds JVM-only subclasses overriding
BigInteger/BigDecimal signum across five truth operations, an overridden numeric
addition, and an IBig key subclass. It checks callback visibility and exact trace
parity against baseline, plus static-key fallback before callbacks. The parent
owns executing these new regressions.

## Parent execution result

Final `gate5.log` passed: 846 passing tests, one existing skip, zero failures
across VM/conformance/CLI JVM and Node. VM counts are 192 JVM and 118 Node,
including the new observation and bignum subclass regressions. Parent-held
`PreparedBlockAcceptance.java` passed 538 comparisons and the fixture probe
passed 32 configurations with three executions each. See `test-counts-final.json`,
`parent-acceptance-final.log`, and `fixture-validation-final.log`.

These checks passed both before and after the bounded hot-loop/cleanup extraction
that reduced the hot JVM method from 8,935 to 3,120 bytes. The extraction retains
the same operations, guards, fallback, Abort handling and finally ordering;
the new loop is a private non-inline method. The initial partial timing series
is archived separately and excluded from final performance acceptance. Sources
and jar were refrozen before restarting the entire measurement matrix with
ordinary JVM flags. See `README.md` for results and `initial-candidate/README.md`
for the diagnostic and correction.
