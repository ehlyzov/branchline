import subprocess,json,pathlib,platform,time,sys
root=pathlib.Path.cwd(); out=root/'development/evidence/vm-json-pipeline'; tmp=pathlib.Path('/tmp/branchline-vm-json-pipeline'); jar=str(root/'vm-benchmarks/build/libs/vm-benchmarks-v0.11.0-SNAPSHOT-jmh.jar')
commands=[]
def run(args,name):
    print('START',name,flush=True); start=time.time()
    with (out/(name+'.log')).open('w') as f: p=subprocess.run(args,stdout=f,stderr=subprocess.STDOUT)
    commands.append(dict(name=name,argv=args,exit=p.returncode,seconds=time.time()-start)); (out/'measurement-commands.json').write_text(json.dumps(commands,indent=2))
    if p.returncode: raise RuntimeError(name+' failed')
    print('DONE',name,round(time.time()-start,1),flush=True)
jvm=['-Xms512m','-Xmx2g','-XX:+UseSerialGC']; probe=['java',*jvm,'-cp',jar,'io.github.ehlyzov.branchline.vm.SelectiveJsonPipelineProbe']
for shape,size in [('strings',1),('strings',16),('strings',64),('records',16)]:
    path=str(tmp/f'{shape}-{size}.json'); run([*probe,'generate',path,str(size),shape],f'generate-{shape}-{size}')
    for pct in [1,10,100] if shape=='strings' else [1,100]:
        for mode in ['eager','selective']:run([*probe,'validate',path,str(size),shape,str(pct),mode],f'validate-{shape}-{size}-{pct}-{mode}')
bench=['java','-jar',jar,'io.github.ehlyzov.branchline.vm.SelectiveJsonPipelineBenchmark.fullPipeline','-p','engineMode=eager,selective','-t','1','-f','2','-wi','3','-i','5','-w','500ms','-r','500ms','-jvmArgs',' '.join(jvm),'-prof','gc','-foe','true','-rf','json']
for name,size,pct,shape,access in [('strings','1,16,64','1,10,100','strings','static'),('records','16','1,100','records','static'),('fallback','16','1','strings','dynamic')]:
    run([*bench,'-p','inputMiB='+size,'-p','selectedPercent='+pct,'-p','shape='+shape,'-p','accessPattern='+access,'-rff',str(out/f'pipeline-{name}-jmh.json')],f'pipeline-{name}-jmh')
for rep in range(1,4):
    for shape,size,pct,access in [('strings',16,1,'static'),('strings',64,1,'static'),('strings',16,100,'static'),('records',16,1,'static'),('strings',16,1,'dynamic')]:
        for mode in (['eager','selective'] if rep%2 else ['selective','eager']):
            name=f'memory-{shape}-{size}-{pct}-{access}-{mode}-{rep}'
            args=['/usr/bin/time','-l',*probe,'memory',str(tmp/f'{shape}-{size}.json'),str(size),shape,str(pct),mode,access]
            run(args,name)
print('ALL MEASUREMENTS COMPLETE',flush=True)
