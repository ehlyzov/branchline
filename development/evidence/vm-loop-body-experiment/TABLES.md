# Measured tables

All execution times are microseconds/op; allocations are bytes/op. Lower is better.

## forward — time

| Size | Workload | interpreter | baseline | preparedOptimized | loopBody |
| --- | --- | --- | --- | --- | --- |
| small | paths | 0.270 | 0.387 | 0.474 | 0.389 |
| small | reduction | 0.613 | 0.927 | 0.994 | 0.928 |
| small | holdoutProjection | 1.119 | 1.341 | 1.535 | 1.316 |
| large | paths | 0.283 | 0.417 | 0.472 | 0.421 |
| large | reduction | 25.67 | 39.53 | 29.95 | 38.96 |
| large | holdoutProjection | 106.49 | 124.17 | 123.26 | 104.22 |

JMH reported 99.9% CI half-width (iteration-based; not a guarantee against order/host effects):

| Size | Workload | interpreter | baseline | preparedOptimized | loopBody |
| --- | --- | --- | --- | --- | --- |
| small | paths | 0.019 | 0.009 | 0.059 | 0.024 |
| small | reduction | 0.064 | 0.015 | 0.004 | 0.012 |
| small | holdoutProjection | 0.011 | 0.030 | 0.027 | 0.093 |
| large | paths | 0.005 | 0.029 | 0.041 | 0.005 |
| large | reduction | 0.359 | 0.133 | 2.533 | 0.231 |
| large | holdoutProjection | 13.72 | 0.716 | 5.299 | 0.546 |

Allocated bytes/op:

| Size | Workload | interpreter | baseline | preparedOptimized | loopBody |
| --- | --- | --- | --- | --- | --- |
| small | paths | 784 | 704 | 704 | 704 |
| small | reduction | 824 | 832 | 800 | 832 |
| small | holdoutProjection | 2376 | 1568 | 1536 | 1568 |
| large | paths | 784 | 704 | 704 | 704 |
| large | reduction | 8724 | 8688 | 8656 | 8688 |
| large | holdoutProjection | 193248 | 128280 | 128248 | 128280 |

## reverse — time

| Size | Workload | interpreter | baseline | preparedOptimized | loopBody |
| --- | --- | --- | --- | --- | --- |
| large | paths | 0.277 | 0.404 | 0.545 | 0.425 |
| large | reduction | 28.07 | 38.49 | 28.91 | 38.91 |
| large | holdoutProjection | 110.82 | 123.49 | 128.66 | 109.65 |

JMH reported 99.9% CI half-width (iteration-based; not a guarantee against order/host effects):

| Size | Workload | interpreter | baseline | preparedOptimized | loopBody |
| --- | --- | --- | --- | --- | --- |
| large | paths | 0.004 | 0.028 | 0.016 | 0.024 |
| large | reduction | 1.159 | 0.486 | 2.215 | 1.478 |
| large | holdoutProjection | 9.777 | 1.301 | 6.700 | 7.418 |

Allocated bytes/op:

| Size | Workload | interpreter | baseline | preparedOptimized | loopBody |
| --- | --- | --- | --- | --- | --- |
| large | paths | 784 | 704 | 704 | 704 |
| large | reduction | 8724 | 8688 | 8656 | 8688 |
| large | holdoutProjection | 193248 | 128280 | 128248 | 128280 |

## preparation — time

| Size | Workload | interpreter | baseline | preparedOptimized | loopBody |
| --- | --- | --- | --- | --- | --- |
| large | reduction | 1.700 | 3.199 | 5.464 | 3.492 |
| large | holdoutProjection | 1.714 | 3.251 | 5.844 | 50.20 |

JMH reported 99.9% CI half-width (iteration-based; not a guarantee against order/host effects):

| Size | Workload | interpreter | baseline | preparedOptimized | loopBody |
| --- | --- | --- | --- | --- | --- |
| large | reduction | 0.015 | 0.020 | 0.049 | 0.026 |
| large | holdoutProjection | 0.024 | 0.010 | 0.077 | 3.550 |

Allocated bytes/op:

| Size | Workload | interpreter | baseline | preparedOptimized | loopBody |
| --- | --- | --- | --- | --- | --- |
| large | reduction | 11308 | 20084 | 27928 | 28412 |
| large | holdoutProjection | 13456 | 20732 | 29624 | 64670 |

## fallback — time

| Size | Workload | interpreter | baseline | preparedOptimized | loopBody |
| --- | --- | --- | --- | --- | --- |
| large | fallbackClosure | 0.151 | 0.219 | 0.225 | 0.228 |

JMH reported 99.9% CI half-width (iteration-based; not a guarantee against order/host effects):

| Size | Workload | interpreter | baseline | preparedOptimized | loopBody |
| --- | --- | --- | --- | --- | --- |
| large | fallbackClosure | 0.002 | 0.003 | 0.005 | 0.015 |

Allocated bytes/op:

| Size | Workload | interpreter | baseline | preparedOptimized | loopBody |
| --- | --- | --- | --- | --- | --- |
| large | fallbackClosure | 856 | 824 | 824 | 824 |

## Memory — medians of three fresh JVMs

Projection, shared input. Prepared-only heap excludes execution; executed heap includes retained runtime states/results. Metaspace is separate non-heap memory; RSS is process peak. All deltas are approximate.

| Lifetime | Engine | Prepared heap MiB | Executed heap MiB | Prepared metaspace MiB | Peak RSS MiB | Generated classes | Process classes unloaded after release | Wrappers collected/repeat |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| reuse | interpreter | 0.003 | 0.115 | 0.002 | 161.70 | 0 | 0 | 1,1,1 |
| reuse | baseline | 0.006 | 0.120 | 0.002 | 160.03 | 0 | 0 | 1,1,1 |
| reuse | preparedOptimized | 0.009 | 0.122 | 0.002 | 114.19 | 0 | 0 | 1,1,1 |
| reuse | loopBody | 0.009 | 0.122 | 0.026 | 163.77 | 1 | 1 | 1,1,1 |
| many | interpreter | 3.209 | 3.686 | 0.127 | 190.77 | 0 | 0 | 1000,1000,1000 |
| many | baseline | 6.069 | 117.11 | 0.267 | 261.47 | 0 | 0 | 1000,1000,1000 |
| many | preparedOptimized | 9.145 | 120.48 | 0.328 | 305.69 | 0 | 0 | 1000,1000,1000 |
| many | loopBody | 9.468 | 120.52 | 3.505 | 304.27 | 1000 | 1000 | 1000,1000,1000 |

Reuse: one executor prepared once, executed 1000 times. Many: 1000 independent plans/executors, each executed once. No generated-class cache. Class-unload counters are process-wide and do not identify individual loaders. Raw ranges and committed pools remain in memory-raw.csv and memory logs; weak references prove wrapper collection only.
