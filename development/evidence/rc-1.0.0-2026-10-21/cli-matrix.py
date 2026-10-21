#!/usr/bin/env python3
"""RC acceptance: prior frozen matrix with only exact RC version assertion changed."""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import tarfile
import tempfile
import time

ROOT = Path(__file__).resolve().parents[3]
DEFAULT_OUT = ROOT / 'output/one-zero-platform-2026-10-21/cli'
P = argparse.ArgumentParser()
P.add_argument('--jar', type=Path, required=True)
P.add_argument('--tarball', type=Path, required=True)
P.add_argument('--out', type=Path, default=DEFAULT_OUT)
P.add_argument('--config', type=Path, help='JSON array of {id,kind,executable}; default macOS installed runtimes')
A = P.parse_args()
A.out = A.out.resolve()
A.out.mkdir(parents=True, exist_ok=True)
def sha(p): return hashlib.sha256(Path(p).read_bytes()).hexdigest()
runtime_root = DEFAULT_OUT / 'runtimes'
config = json.loads(A.config.read_text()) if A.config else [
    {'id':'jvm-temurin-21.0.11','kind':'jvm','executable':'/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin/java'},
    {'id':'jvm-liberica-21.0.9','kind':'jvm','executable':'/Library/Java/JavaVirtualMachines/liberica-jdk-21.jdk/Contents/Home/bin/java'},
    {'id':'jvm-liberica-25.0.1','kind':'jvm','executable':'/Library/Java/JavaVirtualMachines/liberica-jdk-25.jdk/Contents/Home/bin/java'},
    *[{'id':f'node-{v}','kind':'node','executable':str(runtime_root/f'node-v{v}-darwin-arm64/bin/node')} for v in ['18.20.8','22.23.3','24.22.0']],
    {'id':'node-25.3.0','kind':'node','executable':shutil.which('node')},
]
work = Path(tempfile.mkdtemp(prefix='branchline platform acceptance '))
fixtures = work / 'independent fixtures'
fixtures.mkdir()
def write(name, text):
    p = fixtures/name
    p.write_text(text)
    return p
sources = {
 'hello': 'TRANSFORM Hello { OUTPUT { greeting: "Hello, " + input.name } }',
 'defaults': 'TRANSFORM Defaults { OUTPUT { name: input.name ?? "Guest", nickname: input.nickname, active: input.active ?? true, attempts: input.attempts ?? 3 } }',
 'filter': 'TRANSFORM Available {\n LET items = input.items ?? []\n OUTPUT { items: [{ sku: item.sku, total: item.qty * item.price } FOR EACH item IN items WHERE item.qty > 0] }\n}',
 'numeric': 'TRANSFORM Numeric { OUTPUT { fraction: 1.5, floor: [FLOOR(1.5), FLOOR(-1.5)], ceil: [CEIL(1.5), CEIL(-1.5)], ties: [ROUND(250,-2), ROUND(-250,-2), ROUND(350,-2), ROUND(-350,-2)] } }',
 'contract': 'TRANSFORM Main(input: { count: number }) -> { total: number } { OUTPUT { total: input.count + 1 } }',
 'syntax': 'TRANSFORM Broken { OUTPUT { value: } }',
 'runtime': 'TRANSFORM Fail { OUTPUT { value: ASSERT(false) } }',
}
scripts = {k:write(k+'.bl',v) for k,v in sources.items()}
inputs = {k:write(k+'.json',json.dumps(v)) for k,v in {
 'hello':{'name':'Grace'}, 'empty':{},
 'defaults':{'name':None,'nickname':None,'active':False,'attempts':0},
 'filter':{'items':[{'sku':'zero','qty':0,'price':100},{'sku':'two','qty':2,'price':7},{'sku':'one','qty':1,'price':3},{'sku':'negative','qty':-4,'price':8}]},
 'contract-valid':{'count':8},'contract-invalid':{'count':'bad'},
}.items()}
bad_json = write('invalid.json','{"truncated":')
def envelope(value, version=3):
    return {'version':version,'format':'branchline-vm','transform':'Fixture','script':'TRANSFORM Fixture { OUTPUT {} }',
      'bytecode':{'instructions':[{'op':'PUSH','value':{'t':'obj','obj':{'value':value}}},{'op':'RETURN_VALUE'}]}}
