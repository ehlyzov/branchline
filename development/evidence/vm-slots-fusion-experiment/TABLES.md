# Measured tables

All execution times are microseconds/op; allocations are bytes/op. Lower is better.

## confirmation — time

| Size | Workload | interpreter | baseline | slots | fusion | both |
| --- | --- | --- | --- | --- | --- | --- |
| large | paths | 0.269 | 0.386 | 0.647 | 0.473 | 0.509 |
| large | comprehension | 21.25 | 36.61 | 45.11 | 48.29 | 43.48 |
| large | reduction | 26.62 | 36.44 | 36.60 | 43.36 | 36.68 |

JMH reported 99.9% CI half-width (iteration-based; not a guarantee against order/host effects):

| Size | Workload | interpreter | baseline | slots | fusion | both |
| --- | --- | --- | --- | --- | --- | --- |
| large | paths | 0.009 | 0.008 | 0.008 | 0.022 | 0.007 |
| large | comprehension | 0.695 | 0.511 | 2.035 | 0.369 | 0.245 |
| large | reduction | 1.201 | 1.783 | 4.274 | 0.684 | 0.185 |

Allocated bytes/op:

| Size | Workload | interpreter | baseline | slots | fusion | both |
| --- | --- | --- | --- | --- | --- | --- |
| large | paths | 784 | 704 | 1304 | 880 | 1272 |
| large | comprehension | 32768 | 5672 | 6216 | 5872 | 6216 |
| large | reduction | 8736 | 8688 | 9136 | 8888 | 9144 |

## factorial — time

| Size | Workload | interpreter | baseline | slots | fusion | both |
| --- | --- | --- | --- | --- | --- | --- |
| small | paths | 0.372 | 0.524 | 0.781 | 0.664 | 0.719 |
| small | comprehension | 0.803 | 1.292 | 1.907 | 1.747 | 1.933 |
| small | reduction | 0.839 | 1.259 | 1.511 | 1.676 | 1.524 |
| small | holdoutProjection | 1.395 | 1.954 | 2.535 | 2.768 | 2.612 |
| small | holdoutDeepPaths | 0.345 | 0.504 | 0.786 | 0.587 | 0.702 |
| large | paths | 0.387 | 0.611 | 0.824 | 0.641 | 0.735 |
| large | comprehension | 31.39 | 53.26 | 64.18 | 68.67 | 64.72 |
| large | reduction | 37.97 | 54.30 | 49.82 | 64.07 | 49.57 |
| large | holdoutProjection | 162.51 | 167.54 | 206.54 | 200.96 | 204.72 |
| large | holdoutDeepPaths | 0.380 | 0.511 | 0.895 | 0.655 | 0.739 |

JMH reported 99.9% CI half-width (iteration-based; not a guarantee against order/host effects):

| Size | Workload | interpreter | baseline | slots | fusion | both |
| --- | --- | --- | --- | --- | --- | --- |
| small | paths | 0.049 | 0.034 | 0.013 | 0.055 | 0.020 |
| small | comprehension | 0.239 | 0.044 | 0.029 | 0.069 | 0.023 |
| small | reduction | 0.045 | 0.024 | 0.178 | 0.084 | 0.046 |
| small | holdoutProjection | 0.129 | 0.202 | 0.051 | 0.040 | 0.096 |
| small | holdoutDeepPaths | 0.026 | 0.017 | 0.006 | 0.034 | 0.052 |
| large | paths | 0.004 | 0.093 | 0.038 | 0.049 | 0.028 |
| large | comprehension | 0.385 | 2.397 | 1.391 | 1.498 | 1.763 |
| large | reduction | 4.640 | 2.820 | 4.252 | 4.523 | 0.595 |
| large | holdoutProjection | 0.899 | 4.997 | 1.871 | 1.599 | 2.244 |
| large | holdoutDeepPaths | 0.011 | 0.010 | 0.122 | 0.010 | 0.029 |

Allocated bytes/op:

