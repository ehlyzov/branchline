# Raw-derived measurement tables

Unprofiled JMH means ±99.9% half-width, microseconds/op. GC allocation is B/op.
Preparation uses a separate shorter protocol; do not pool rows across protocols.

## old-baseline

| Size | Workload | Engine | us/op ± error | B/op |
| --- | --- | --- | ---: | ---: |
| large | paths | baseline | 0.4139 ± 0.0225 | 704.0 |
| large | reduction | baseline | 40.4288 ± 0.3072 | 8,688.0 |
| large | holdoutProjection | baseline | 126.3180 ± 2.3655 | 128,280.1 |

## forward

| Size | Workload | Engine | us/op ± error | B/op |
| --- | --- | --- | ---: | ---: |
| small | paths | interpreter | 0.2669 ± 0.0252 | 784.0 |
| small | reduction | interpreter | 0.6345 ± 0.0123 | 800.0 |
| small | holdoutProjection | interpreter | 1.0437 ± 0.0866 | 2,376.0 |
| small | paths | baseline | 0.3816 ± 0.0193 | 704.0 |
| small | reduction | baseline | 0.9101 ± 0.0064 | 832.0 |
| small | holdoutProjection | baseline | 1.3520 ± 0.0190 | 1,568.0 |
| small | paths | loopBody | 0.3714 ± 0.0186 | 704.0 |
| small | reduction | loopBody | 0.9461 ± 0.0173 | 832.0 |
| small | holdoutProjection | loopBody | 1.3424 ± 0.0896 | 1,568.0 |
| small | paths | localLoop | 0.4056 ± 0.0075 | 704.0 |
| small | reduction | localLoop | 0.9278 ± 0.0159 | 832.0 |
| small | holdoutProjection | localLoop | 0.8480 ± 0.0500 | 1,504.0 |
| large | paths | interpreter | 0.3062 ± 0.0135 | 784.0 |
| large | reduction | interpreter | 25.9188 ± 0.1548 | 8,724.0 |
| large | holdoutProjection | interpreter | 118.3508 ± 3.2481 | 193,248.0 |
| large | paths | baseline | 0.4215 ± 0.0330 | 704.0 |
| large | reduction | baseline | 40.1947 ± 0.3144 | 8,688.0 |
| large | holdoutProjection | baseline | 126.0570 ± 2.5573 | 128,280.1 |
| large | paths | loopBody | 0.4135 ± 0.0212 | 704.0 |
| large | reduction | loopBody | 40.3336 ± 0.1724 | 8,688.0 |
| large | holdoutProjection | loopBody | 103.5326 ± 1.4672 | 128,280.0 |
| large | paths | localLoop | 0.4478 ± 0.0337 | 704.0 |
| large | reduction | localLoop | 39.9109 ± 1.5124 | 8,688.0 |
| large | holdoutProjection | localLoop | 71.2280 ± 4.3239 | 120,472.0 |

## reverse

| Size | Workload | Engine | us/op ± error | B/op |
| --- | --- | --- | ---: | ---: |
| large | paths | localLoop | 0.4541 ± 0.0100 | 704.0 |
| large | reduction | localLoop | 40.4147 ± 0.6301 | 8,688.0 |
| large | holdoutProjection | localLoop | 71.5896 ± 3.5238 | 120,472.0 |
| large | paths | loopBody | 0.4505 ± 0.0174 | 704.0 |
| large | reduction | loopBody | 37.5821 ± 0.6363 | 8,688.0 |
| large | holdoutProjection | loopBody | 108.7170 ± 2.0908 | 128,280.0 |
| large | paths | baseline | 0.4443 ± 0.0091 | 704.0 |
| large | reduction | baseline | 39.4713 ± 3.1908 | 8,688.0 |
| large | holdoutProjection | baseline | 124.5096 ± 2.5380 | 128,280.1 |
| large | paths | interpreter | 0.2840 ± 0.0022 | 784.0 |
| large | reduction | interpreter | 27.3250 ± 1.6994 | 8,736.0 |
| large | holdoutProjection | interpreter | 113.9289 ± 10.2765 | 193,248.0 |

## preparation

| Size | Workload | Engine | us/op ± error | B/op |
| --- | --- | --- | ---: | ---: |
| large | reduction | interpreter | 1.7509 ± 0.0392 | 11,276.0 |
| large | holdoutProjection | interpreter | 1.8227 ± 0.1219 | 13,520.0 |
| large | reduction | baseline | 3.3193 ± 0.0599 | 20,164.0 |
| large | holdoutProjection | baseline | 3.9704 ± 2.2892 | 20,752.0 |
| large | reduction | loopBody | 3.7696 ± 0.0905 | 28,633.2 |
| large | holdoutProjection | loopBody | 68.7734 ± 25.5279 | 66,442.0 |
| large | reduction | localLoop | 3.5549 ± 0.0119 | 28,492.0 |
| large | holdoutProjection | localLoop | 95.9276 ± 11.3414 | 120,917.9 |

## fallback

| Size | Workload | Engine | us/op ± error | B/op |
| --- | --- | --- | ---: | ---: |
| large | fallbackClosure | interpreter | 0.1502 ± 0.0017 | 856.0 |
| large | fallbackClosure | baseline | 0.2313 ± 0.0103 | 824.0 |
| large | fallbackClosure | loopBody | 0.2361 ± 0.0058 | 824.0 |
| large | fallbackClosure | localLoop | 0.2247 ± 0.0024 | 824.0 |

## Fresh-process memory medians

Three observations per row; requested-GC process heap deltas are approximate, not graph sizes.
Executed state includes retained result trees. Peak RSS includes the entire process.

| Engine | Lifetime | Prepared heap MiB | Executed heap MiB | Prepared Metaspace used delta MiB | Peak RSS MiB |
| --- | --- | ---: | ---: | ---: | ---: |
| baseline | many | 6.095 | 117.159 | 0.269 | 265.031 |
| baseline | reuse | 0.006 | 0.120 | 0.002 | 162.516 |
| interpreter | many | 3.224 | 3.701 | 0.132 | 192.859 |
| interpreter | reuse | 0.003 | 0.115 | 0.002 | 163.781 |
| localLoop | many | 11.605 | 122.475 | 5.655 | 337.016 |
| localLoop | reuse | 0.012 | 0.129 | 0.022 | 161.641 |
| loopBody | many | 9.480 | 120.532 | 3.502 | 308.859 |
| loopBody | reuse | 0.009 | 0.122 | 0.033 | 166.000 |
