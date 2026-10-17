import pathlib,json,csv,re,statistics
out=pathlib.Path(__file__).resolve().parent
engines=['interpreter','baseline','preparedControl','preparedOptimized']
def read(name):
    p=out/name
    return json.loads(p.read_text()) if p.exists() and p.stat().st_size else []
def save(name,rows):
    if not rows:return
    with (out/name).open('w') as f:
        w=csv.DictWriter(f,fieldnames=list(rows[0]));w.writeheader();w.writerows(rows)
def metric(x):
    return dict(us=x['primaryMetric']['score'],error99_9=x['primaryMetric']['scoreError'],
                allocatedBytes=next(v['score'] for k,v in x['secondaryMetrics'].items() if k.endswith('gc.alloc.rate.norm')))
effects=[]
for name in ['old-baseline','forward','reverse','preparation','fallback']:
    data=read(name+'.json'); save(name+'-summary.csv',[dict(**x['params'],**metric(x)) for x in data])
    if name not in ['forward','reverse']:continue
    groups={}
    for x in data:groups.setdefault((x['params']['dataset'],x['params']['workload']),{})[x['params']['engine']]=metric(x)
    for (size,workload),g in groups.items():
        i,b,c,o=[g[e] for e in engines]
        effects.append(dict(run=name,dataset=size,workload=workload,
            interpreterUs=i['us'],baselineUs=b['us'],controlUs=c['us'],optimizedUs=o['us'],
            controlVsBaseline=c['us']/b['us'],optimizedVsControl=o['us']/c['us'],optimizedVsBaseline=o['us']/b['us'],optimizedVsInterpreter=o['us']/i['us'],
            optimizedAllocationDelta=o['allocatedBytes']-b['allocatedBytes'],
            conservativeTimeRatio=(o['us']+o['error99_9'])/(b['us']-b['error99_9']) if b['us']>b['error99_9'] else None))
save('effects.csv',effects)
memory={}
for p in sorted(out.glob('memory-*.log')):
    lines=p.read_text().splitlines()
    if not any('maximum resident set size' in l for l in lines):continue
    x=json.loads(next(l for l in lines if l.startswith('{')))
    rss=int(next(re.search(r'\s*(\d+)\s+maximum resident set size',l).group(1) for l in lines if 'maximum resident set size' in l))
    memory.setdefault((x['engine'],x['lifetime']),[]).append(dict(**x['heapUsedBytes'],**x['approximateHeapDeltasBytes'],peakRssBytes=rss,collectedExecutorWrappers=x['collectedExecutorWrappers']))
rows=[]
for (engine,lifetime),rs in memory.items():
    rows.append(dict(engine=engine,lifetime=lifetime,repeats=len(rs),**{k:statistics.median(r[k] for r in rs) for k in rs[0]}))
save('memory-summary.csv',rows)
print('Summaries generated:',len(effects),'execution cases;',len(rows),'memory groups')
