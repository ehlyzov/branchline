import pathlib,subprocess,json,time
root=pathlib.Path.cwd();out=root/'development/evidence/vm-slots-fusion-experiment';tmp=pathlib.Path('/tmp/branchline-vm-factorial');jar=str(root/'vm-benchmarks/build/libs/vm-benchmarks-v0.11.0-SNAPSHOT-jmh.jar');commands=[]
def run(args,name):
 print('START',name,flush=True);start=time.time()
 with (out/(name+'.log')).open('w') as f:p=subprocess.run(args,stdout=f,stderr=subprocess.STDOUT)
 commands.append(dict(name=name,argv=args,exit=p.returncode,seconds=time.time()-start));(out/'commands.json').write_text(json.dumps(commands,indent=2));print('DONE',name,round(time.time()-start,1),'exit',p.returncode,flush=True)
 if p.returncode:raise RuntimeError(name)
knobs=['-t','1','-f','2','-wi','2','-i','4','-w','500ms','-r','500ms','-jvmArgs','-Xms256m -Xmx768m -XX:+UseG1GC','-prof','gc','-foe','true','-rf','json']
run(['java','-cp',str(tmp/'before.jar')+':'+jar,'org.openjdk.jmh.Main','.*VMFactorialBenchmark.execute',*knobs,'-p','engine=baseline','-p','dataset=large','-p','workload=paths,comprehension,reduction','-rff',str(out/'old-baseline.json')],'old-baseline')
run(['java','-jar',jar,'.*VMFactorialBenchmark.execute',*knobs,'-rff',str(out/'factorial.json')],'factorial')
run(['java','-jar',jar,'.*VMFactorialPreparationBenchmark.prepare',*knobs,'-p','dataset=large','-p','workload=reduction,holdoutProjection','-rff',str(out/'preparation.json')],'preparation')
run(['java','-jar',jar,'.*VMFactorialBenchmark.execute',*knobs,'-p','dataset=large','-p','workload=fallbackClosure','-rff',str(out/'fallback.json')],'fallback')
for rep in range(1,4):
 for lifetime in ['reuse','many']:
  for engine in (['interpreter','baseline','slots','fusion','both'] if rep%2 else ['both','fusion','slots','baseline','interpreter']):
   run(['/usr/bin/time','-l','java','-Xms32m','-Xmx768m','-XX:+UseG1GC','-cp',jar,'io.github.ehlyzov.branchline.vm.VMFactorialProbe','memory',engine,'reduction','large',lifetime,'1000'],f'memory-{engine}-{lifetime}-{rep}')
print('ALL MEASUREMENTS COMPLETE',flush=True)
