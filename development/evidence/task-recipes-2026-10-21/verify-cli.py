"""Published CLI acceptance; fixture examples plus independent business edge cases."""
import copy, hashlib, json, pathlib, subprocess
ROOT=pathlib.Path(__file__).resolve().parents[3]
OUT=ROOT/'output/task-recipes-2026-10-21/cli';OUT.mkdir(parents=True,exist_ok=True)
IDS=['prepare-order','recipe-customer','recipe-sales-summary','recipe-incomplete-data','recipe-test-report','recipe-pipeline-guard']
ENGINES={
 'jvm':['java','-jar',str(ROOT/'output/rc-1.0.0-2026-10-21/published/branchline-cli-v1.0.0-rc.1-all.jar')],
 'node':[str(ROOT/'output/one-zero-platform-2026-10-21/cli/runtimes/node-v22.23.3-darwin-arm64/bin/node'),str(ROOT/'output/rc-1.0.0-2026-10-21/published-node-extracted/bin/bl.cjs')]
}
independent=json.loads((pathlib.Path(__file__).parent/'independent-cases.json').read_text())
results=[];hashes={}
for rid in IDS:
 fixture=json.loads((ROOT/f'playground/examples/{rid}.json').read_text())
 program=ROOT/f'docs/downloads/{rid}/program.bl'
 assert program.read_text().strip()=='\n'.join(fixture['program']).strip()
 hashes[rid]=hashlib.sha256(program.read_bytes()).hexdigest()
 cases=[{'name':'base','input':fixture['input'],'expectedOutput':fixture['expectedOutput']}]
 exercise=fixture['exercise'];inp=copy.deepcopy(fixture['input']);target=inp
 for key in exercise['patch']['path'][:-1]:target=target[key]
 target[exercise['patch']['path'][-1]]=exercise['patch']['value']
 cases.append(dict(exercise,name='exercise',input=inp))
 cases+=fixture.get('edgeCases',[])+independent[rid]
 for case in cases:
  ip=OUT/f'{rid}-{case["name"]}.json';ip.write_text(json.dumps(case['input']))
  for runtime,cmd in ENGINES.items():
   r=subprocess.run(cmd+[str(program),'--input',str(ip)],capture_output=True,text=True,timeout=30)
   try:actual=json.loads(r.stdout)
   except ValueError:actual=None
   error=case.get('expectedError')
   ok=(r.returncode!=0 and error in r.stderr and not r.stdout.strip()) if error else (r.returncode==0 and actual==case['expectedOutput'])
   results.append({'recipe':rid,'case':case['name'],'runtime':runtime,'ok':ok,'exit':r.returncode,'output':actual,'stderr':r.stderr})
report={'program_sha256':hashes,'cases':results}
(pathlib.Path(__file__).parent/'cli-results.json').write_text(json.dumps(report,indent=2)+'\n')
print(f'{sum(x["ok"] for x in results)}/{len(results)} CLI checks passed')
for x in results:
 if not x['ok']: print(x)
raise SystemExit(0 if all(x['ok'] for x in results) else 1)
