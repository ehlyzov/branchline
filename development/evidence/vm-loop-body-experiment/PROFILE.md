# Baseline projection profile

Frozen prior jar, large unchanged holdoutProjection, same JVM/heap, JMH JFR profile
configuration, one fork, 3x1s warmup, 4x2s measurements per engine, serial runs.
Exact commands and jar/source hashes are retained. Instrumented timing is not
used as final speed evidence. JFR CPU samples are asynchronous with non-safepoint
information enabled; worker-thread nonempty stacks are the denominator.

| Leaf method | Ordinary VM | Prepared optimized | Interpreter |
| --- | ---: | ---: | ---: |
| VM.executeOpcode | 28.0% | — | — |
| VM.run | 17.1% | — | — |
| VM.runPreparedBlocks | — | 24.1% | — |
| ArrayDeque.addFirst | 3.4% | 7.0% | — |
| ArrayDeque.ensureCapacity | 5.2% | 5.9% | — |
| NumericKt.narrowInteger | 3.8% | 5.4% | — |
| ExprEvalSupportKt.unwrapNum | — | — | 20.9% |
| Exec.handleBinary | — | — | 14.5% |
| Exec.evalExpr | — | — | 10.3% |

Worker samples: ordinary557, prepared460, interpreter592. Full exclusive/inclusive
method counts and leaf source lines are in profile-summary-before.json; raw JFR
files are retained. Percentages are sampled method attribution, not cycle counts,
allocation percentages, disjoint semantic cost categories or a removable speedup
bound. Inlining and source-line mappings can charge helpers to enclosing methods.
No exact attribution of branch prediction, cache misses or assembly was collected.

All three execute O(number of rows plus accessed paths and constructed fields).
The selected experiment targets constant factors, not asymptotic complexity.
Ordinary VM additionally performs per-bytecode dispatch; prior preparation retains
a generic dispatch and stack traffic. A generated complete loop can eliminate
that dispatch while retaining helpers as a bounded causal experiment. Whether
JVM call inlining makes it faster is unknown until measured. Scope: a reusable
single comprehension shape, not fixture-specific code or numeric specialization.

## After generated-loop execution

Same profiling protocol, frozen final jar, one loopBody/large/projection fork.
498 worker samples: LoopContext.gt87 (17.5%), ArrayDeque methods130 (26.1%),
NumericKt.narrowInteger37 (7.4%), VM.access$setPc$p23 (4.6%). Generated run itself
is a leaf in9 samples (1.8%); methods inlined beneath it or context calls can be
charged elsewhere. Ordinary VM.run/executeOpcode have zero leaf samples in this
recording; they still execute the small prefix/suffix and are not eliminated
from the program. `profile-summary.json` and raw compressed events retain evidence.

This supports removal of the body dispatcher and identifies remaining sampled
work in canonical stack operations, comparisons/numeric helpers and PC writes.
It does not quantify a guaranteed speedup from removing any one method or establish
machine-level causes such as cache misses. The controlled uninstrumented JMH
comparison, not the profiled scores or ratios of sample shares, establishes the
11–16% large-projection time improvement versus ordinary VM. No confident
interpreter advantage was established.
