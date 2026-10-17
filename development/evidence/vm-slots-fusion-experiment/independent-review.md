# Independent experimental-runtime review

Reviewed the experimental runtime, slot-frame preparation/materialization, and
fusion plans independently of the implementation owner. This review makes no
performance claim and does not substitute for the parent-owned build/test gate.

## Review scope and observations

- Host boundaries materialize initialized local aliases and flush deferred named
  bindings before invoking host code. Alias detachment is permanent within the
  invocation; later named writes remain live while local reads retain baseline
  stale-local behavior. Host completion invalidates cached named values.
- The slot frame tracks presence separately from nullable values. Deferred
  removals, updates of existing keys, and insertion serials preserve ordinary
  insertion-ordered map behavior at flush boundaries. Loop state keeps the
  previous presence/value and restores it when the loop ends.
- Final return/error cleanup materializes locals and flushes named writes.
  Original program counters, operand-stack effects, and geometric local-array
  capacity matter to snapshot parity; tests owned by the runtime agent cover
  final/error snapshots and mixed aliased/non-aliased local writes.
- A custom collection or environment, tracing, unsupported call/closure/TRY/
  suspension operations, and other observable operations use explicit fallback.
  Mid-invocation fallback resumes at the current source instruction, never
  replays the program. Fusion path prefixes may complete before that boundary.
- Slot plans are allocated only when slots are requested, fusion plans only when
  fusion is requested, and unsupported programs have neither. Effective flags
  describe executed work and must remain subsets of requested flags. Disabled
  diagnostic collection reports zero mechanism counters.

## Findings and limitations

The initial skipped DUP overflow check in slot mirror elimination was reported
and corrected by the runtime owner. The initial host-error tracing concern was
resolved by reading baseline `run`/`tracedCall`: installing a tracer inside a
host that then throws produces no call-frame trace event in either path.

Prepared plans retain the original mutable bytecode arrays. Mutating bytecode
after preparation is outside this experiment's supported usage; no freshness
guard is present. Concurrent mutation of a running environment or bytecode is
also outside the experiment. The ordering argument applies to insertion-ordered
maps; general HashMap iteration order is not a language guarantee.

## Additional adversarial evidence to run

`ExperimentalHostBoundaryAcceptanceTest` adds independent baseline comparisons
for two host boundaries, host-time snapshots, named-binding removal/reinsertion,
present-null values, and mirrored stores after alias detachment. It also checks
all slot/fusion/diagnostic flag combinations and unsupported-program reports.
The parent owns running these tests on JVM/Node and recording the actual results.

## Parent execution result

The final JVM/Node VM, conformance and CLI gate completed successfully:
797 passed, one existing disabled test, zero failures (`final-gate3.log`,
`test-counts.json`). The parent acceptance probe also passed 1,016 combinations
(`parent-after2.log`), after detecting and correcting final-local snapshot
materialization/capacity in the initial prototype (`parent-before.log`).
Performance and memory acceptance are separate and recorded in `README.md`.
