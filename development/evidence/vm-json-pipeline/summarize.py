import json,pathlib,csv,statistics,re
out=pathlib.Path('development/evidence/vm-json-pipeline'); rows=[]
for name in ['strings','records','fallback']:
 p=out/f'pipeline-{name}-jmh.json'
 if not p.exists() or not p.stat().st_size:continue
 data=json.loads(p.read_text()); groups={}
 for x in data:
  a=x['params']; k=(a['shape'],int(a['inputMiB']),int(a['selectedPercent']),a['accessPattern']);groups.setdefault(k,{})[a['engineMode']]=x
 for key,g in groups.items():
  e,s=g['eager'],g['selective']; et=e['primaryMetric']['score'];st=s['primaryMetric']['score']; ea=next(v['score'] for k,v in e['secondaryMetrics'].items() if k.endswith('gc.alloc.rate.norm'));sa=next(v['score'] for k,v in s['secondaryMetrics'].items() if k.endswith('gc.alloc.rate.norm'))
  rows.append(dict(shape=key[0],MiB=key[1],selectedPercent=key[2],access=key[3],eagerMs=et,selectiveMs=st,eagerError=e['primaryMetric']['scoreError'],selectiveError=s['primaryMetric']['scoreError'],speedup=et/st,timeReductionPercent=100*(1-st/et),eagerAllocatedMiB=ea/2**20,selectiveAllocatedMiB=sa/2**20,allocationReductionPercent=100*(1-sa/ea)))
if rows:
 with (out/'timing-summary.csv').open('w') as f:
  w=csv.DictWriter(f,fieldnames=list(rows[0]));w.writeheader();w.writerows(rows)
 print('TIMING');print(json.dumps(rows,indent=2))
mem={}
for p in out.glob('memory-*.log'):
 lines=p.read_text().splitlines(); x=json.loads(next(l for l in lines if l.startswith('{')));rss=int(next(re.search(r'\s*(\d+)\s+maximum resident set size',l).group(1) for l in lines if 'maximum resident set size' in l));key=(x['shape'],x['inputMiB'],x['selectedPercent'],x['accessPattern'],x['engineMode']); vals={**x['heapUsedBytes'],**x['heapDeltasBytes'],'peakRssBytes':rss}; mem.setdefault(key,[]).append(vals)
mr=[]
for key,rs in mem.items():mr.append(dict(shape=key[0],MiB=key[1],selectedPercent=key[2],access=key[3],engine=key[4],repeats=len(rs),**{k:statistics.median(r[k] for r in rs) for k in rs[0]}))
if mr:
 with (out/'memory-summary.csv').open('w') as f:
  w=csv.DictWriter(f,fieldnames=list(mr[0]));w.writeheader();w.writerows(mr)
 print('MEMORY');print(json.dumps(mr,indent=2))
