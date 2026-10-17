from pathlib import Path
import csv,json
p=Path(__file__).resolve().parent
lines=['# Raw-derived measurement tables','', 'Unprofiled JMH means ±99.9% half-width, microseconds/op. GC allocation is B/op.','Preparation uses a separate shorter protocol; do not pool rows across protocols.','']
for name in ('old-baseline','forward','reverse','preparation','fallback'):
 data=json.loads((p/(name+'.json')).read_text())
 lines+=['## '+name,'','| Size | Workload | Engine | us/op ± error | B/op |','| --- | --- | --- | ---: | ---: |']
 for r in data:
  z=r['params'];m=r['primaryMetric'];a=next(v['score'] for k,v in r['secondaryMetrics'].items() if k.endswith('gc.alloc.rate.norm'))
  lines.append(f"| {z['dataset']} | {z['workload']} | {z['engine']} | {m['score']:.4f} ± {m['scoreError']:.4f} | {a:,.1f} |")
 lines.append('')
lines+=['## Fresh-process memory medians','', 'Three observations per row; requested-GC process heap deltas are approximate, not graph sizes.','Executed state includes retained result trees. Peak RSS includes the entire process.','', '| Engine | Lifetime | Prepared heap MiB | Executed heap MiB | Prepared Metaspace used delta MiB | Peak RSS MiB |','| --- | --- | ---: | ---: | ---: | ---: |']
with (p/'memory-summary.csv').open() as f:
 for r in csv.DictReader(f):
  vals=[float(r[k])/1048576 for k in ('preparedUnexecutedMinusFixture','executedMinusFixture','metaspacePreparedDelta','peakRssBytes')]
  lines.append('| '+r['engine']+' | '+r['lifetime']+' | '+' | '.join(f'{v:.3f}' for v in vals)+' |')
(p/'TABLES.md').write_text('\n'.join(lines)+'\n')
