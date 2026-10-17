"""Parent-owned serial measurements. Run only after acceptance and source freeze."""
import pathlib, subprocess, json, time
root=pathlib.Path.cwd(); out=root/'development/evidence/vm-prepared-block-experiment'
jar=str(root/'vm-benchmarks/build/libs/vm-benchmarks-v0.11.0-SNAPSHOT-jmh.jar')
before='/tmp/branchline-vm-prepared/before.jar'; commands=[]
def run(args,name):
    print('START',name,flush=True); start=time.time()
    with (out/(name+'.log')).open('w') as f:
        p=subprocess.run(args,stdout=f,stderr=subprocess.STDOUT)
    commands.append(dict(name=name,argv=args,exit=p.returncode,seconds=time.time()-start))
    (out/'commands.json').write_text(json.dumps(commands,indent=2)+'\n')
    print('DONE',name,round(time.time()-start,1),'exit',p.returncode,flush=True)
    if p.returncode: raise RuntimeError(name)
common=['-t','1','-f','2','-jvmArgs','-Xms256m -Xmx768m -XX:+UseG1GC','-prof','gc','-foe','true','-rf','json']
long=common+['-wi','3','-i','5','-w','1s','-r','1s']
short=common+['-wi','2','-i','4','-w','500ms','-r','500ms']
execution='.*VMPreparedBlockBenchmark.execute'
run(['java','-cp',before+':'+jar,'org.openjdk.jmh.Main',execution,*long,
     '-p','engine=baseline','-p','dataset=large','-rff',str(out/'old-baseline.json')],'old-baseline')
run(['java','-jar',jar,execution,*long,'-rff',str(out/'forward.json')],'forward')
run(['java','-jar',jar,execution,*long,'-p','dataset=large',
     '-p','engine=preparedOptimized,preparedControl,baseline,interpreter',
     '-rff',str(out/'reverse.json')],'reverse')
run(['java','-jar',jar,'.*VMPreparedBlockPreparationBenchmark.prepare',*short,
     '-p','dataset=large','-p','workload=reduction,holdoutProjection',
     '-rff',str(out/'preparation.json')],'preparation')
run(['java','-jar',jar,execution,*short,'-p','dataset=large','-p','workload=fallbackClosure',
     '-rff',str(out/'fallback.json')],'fallback')
engines=['interpreter','baseline','preparedControl','preparedOptimized']
for rep in range(1,4):
    for lifetime in ['reuse','many']:
        for engine in (engines if rep%2 else engines[::-1]):
            run(['/usr/bin/time','-l','java','-Xms32m','-Xmx768m','-XX:+UseG1GC','-cp',jar,
                 'io.github.ehlyzov.branchline.vm.VMPreparedBlockProbe','memory',engine,
                 'reduction','large',lifetime,'1000'],f'memory-{engine}-{lifetime}-{rep}')
print('ALL MEASUREMENTS COMPLETE',flush=True)
