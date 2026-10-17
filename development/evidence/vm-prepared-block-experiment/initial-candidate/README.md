# Superseded first candidate and JIT eligibility diagnosis

This is preserved diagnostic evidence, not the final optimization result.
The first candidate passed gate4, 538 parent comparisons and 32 fixture configs.
The parent interrupted the first forward timing matrix after preparedControl
small workloads showed an order-of-magnitude regression. Completed default
point estimates included paths5.607us, reduction13.236us and projection19.724us.
The original jar is local at `/tmp/branchline-vm-prepared/initial-candidate.jar`;
its hash and 68 source hashes are in this directory. The partial forward log is
retained; a possibly incomplete JSON must not be treated as a full matrix.

Direct classfile inspection (`../class_sizes.py`) found executeBlocks Code length
8,935 bytes, versus executeExperimental3,459 and executeOpcode2,606. Local
`java -XX:+UnlockDiagnosticVMOptions -XX:+PrintFlagsFinal -version` reported
`DontCompileHugeMethods = true {product} {default}` on Temurin21.0.11.

Controlled diagnostic runs use the same initial jar and preparedControl/small/
reduction fixture, one fork, two1s warmups, three1s measurements, gc profiler,
-Xms256m -Xmx768m/G1. Only the explicit DontCompileHugeMethods boolean differs:

| JVM option | Time us/op | JMH99.9% half-width | B/op |
| --- | ---: | ---: | ---: |
| +DontCompileHugeMethods | 12.917 | 1.203 | 832.005 |
| -DontCompileHugeMethods | 1.481 | 0.314 | 832.001 |

The switch changes eligibility for all huge methods, not one named method.
Together with source/class-size inspection this supports the diagnosis that
the new large execution method was the dominant implementation problem; it is
not an assembly-level attribution of every cycle. The diagnostic flag is not
used in any final comparison or proposed production configuration.

Kotlin replicated the whole-invocation nested finally across early returns in
executeBlocks. One bounded correction extracted the unchanged loop into
runPreparedBlocks and cleanup into finishPreparedBlocks. Guards, patterns,
operands, status updates and exception/cleanup ordering were preserved.
The final hot-loop Code length is3,120 bytes (`../final-method-sizes.json`).

After the correction the parent reran all gates/probes (gate5 and final acceptance
logs), froze a new jar/source manifest and restarted the entire measurement
protocol under default JVM options. Initial and final results are not pooled.
No additional optimization patterns were added in response to the measurements.

Exact diagnostic parameters and fork data are retained inside
huge-method-default.json and huge-method-enabled.json. Reproduction uses:

```bash
java -jar /tmp/branchline-vm-prepared/initial-candidate.jar '.*VMPreparedBlockBenchmark.execute' -p engine=preparedControl -p dataset=small -p workload=reduction -t 1 -f 1 -wi 2 -i 3 -w 1s -r 1s -jvmArgs '-Xms256m -Xmx768m -XX:+UseG1GC -XX:+DontCompileHugeMethods' -prof gc -rf json -rff huge-method-default.json
```

Change only +DontCompileHugeMethods to -DontCompileHugeMethods and the output
filename for the paired diagnostic. The initial driver and its children were
terminated intentionally (driver exit143); this is not a test failure.
