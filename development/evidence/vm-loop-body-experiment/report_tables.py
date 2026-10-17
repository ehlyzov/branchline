"""Render measured tables only; interpretation lives in README.md."""
import pathlib, json, statistics, re
out = pathlib.Path(__file__).resolve().parent
engines = ['interpreter', 'baseline', 'preparedOptimized', 'loopBody']
def read(name): return json.loads((out / (name+'.json')).read_text())
def fmt(x): return f'{x:.3f}' if abs(x)<10 else f'{x:.2f}'
def table(headers, rows):
    return '\n'.join(['| '+' | '.join(headers)+' |', '| '+' | '.join(['---']*len(headers))+' |'] +
                     ['| '+' | '.join(map(str, r))+' |' for r in rows])+'\n'
parts = ['# Measured tables\n', 'All execution times are microseconds/op; allocations are bytes/op. Lower is better.\n']
for name in ['forward', 'reverse', 'preparation', 'fallback']:
    data = read(name)
    groups = {}
    for x in data: groups.setdefault((x['params']['dataset'], x['params']['workload']), {})[x['params']['engine']] = x
    rows=[]; alloc=[]; error=[]
    for (size, workload), group in groups.items():
        rows.append([size,workload]+[fmt(group[e]['primaryMetric']['score']) for e in engines])
        error.append([size,workload]+[fmt(group[e]['primaryMetric']['scoreError']) for e in engines])
        alloc.append([size,workload]+[str(round(next(v['score'] for k,v in group[e]['secondaryMetrics'].items() if k.endswith('gc.alloc.rate.norm')))) for e in engines])
    headers=['Size','Workload']+engines
    parts += ['## '+name+' — time\n',table(headers, rows),
              'JMH reported 99.9% CI half-width (iteration-based; not a guarantee against order/host effects):\n',table(headers,error),
              'Allocated bytes/op:\n',table(headers,alloc)]
parts += ['## Memory — medians of three fresh JVMs\n', 'Projection, shared input. Prepared-only heap excludes execution; executed heap includes retained runtime states/results. Metaspace is separate non-heap memory; RSS is process peak. All deltas are approximate.\n']
import csv
memory=list(csv.DictReader((out/'memory-summary.csv').open()))
raw=list(csv.DictReader((out/'memory-raw.csv').open()))
rows=[]
for life in ['reuse','many']:
 for e in engines:
  m=next(x for x in memory if x['engine']==e and x['lifetime']==life)
  rs=[x for x in raw if x['engine']==e and x['lifetime']==life]
  def mib(k):return fmt(float(m[k])/1048576)
  rows.append([life,e,mib('preparedUnexecutedMinusFixture'),mib('executedMinusFixture'),mib('metaspacePreparedDelta'),mib('peakRssBytes'),int(float(m['generatedClasses'])),int(float(m['classesUnloadedAfterExecutorRelease'])),','.join(str(int(float(x['collectedWrappers']))) for x in rs)])
parts.append(table(['Lifetime','Engine','Prepared heap MiB','Executed heap MiB','Prepared metaspace MiB','Peak RSS MiB','Generated classes','Process classes unloaded after release','Wrappers collected/repeat'],rows))
parts += ['Reuse: one executor prepared once, executed 1000 times. Many: 1000 independent plans/executors, each executed once. No generated-class cache. Class-unload counters are process-wide and do not identify individual loaders. Raw ranges and committed pools remain in memory-raw.csv and memory logs; weak references prove wrapper collection only.\n']
(out/'TABLES.md').write_text('\n'.join(parts))
print('TABLES.md generated')