numeric_artifact = write('v3-bigint.blc',json.dumps(envelope({'t':'bi','s':'123456789012345678901234567890'})))
relabeled = write('invalid-v2-bi.blc',json.dumps(envelope({'t':'bi','s':'123'},2)))
future = write('future.blc',json.dumps({'version':999}))
cases=[]
def invoke(runtime,name,args,expected=None,status=0,check=None,stdin=None,diagnostic_stdout=False):
    command=[*runtime['command'],*map(str,args)]
    start=time.monotonic()
    env=os.environ.copy()
    if runtime['kind']=='node': env['PATH']=str(Path(runtime['executable']).parent)+os.pathsep+env.get('PATH','')
    try:
        p=subprocess.run(command,cwd=runtime['install'],env=env,input=stdin,text=True,capture_output=True,timeout=30)
        result={'runtime':runtime['id'],'case':name,'command':command,'cwd':str(runtime['install']),
          'exit':p.returncode,'stdout':p.stdout,'stderr':p.stderr,'expected_status':status,'expected_json':expected}
        assert p.returncode==status, f'exit {p.returncode} != {status}'
        if expected is not None: assert json.loads(p.stdout)==expected, f'JSON mismatch: {p.stdout!r}'
        if status and not diagnostic_stdout: assert not p.stdout.strip(), 'failed command emitted execution output'
        if check: check(p)
        result['passed']=True
    except Exception as e:
        result=locals().get('result',{'runtime':runtime['id'],'case':name,'command':command})
        result.update(passed=False,failure=str(e))
    result['seconds']=round(time.monotonic()-start,3)
    cases.append(result)
    (A.out/'results.json').write_text(json.dumps(cases,indent=2)+'\n')
    return result
def inspect_check(p):
    obj=json.loads(p.stdout)
    assert obj['success'] is True and len(obj['transforms'])==1 and obj['normalizedSource']
def inspect_error_check(p):
    obj=json.loads(p.stdout)
    assert obj['success'] is False and obj['transforms']==[] and obj['subsetCompatibility']=='UNKNOWN'
    assert any(d['code']=='parse_error' and d['category']=='syntax' for d in obj['diagnostics'])
def error_check(kind):
    def check(p):
        e=json.loads(p.stderr)['error']; assert e['kind']==kind and e['exitCode']==p.returncode
    return check
