"""Independent acceptance of first-success files with published CLI packages."""
import argparse, hashlib, json, pathlib, subprocess
p=argparse.ArgumentParser()
p.add_argument('--files',type=pathlib.Path,required=True)
p.add_argument('--jar',type=pathlib.Path,required=True)
p.add_argument('--node',type=pathlib.Path,required=True)
p.add_argument('--node-cli',type=pathlib.Path,required=True)
p.add_argument('--out',type=pathlib.Path,required=True)
a=p.parse_args(); a.out.mkdir(parents=True,exist_ok=True)
program=(a.files/'program.bl').resolve(); original=json.loads((a.files/'input.json').read_text())
# Oracles are fixed business expectations; no engine is used as another's oracle.
cases=[('original',original,{'order_id':'A-104','city':'London','skus':['BOOK']}),
 ('edited',{'id':'A-104','shipping':{'city':'London'},'items':[{'sku':'BOOK','qty':2},{'sku':'PEN','qty':1}]},{'order_id':'A-104','city':'London','skus':['BOOK','PEN']}),
 ('empty',{'id':'A-104','shipping':{},'items':[]},{'order_id':'A-104','city':'Not provided','skus':[]}),
 ('null',{'id':'A-104','shipping':{'city':None},'items':[{'sku':'PEN','qty':0}]},{'order_id':'A-104','city':'Not provided','skus':[]})]
engines={'jvm':['java','-jar',str(a.jar.resolve())],'node':[str(a.node.resolve()),str(a.node_cli.resolve())]}
results=[]
for runtime,command in engines.items():
 for label,inp,expected in cases:
  path=(a.out/f'{label}.json').resolve();path.write_text(json.dumps(inp))
  r=subprocess.run(command+[str(program),'--input',str(path)],capture_output=True,text=True,timeout=30)
  try: actual=json.loads(r.stdout)
  except ValueError: actual=None
  results.append({'runtime':runtime,'case':label,'exit':r.returncode,'actual':actual,'expected':expected,'ok':r.returncode==0 and actual==expected,'stderr':r.stderr})
report={'program_sha256':hashlib.sha256(program.read_bytes()).hexdigest(),'input_sha256':hashlib.sha256((a.files/'input.json').read_bytes()).hexdigest(),'cases':results}
(a.out/'cli-results.json').write_text(json.dumps(report,indent=2))
print(f"{sum(x['ok'] for x in results)}/{len(results)} independent CLI checks passed")
raise SystemExit(0 if all(x['ok'] for x in results) else 1)
