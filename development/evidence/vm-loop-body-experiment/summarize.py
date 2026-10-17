from pathlib import Path
import json,csv,re,statistics
out=Path(__file__).resolve().parent
engines=['interpreter','baseline','preparedOptimized','loopBody']
def read(name):
 p=out/name
 return json.loads(p.read_text()) if p.exists() and p.stat().st_size else []
def save(name,rows):
 if not rows:return
 with (out/name).open('w') as f:
  w=csv.DictWriter(f,fieldnames=list(rows[0]));w.writeheader();w.writerows(rows)
def metric(x):
 return dict(us=x['primaryMetric']['score'],error99_9=x['primaryMetric']['scoreError'],allocatedBytes=next(v['score'] for k,v in x['secondaryMetrics'].items() if k.endswith('gc.alloc.rate.norm')))
effects=[]
for name in ['old-baseline','forward','reverse','preparation','fallback']:
 data=read(name+'.json');save(name+'-summary.csv',[dict(**x['params'],**metric(x)) for x in data])
 if name not in ['forward','reverse']:continue
 groups={}
 for x in data:groups.setdefault((x['params']['dataset'],x['params']['workload']),{})[x['params']['engine']]=metric(x)
 for (size,workload),g in groups.items():
  i,b,p,n=[g[e] for e in engines]
  effects.append(dict(run=name,dataset=size,workload=workload,interpreterUs=i['us'],baselineUs=b['us'],priorPreparedUs=p['us'],loopBodyUs=n['us'],newVsBaseline=n['us']/b['us'],newVsInterpreter=n['us']/i['us'],newVsPriorPrepared=n['us']/p['us'],allocationDeltaVsBaseline=n['allocatedBytes']-b['allocatedBytes'],allocationDeltaVsInterpreter=n['allocatedBytes']-i['allocatedBytes'],baselineConservativeEndpointRatio=(n['us']+n['error99_9'])/(b['us']-b['error99_9']) if b['us']>b['error99_9'] else None,interpreterConservativeEndpointRatio=(n['us']+n['error99_9'])/(i['us']-i['error99_9']) if i['us']>i['error99_9'] else None))
save('effects.csv',effects)
memory={};raw=[]
for p in sorted(out.glob('memory-*.log')):
 lines=p.read_text().splitlines()
 if not any('maximum resident set size' in l for l in lines):continue
 x=json.loads(next(l for l in lines if l.startswith('{')));s=x['stages'];a=s['fixtureOnlyAfterPriming'];b=s['fixturePreparedUnexecutedExecutorsAndWeakReferences'];c=s['fixtureExecutedExecutorsWeakReferencesAndLastOutput'];d=s['fixtureWeakReferencesAndLastOutput'];f=s['fixtureOnlyAfterRelease']
 def meta(stage,pool='Metaspace',kind='used'):return stage['nonHeapPoolsBytes'].get(pool,{}).get(kind,0)
 row=dict(**x['approximateHeapDeltasBytes'],peakRssBytes=int(next(re.search(r'\s*(\d+)\s+maximum resident set size',l).group(1) for l in lines if 'maximum resident set size' in l)),metaspacePreparedDelta=meta(b)-meta(a),metaspaceExecutedDelta=meta(c)-meta(a),metaspaceAfterReleaseDelta=meta(f)-meta(a),metaspaceCommittedPreparedDelta=meta(b,kind='committed')-meta(a,kind='committed'),classSpacePreparedDelta=meta(b,'Compressed Class Space')-meta(a,'Compressed Class Space'),generatedClasses=x['preparedGeneratedClassCountSum'],generatedClassfileBytes=x['preparedGeneratedClassfileBytesSum'],classesUnloadedAfterExecutorRelease=d['unloadedClasses']-c['unloadedClasses'],collectedWrappers=x['collectedExecutorWrappers'])
 memory.setdefault((x['engine'],x['lifetime']),[]).append(row);raw.append(dict(file=p.name,engine=x['engine'],lifetime=x['lifetime'],**row))
save('memory-raw.csv',raw)
save('memory-summary.csv',[dict(engine=e,lifetime=l,repeats=len(rs),**{k:statistics.median(r[k] for r in rs) for k in rs[0]}) for (e,l),rs in memory.items()])
print('Summaries:',len(effects),'execution cases,',len(raw),'memory processes')