artifacts={}
runtime_records=[]
expected_numeric={'fraction':1.5,'floor':[1,-2],'ceil':[2,-1],'ties':[200,-200,400,-400]}
expected_filter={'items':[{'sku':'two','total':14},{'sku':'one','total':3}]}
for r in config:
    r=dict(r)
    install=work/(r['id']+' fresh install')
    install.mkdir()
    r['install']=install
    if r['kind']=='jvm':
        shutil.copy2(A.jar,install/'branchline candidate.jar')
        r['command']=[r['executable'],'-jar',str(install/'branchline candidate.jar')]
        ver=subprocess.run([r['executable'],'-version'],capture_output=True,text=True,check=True)
    else:
        with tarfile.open(A.tarball) as t: t.extractall(install,filter='data')
        launcher=install/'bin/bl.cjs'
        assert os.access(launcher,os.X_OK), 'tarball launcher must retain executable mode'
        r['command']=[str(launcher)]  # Exercise shipped shebang, selecting runtime through PATH.
        ver=subprocess.run([r['executable'],'-p','JSON.stringify({version:process.version,platform:process.platform,arch:process.arch,versions:process.versions})'],capture_output=True,text=True,check=True)
    runtime_records.append({'id':r['id'],'kind':r['kind'],'executable':r['executable'],'executable_sha256':sha(r['executable']),
      'version_stdout':ver.stdout,'version_stderr':ver.stderr,'install':str(install)})
    invoke(r,'version',['--version'],check=lambda p: (None if p.stdout.strip() == 'Branchline CLI v1.0.0-rc.1' else (_ for _ in ()).throw(AssertionError(p.stdout))))
    invoke(r,'help',['--help'],check=lambda p: (None if 'Usage:' in p.stdout else (_ for _ in ()).throw(AssertionError(p.stdout))))
    invoke(r,'intro-hello',[scripts['hello'],'--input',inputs['hello']],{'greeting':'Hello, Grace'})
    invoke(r,'stdin-hello',[scripts['hello'],'--input','-','--output-format','json-compact'],{'greeting':'Hello, Hopper'},stdin='{"name":"Hopper"}')
    invoke(r,'defaults-null-falsy',[scripts['defaults'],'--input',inputs['defaults']],{'name':'Guest','nickname':None,'active':False,'attempts':0})
    invoke(r,'defaults-missing',[scripts['defaults']],{'name':'Guest','nickname':None,'active':True,'attempts':3})
    invoke(r,'filter-project',[scripts['filter'],'--input',inputs['filter']],expected_filter)
    invoke(r,'filter-missing',[scripts['filter']],{'items':[]})
    invoke(r,'numeric-corrections',[scripts['numeric']],expected_numeric)
    invoke(r,'strict-contract-valid',[scripts['contract'],'--input',inputs['contract-valid'],'--contracts','strict'],{'total':9})
    invoke(r,'strict-contract-invalid',[scripts['contract'],'--input',inputs['contract-invalid'],'--contracts','strict','--error-format','json'],status=70,check=error_check('runtime'))
    invoke(r,'inspect',[ 'inspect',scripts['hello'],'--contracts','--contracts-json','--normalized'],check=inspect_check)
    invoke(r,'inspect-syntax-error',['inspect',scripts['syntax'],'--contracts','--contracts-json','--error-format','json'],status=70,check=inspect_error_check,diagnostic_stdout=True)
    invoke(r,'usage-invalid-mode',[scripts['hello'],'--contracts','invalid-mode','--error-format','json'],status=64,check=error_check('usage'))
    invoke(r,'input-invalid-json',[scripts['hello'],'--input',bad_json,'--error-format','json'],status=65,check=error_check('input'))
    invoke(r,'missing-file',['run',fixtures/'absent.bl','--error-format','json'],status=74,check=error_check('io'))
    invoke(r,'missing-compile-source',['compile',fixtures/'absent.bl','--error-format','json'],status=74,check=error_check('io'))
    invoke(r,'missing-artifact',['exec',fixtures/'absent.blc','--error-format','json'],status=74,check=error_check('io'))
    invoke(r,'missing-input',[scripts['hello'],'--input',fixtures/'absent.json','--error-format','json'],status=74,check=error_check('io'))
    blocked=install/'blocked parent'
    blocked.write_text('regular file prevents creation of a child output')
    invoke(r,'compile-output-io-error',['compile',scripts['hello'],'--output',blocked/'child.blc','--error-format','json'],status=74,check=error_check('io'))
    invoke(r,'runtime-failure',[scripts['runtime'],'--error-format','json'],status=70,check=error_check('runtime'))
    invoke(r,'future-version',['exec',future,'--error-format','json'],status=65,check=error_check('input'))
    invoke(r,'legacy-v1',['exec',ROOT/'cli/src/jvmTest/resources/fixtures/compatibility/legacy-hello.blc','--input',inputs['hello']],{'greeting':'Hello, Grace'})
    invoke(r,'legacy-v2',['exec',ROOT/'cli/src/jvmTest/resources/fixtures/compatibility/legacy-v2.blc'],{'greeting':'hello','ordinary':1.5,'integer':42})
    invoke(r,'legacy-relabel-rejected',['exec',relabeled,'--error-format','json'],status=65,check=error_check('input'))
    if r['kind']=='jvm': invoke(r,'v3-bigint-exact',['exec',numeric_artifact],{'value':'123456789012345678901234567890'})
    else: invoke(r,'v3-bigint-outside-js-domain',['exec',numeric_artifact,'--error-format','json'],status=65,check=error_check('input'))
    artifacts[r['id']]={}
    for key,want,inp in [('filter',expected_filter,inputs['filter']),('numeric',expected_numeric,inputs['empty'])]:
        dest=install/(key+' saved.blc')
        invoke(r,'compile-'+key,['compile',scripts[key],'--output',dest])
        artifacts[r['id']][key]=dest
        invoke(r,'exec-own-'+key,['exec',dest,'--input',inp,'--contracts','strict'],want)
    contract_artifact=install/'contract saved.blc'
    invoke(r,'compile-contract',['compile',scripts['contract'],'--output',contract_artifact])
    invoke(r,'exec-contract-valid',['exec',contract_artifact,'--input',inputs['contract-valid'],'--contracts','strict'],{'total':9})
    invoke(r,'exec-contract-invalid',['exec',contract_artifact,'--input',inputs['contract-invalid'],'--contracts','strict','--error-format','json'],status=70,check=error_check('runtime'))
    if r['kind']=='node':
        xml=write(r['id']+'-xml.xml','<root><name>Ada</name></root>')
        xs=write(r['id']+'-xml.bl','TRANSFORM Xml { OUTPUT { name: input.root.name["$"] } }')
        invoke(r,'bundled-xml-dependency',[xs,'--input',xml,'--input-format','xml'],{'name':'Ada'})
    r['artifacts']=artifacts[r['id']]
    print(r['id'], 'local cases finished',flush=True)
    r['_ready']=True
    # Retain runtime details for cross-artifact phase below.
    r['_expected']=True
    if 'ready' not in globals(): ready=[]
    ready.append(r)