| Size | Workload | interpreter | baseline | slots | fusion | both |
| --- | --- | --- | --- | --- | --- | --- |
| small | paths | 784 | 704 | 1296 | 888 | 1328 |
| small | comprehension | 1376 | 816 | 1296 | 1016 | 1352 |
| small | reduction | 824 | 832 | 1288 | 1048 | 1288 |
| small | holdoutProjection | 2376 | 1568 | 2168 | 1992 | 2168 |
| small | holdoutDeepPaths | 632 | 552 | 1064 | 736 | 1040 |
| large | paths | 784 | 704 | 1304 | 888 | 1272 |
| large | comprehension | 32768 | 5672 | 6216 | 5872 | 6216 |
| large | reduction | 8736 | 8688 | 9144 | 8888 | 9144 |
| large | holdoutProjection | 193253 | 128281 | 156001 | 144085 | 144263 |
| large | holdoutDeepPaths | 648 | 552 | 1072 | 736 | 1060 |

## preparation — time

| Size | Workload | interpreter | baseline | slots | fusion | both |
| --- | --- | --- | --- | --- | --- | --- |
| large | reduction | 2.340 | 4.409 | 8.025 | 7.994 | 8.522 |
| large | holdoutProjection | 2.371 | 4.582 | 9.294 | 9.299 | 10.27 |

JMH reported 99.9% CI half-width (iteration-based; not a guarantee against order/host effects):

| Size | Workload | interpreter | baseline | slots | fusion | both |
| --- | --- | --- | --- | --- | --- | --- |
| large | reduction | 0.048 | 0.028 | 0.072 | 0.134 | 0.299 |
| large | holdoutProjection | 0.018 | 0.246 | 0.079 | 0.513 | 0.426 |

Allocated bytes/op:

| Size | Workload | interpreter | baseline | slots | fusion | both |
| --- | --- | --- | --- | --- | --- | --- |
| large | reduction | 11292 | 20020 | 40960 | 40128 | 41472 |
| large | holdoutProjection | 13464 | 20788 | 49172 | 49160 | 50052 |

## fallback — time

| Size | Workload | interpreter | baseline | slots | fusion | both |
| --- | --- | --- | --- | --- | --- | --- |
| large | fallbackClosure | 0.202 | 0.229 | 0.217 | 0.224 | 0.215 |

JMH reported 99.9% CI half-width (iteration-based; not a guarantee against order/host effects):

| Size | Workload | interpreter | baseline | slots | fusion | both |
| --- | --- | --- | --- | --- | --- | --- |
| large | fallbackClosure | 0.006 | 0.064 | 0.006 | 0.003 | 0.004 |

Allocated bytes/op:

| Size | Workload | interpreter | baseline | slots | fusion | both |
| --- | --- | --- | --- | --- | --- | --- |
| large | fallbackClosure | 856 | 824 | 920 | 920 | 920 |

## Memory — medians of three fresh JVMs

Heap delta is (fixture + prepared executors + weak references + last output) minus primed fixture. It is an approximate process heap observation. RSS is the process peak, including preparation/JIT; it is not retained heap.

| Lifetime | Engine | Heap delta MiB | Heap range | Peak RSS MiB | RSS range | Heap released with executors KiB | Wrappers collected / repeat |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reuse | interpreter | 0.034 | 0.034–0.034 | 98.05 | 97.88–99.05 | 2.48 | 1,1,1 |
| reuse | baseline | 0.038 | 0.038–0.038 | 111.78 | 105.64–112.02 | 5.95 | 1,1,1 |
| reuse | slots | 0.039 | 0.038–0.039 | 116.08 | 115.27–119.16 | 6.34 | 1,1,1 |
| reuse | fusion | 0.042 | 0.042–0.042 | 121.23 | 113.56–121.72 | 6.48 | 1,1,1 |
| reuse | both | 0.039 | 0.039–0.039 | 132.19 | 131.83–133.67 | 6.83 | 1,1,1 |
| many | interpreter | 2.553 | 2.552–2.553 | 108.12 | 107.94–108.36 | 2536.23 | 1000,1000,1000 |
| many | baseline | 6.156 | 6.151–6.193 | 141.62 | 121.56–144.52 | 6221.95 | 1000,1000,1000 |
| many | slots | 6.525 | 6.525–6.529 | 175.14 | 174.16–177.88 | 6600.58 | 1000,1000,1000 |
| many | fusion | 6.668 | 6.664–6.687 | 165.64 | 131.94–170.42 | 6745.95 | 1000,1000,1000 |
| many | both | 7.006 | 7.006–7.008 | 208.17 | 207.73–210.23 | 7092.87 | 1000,1000,1000 |

Reuse: one executor, 1,000 executions. Many: 1,000 independently compiled/prepared executors, one execution each. One shared large reduction input and final scalar result. Executor-release deltas are also approximate; weak references prove wrapper collection only.
