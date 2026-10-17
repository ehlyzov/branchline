# After-change JFR profile

The serial instrumented localLoop projection run sampled 499 JMH-worker stacks.
Leading leaf-method attribution: String.equals31.1%, HashMap.getNode21.2%,
HashMap.putVal7.6%, LinkedHashMap.linkNodeAtEnd4.6%, generated run3.2%,
LocalLoopSupport.readStatic3.0%, String.hashCode2.8%, safeStatic2.8%,
LocalContext.key2.4%, hasVariable2.4%. Raw recording and compressed JFR JSON,
exact command and profile-summary.json are preserved.

The previous experiment's loopBody profile attributed26.1% to ArrayDeque methods,
17.5% to comparison gt,7.4% to numeric narrowing and4.6% to PC writes. It was a
separate prior run, not a simultaneously randomized same-binary profile. The new
profile is consistent with shifting cost toward field lookup/string comparison
and output map construction after removing most virtual operand-stack/numeric
helper work. Method attribution includes inlined work and sampling bias; it is
not a precise removable-cost partition or an assembly-level causal experiment.

The JFR run's instrumented timing is excluded from speed comparisons. New source
and classfile inspection independently establish real locals/direct primitive
ops/spill maps. Further field-layout or reuse/cache experiments would need separate
correctness and real-workload evidence; this turn does not expand the optimizer.