for r in ready:
    producer=next(p for p in ready if p['kind']!=r['kind'])
    for key,want,inp in [('filter',expected_filter,inputs['filter']),('numeric',expected_numeric,inputs['empty'])]:
        invoke(r,'cross-'+producer['id']+'-'+key,['exec',producer['artifacts'][key],'--input',inp,'--contracts','strict'],want)
old=ROOT/'output/one-zero-surface-2026-10-18/reader-v2.jar'
if old.exists():
    oldrt={'id':'retained-v2-reader','kind':'jvm','executable':ready[0]['executable'],'install':work,'command':[ready[0]['executable'],'-jar',str(old)]}
    for kind in ['jvm','node']:
        producer=next(r for r in ready if r['kind']==kind)
        invoke(oldrt,'new-'+kind+'-artifact-rejected',['exec',producer['artifacts']['numeric']],status=65,
          check=lambda p: (None if 'Unsupported compiled artifact version 3' in p.stderr else (_ for _ in ()).throw(AssertionError(p.stderr))))
summary={'timestamp_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'host':{'platform':platform.platform(),'uname':list(platform.uname()),'mac_ver':platform.mac_ver()},
 'work_directory':str(work),'jar':{'source':str(A.jar.resolve()),'sha256':sha(A.jar)},'tarball':{'source':str(A.tarball.resolve()),'sha256':sha(A.tarball)},
 'harness_sha256':sha(__file__),'runtimes':runtime_records,'cases':len(cases),'passed':sum(c['passed'] for c in cases),
 'reference_hashes':{str(p.relative_to(ROOT)):sha(p) for p in [ROOT/'cli/src/jvmTest/resources/fixtures/compatibility/legacy-hello.blc',ROOT/'cli/src/jvmTest/resources/fixtures/compatibility/legacy-v2.blc',old]},
 'failures':[{k:c[k] for k in ['runtime','case','failure']} for c in cases if not c['passed']],
 'generated_artifacts':{r:{k:{'path':str(v),'sha256':sha(v),'version':json.loads(v.read_text()).get('version',1)} for k,v in a.items()} for r,a in artifacts.items()}}
(A.out/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')
print(json.dumps({k:summary[k] for k in ['cases','passed','failures']},indent=2))
raise SystemExit(bool(summary['failures']))
