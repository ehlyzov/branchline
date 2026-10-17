"""Render measured tables only; interpretation lives in README.md."""
import pathlib, json, statistics, re
out = pathlib.Path(__file__).resolve().parent
engines = ['interpreter', 'baseline', 'preparedControl', 'preparedOptimized']
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
parts += ['## Memory — medians of three fresh JVMs\n',
          'Heap delta is (fixture + prepared executors + weak references + last output) minus primed fixture. It is an approximate process heap observation. RSS is the process peak, including preparation/JIT; it is not retained heap.\n']
memory={}
for p in sorted(out.glob('memory-*.log')):
    lines=p.read_text().splitlines()
    x=json.loads(next(l for l in lines if l.startswith('{')))
    rss=int(next(re.search(r'\s*(\d+)\s+maximum resident set size',l).group(1) for l in lines if 'maximum resident set size' in l))
    h=x['heapUsedBytes'];d=x['approximateHeapDeltasBytes']
    memory.setdefault((x['lifetime'],x['engine']),[]).append((d['executorsAndOutputMinusBaseline'],rss,h['fixtureExecutorsWeakReferencesAndLastOutput']-h['fixtureWeakReferencesAndLastOutput'],x['collectedExecutorWrappers']))
rows=[]
for lifetime in ['reuse','many']:
    for e in engines:
        rs=memory[lifetime,e]
        h=[r[0]/1048576 for r in rs];rss=[r[1]/1048576 for r in rs]
        rows.append([lifetime,e,fmt(statistics.median(h)),f'{min(h):.3f}–{max(h):.3f}',fmt(statistics.median(rss)),f'{min(rss):.2f}–{max(rss):.2f}',f'{statistics.median(r[2] for r in rs)/1024:.2f}',','.join(str(r[3]) for r in rs)])
parts.append(table(['Lifetime','Engine','Heap delta MiB','Heap range','Peak RSS MiB','RSS range','Heap released with executors KiB','Wrappers collected / repeat'],rows))
parts += ['Reuse: one executor, 1,000 executions. Many: 1,000 independently compiled/prepared executors, one execution each. One shared large reduction input and final scalar result. Executor-release deltas are also approximate; weak references prove wrapper collection only.\n']
(out/'TABLES.md').write_text('\n'.join(parts))
print('TABLES.md generated')
