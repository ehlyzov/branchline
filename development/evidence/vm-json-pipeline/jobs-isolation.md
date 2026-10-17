# Compiled CLI fan-out isolation implementation evidence

Date: 2026-10-17. Parent owns independent acceptance and full JVM/Node gates.

## Reproduction

Command against unchanged CLI implementation:

```sh
./gradlew :cli:jvmTest --tests '*CompiledFanoutIsolationTest'
```

`jobs-before.log` and `jobs-before.xml` record failure. A thread-safe ambient tracer
coordinates two real compiled CLI workers at their first input-variable read:
first reader waits until the second reaches the same instruction; the second
waits until the first produces output. The shared VM resets its environment and
stack during that overlap. Serial reports contain a.json=11 and b.json=22;
parallel reports incorrectly contain b.json=22 for a.json too. The failing test
completed in 0.245 seconds. Latch deadlines are deadlock guards, not sleeps used
to induce the bug.

The final regression additionally checks independently constructed expected
JSON and observes two distinct worker threads. Cleanup does not affect the
observation assertion.

## Fix

- Compiled untraced jobs initialize at most min(jobs, entries) complete
  BranchlineProgram/VMExec contexts serially, then reuse each context exclusively
  on one executor thread. The deserialized bytecode is shared for reads; VM
  preparation caches, stack, environment, execution report, contract synthesizer
  and type resolver belong to the worker. Serial execution reuses its original
  runtime without allocating extra worker contexts.
- The worker mapper returns reports in input order. It joins all tasks even on
  failure before returning the first failure in input order, retaining the JVM
  ExecutionException wrapper. This keeps SharedStoreProvider.store alive until
  all tasks finish. On collector interruption, joins continue, InterruptedException
  is reported in the affected result slot, and the interrupt flag is restored
  after joining. Transforms are not cancelled by collector interruption.
- CLI-owned collecting traces and ambient CollectingTracer use serial compiled
  fan-out, retaining capture-stack/event order. CLI help states this behavior.
  Other ambient custom tracers must themselves support concurrent callbacks.
- MATCH/REPLACE retain the existing bounded 64-entry regex cache; the JVM
  lookup/clear/compile/insert operation is synchronized. JS keeps sequential
  behavior with the same cache policy.

## Focused verification

```sh
./gradlew :cli:jvmTest --tests '*CompiledFanoutIsolationTest' --tests '*ParallelWorker*' :interpreter:jvmTest --tests '*RegexCacheConcurrencyTest'
```

`jobs-focused.log`: BUILD SUCCESSFUL. CLI: 9 tests, all passed. Interpreter regex
cache test: 1 test, passed (up-to-date on final rerun, executed during preceding
focused run). XML evidence is saved as jobs-after.xml, jobs-workers-after.xml,
jobs-workers-common-after.xml and jobs-regex-after.xml.

Coverage: deterministic compiled overlap, independent expected reports,
jobs=1/4 with 12 entries and strict contracts, shared resource lookup, summary
ordering, trace-label order, first-error order and no partial output, provider
restoration, bounded worker creation/reuse, completion before failure return,
and collector interruption with context alive until completion. Regex test
coordinates four threads and churns more than the cache capacity for both
MATCH and REPLACE; it is a correctness stress check, not a deterministic proof
that an unsynchronized HashMap corrupts on every execution.

## Limits

VMFactory.Metrics uses volatile read-modify-write diagnostic counters and may
lose increments under concurrent calls. No execution output depends on these
counters; this existing boundary is unchanged. No claim is made that arbitrary
external ambient tracers are thread-safe or that simultaneous independent CLI
invocations in one process can each own the global SharedStoreProvider. No
performance claim or benchmark measurement is made by this implementation
slice. Parent full JVM/Node acceptance remains required.
