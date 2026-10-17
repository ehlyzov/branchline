import pathlib,json,csv,re,statistics
out=pathlib.Path(__file__).resolve().parent
def save(name,rows):
 if not rows:return
 with (out/name).open('w') as f:w=csv.DictWriter(f,fieldnames=list(rows[0]));w.writeheader();w.writerows(rows)
def read(name):
 p=out/name
 return json.loads(p.read_text()) if p.exists() and p.stat().st_size else []
def metric(x):
 return dict(us=x['primaryMetric']['score'],error99_9=x['primaryMetric']['scoreError'],allocatedBytes=next(v['score'] for k,v in x['secondaryMetrics'].items() if k.endswith('gc.alloc.rate.norm')))
for name in ['old-baseline','factorial','preparation','fallback','confirmation']:
 rows=[]
 for x in read(name+'.json'):rows.append(dict(**x['params'],**metric(x)))
 save(name+'-summary.csv',rows)
g={}
for x in read('factorial.json'):g.setdefault((x['params']['dataset'],x['params']['workload']),{})[x['params']['engine']]=metric(x)
rows=[]
for (size,case),v in g.items():
 b,s,f,a,i=[v[k] for k in ['baseline','slots','fusion','both','interpreter']]
 rows.append(dict(dataset=size,workload=case,interpreterUs=i['us'],baselineUs=b['us'],slotsUs=s['us'],fusionUs=f['us'],bothUs=a['us'],slotsVsBaseline=s['us']/b['us'],fusionVsBaseline=f['us']/b['us'],bothVsBaseline=a['us']/b['us'],bothVsInterpreter=a['us']/i['us'],multiplicativeInteraction=a['us']*b['us']/(s['us']*f['us'])))
save('effects.csv',rows)
mem={}
for p in sorted(out.glob('memory-*.log')):
 lines=p.read_text().splitlines()
 if not any('maximum resident set size' in l for l in lines):continue
 x=json.loads(next(l for l in lines if l.startswith('{')));rss=int(next(re.search(r'\s*(\d+)\s+maximum resident set size',l).group(1) for l in lines if 'maximum resident set size' in l));key=(x['engine'],x['lifetime']);mem.setdefault(key,[]).append(dict(**x['heapUsedBytes'],**x['approximateHeapDeltasBytes'],peakRssBytes=rss,collectedExecutorWrappers=x['collectedExecutorWrappers']))
rows=[]
for (engine,lifetime),rs in mem.items():rows.append(dict(engine=engine,lifetime=lifetime,repeats=len(rs),**{k:statistics.median(r[k] for r in rs) for k in rs[0]}))
save('memory-summary.csv',rows)
print('Summaries generated; timing states',len(read('factorial.json')),'memory groups',len(rows))
