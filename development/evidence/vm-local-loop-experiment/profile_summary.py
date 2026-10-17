"""Summarize JFR worker samples. Instrumented scores are not timing evidence."""
from pathlib import Path
from collections import Counter
import json, subprocess, gzip
root=Path(__file__).resolve().parent
result={}
for folder in sorted(root.glob('jfr-*')):
    recordings=list(folder.rglob('*.jfr'))
    if len(recordings)!=1: raise ValueError((folder,len(recordings)))
    name=folder.name.removeprefix('jfr-')
    compressed=root/('samples-'+name+'.json.gz')
    if compressed.exists():
        with gzip.open(compressed,'rt') as f: data=json.load(f)
    else:
        raw=subprocess.check_output(['jfr','print','--json','--events','jdk.ExecutionSample,jdk.NativeMethodSample',str(recordings[0])])
        data=json.loads(raw)
        with gzip.open(compressed,'wb') as f:f.write(raw)
    events=data['recording']['events'];leaf=Counter();inclusive=Counter();lines=Counter();n=0
    for ev in events:
        values=ev['values'];thread=values.get('sampledThread') or {};frames=(values.get('stackTrace') or {}).get('frames') or []
        if 'jmh-worker' not in thread.get('javaName','') or not frames:continue
        n+=1
        names=[f['method']['type']['name'].replace('/','.')+'.'+f['method']['name'] for f in frames]
        leaf[names[0]]+=1;inclusive.update(set(names));lines[(names[0],frames[0]['lineNumber'])]+=1
    result[name]=dict(totalEvents=len(events),workerSamples=n,
        leaf=[dict(method=k,samples=v,percent=100*v/n) for k,v in leaf.most_common(30)],
        inclusive=[dict(method=k,samples=v,percent=100*v/n) for k,v in inclusive.most_common(30)],
        leafLines=[dict(method=k[0],line=k[1],samples=v,percent=100*v/n) for k,v in lines.most_common(25)])
(root/'profile-summary.json').write_text(json.dumps(result,indent=2)+'\n')
for name,x in result.items():
    print(name,x['workerSamples'],'worker samples')
    for r in x['leaf'][:10]:print(f" {r['percent']:.1f}% {r['method']}")
