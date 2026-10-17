# Measured tables

All execution times are microseconds/op; allocations are bytes/op. Lower is better.

## forward — time

| Size | Workload | interpreter | baseline | preparedControl | preparedOptimized |
| --- | --- | --- | --- | --- | --- |
| small | paths | 0.247 | 0.378 | 0.487 | 0.512 |
| small | reduction | 0.609 | 0.890 | 1.176 | 0.889 |
| small | holdoutProjection | 1.007 | 1.349 | 1.675 | 1.595 |
| large | paths | 0.277 | 0.399 | 0.549 | 0.468 |
| large | reduction | 26.45 | 39.29 | 34.91 | 29.03 |
| large | holdoutProjection | 104.36 | 120.37 | 129.76 | 128.93 |

JMH reported 99.9% CI half-width (iteration-based; not a guarantee against order/host effects):

| Size | Workload | interpreter | baseline | preparedControl | preparedOptimized |
| --- | --- | --- | --- | --- | --- |
| small | paths | 0.008 | 0.020 | 0.010 | 0.039 |
| small | reduction | 0.008 | 0.007 | 0.059 | 0.011 |
| small | holdoutProjection | 0.024 | 0.022 | 0.030 | 0.006 |
| large | paths | 0.002 | 0.012 | 0.071 | 0.013 |
| large | reduction | 1.654 | 1.273 | 0.126 | 0.319 |
| large | holdoutProjection | 4.106 | 0.963 | 4.764 | 2.917 |

Allocated bytes/op:

| Size | Workload | interpreter | baseline | preparedControl | preparedOptimized |
| --- | --- | --- | --- | --- | --- |
| small | paths | 784 | 704 | 704 | 704 |
| small | reduction | 812 | 832 | 832 | 800 |
| small | holdoutProjection | 2376 | 1568 | 1568 | 1536 |
| large | paths | 784 | 704 | 704 | 704 |
| large | reduction | 8736 | 8688 | 8688 | 8656 |
| large | holdoutProjection | 193248 | 128280 | 128280 | 128248 |

## reverse — time

| Size | Workload | interpreter | baseline | preparedControl | preparedOptimized |
| --- | --- | --- | --- | --- | --- |
| large | paths | 0.286 | 0.440 | 0.551 | 0.445 |
| large | reduction | 26.45 | 39.75 | 41.85 | 29.19 |
| large | holdoutProjection | 103.44 | 129.53 | 132.67 | 121.54 |

JMH reported 99.9% CI half-width (iteration-based; not a guarantee against order/host effects):

| Size | Workload | interpreter | baseline | preparedControl | preparedOptimized |
| --- | --- | --- | --- | --- | --- |
| large | paths | 0.005 | 0.007 | 0.065 | 0.006 |
| large | reduction | 1.555 | 4.201 | 0.975 | 1.370 |
| large | holdoutProjection | 1.136 | 2.132 | 5.212 | 2.182 |

Allocated bytes/op:

| Size | Workload | interpreter | baseline | preparedControl | preparedOptimized |
| --- | --- | --- | --- | --- | --- |
| large | paths | 784 | 704 | 704 | 704 |
| large | reduction | 8736 | 8688 | 8688 | 8656 |
| large | holdoutProjection | 193248 | 128280 | 128280 | 128248 |

## preparation — time

| Size | Workload | interpreter | baseline | preparedControl | preparedOptimized |
| --- | --- | --- | --- | --- | --- |
| large | reduction | 1.726 | 3.117 | 4.501 | 5.490 |
| large | holdoutProjection | 1.698 | 3.152 | 4.889 | 5.787 |

JMH reported 99.9% CI half-width (iteration-based; not a guarantee against order/host effects):

| Size | Workload | interpreter | baseline | preparedControl | preparedOptimized |
| --- | --- | --- | --- | --- | --- |
| large | reduction | 0.020 | 0.030 | 0.155 | 0.052 |
| large | holdoutProjection | 0.025 | 0.230 | 0.160 | 0.151 |

Allocated bytes/op:

| Size | Workload | interpreter | baseline | preparedControl | preparedOptimized |
| --- | --- | --- | --- | --- | --- |
| large | reduction | 11244 | 19992 | 25192 | 27848 |
| large | holdoutProjection | 13440 | 20724 | 26796 | 29604 |

## fallback — time

| Size | Workload | interpreter | baseline | preparedControl | preparedOptimized |
| --- | --- | --- | --- | --- | --- |
| large | fallbackClosure | 0.149 | 0.229 | 0.223 | 0.228 |

JMH reported 99.9% CI half-width (iteration-based; not a guarantee against order/host effects):

| Size | Workload | interpreter | baseline | preparedControl | preparedOptimized |
| --- | --- | --- | --- | --- | --- |
| large | fallbackClosure | 0.004 | 0.007 | 0.012 | 0.006 |

Allocated bytes/op:

| Size | Workload | interpreter | baseline | preparedControl | preparedOptimized |
| --- | --- | --- | --- | --- | --- |
| large | fallbackClosure | 856 | 824 | 824 | 824 |

## Memory — medians of three fresh JVMs

Heap delta is (fixture + prepared executors + weak references + last output) minus primed fixture. It is an approximate process heap observation. RSS is the process peak, including preparation/JIT; it is not retained heap.

| Lifetime | Engine | Heap delta MiB | Heap range | Peak RSS MiB | RSS range | Heap released with executors KiB | Wrappers collected / repeat |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reuse | interpreter | 0.034 | 0.034–0.035 | 93.98 | 90.83–95.50 | 2.48 | 1,1,1 |
| reuse | baseline | 0.038 | 0.038–0.039 | 108.53 | 107.95–108.94 | 5.96 | 1,1,1 |
| reuse | preparedControl | 0.040 | 0.040–0.041 | 102.86 | 102.55–105.78 | 8.05 | 1,1,1 |
| reuse | preparedOptimized | 0.043 | 0.042–0.044 | 115.58 | 109.62–115.64 | 8.45 | 1,1,1 |
| many | interpreter | 2.554 | 2.552–2.566 | 124.70 | 122.66–125.31 | 2535.93 | 1000,1000,1000 |
| many | baseline | 6.161 | 6.159–6.170 | 135.98 | 133.34–138.62 | 6227.90 | 1000,1000,1000 |
| many | preparedControl | 8.221 | 8.203–8.236 | 141.05 | 139.16–141.62 | 8336.79 | 1000,1000,1000 |
| many | preparedOptimized | 8.603 | 8.591–8.688 | 163.45 | 152.59–165.30 | 8725.64 | 1000,1000,1000 |

Reuse: one executor, 1,000 executions. Many: 1,000 independently compiled/prepared executors, one execution each. One shared large reduction input and final scalar result. Executor-release deltas are also approximate; weak references prove wrapper collection only.
