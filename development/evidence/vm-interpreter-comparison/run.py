#!/usr/bin/env python3
"""Compile, validate and run the paired JMH harness against a supplied current jar."""
import argparse,os,subprocess,tempfile,json
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--jar',type=Path,required=True)
p.add_argument('--java-home',type=Path,required=True)
p.add_argument('--output',type=Path,required=True)
p.add_argument('--validate-only',action='store_true')
a=p.parse_args();jar=a.jar.resolve();out=a.output.resolve();out.parent.mkdir(parents=True,exist_ok=True)
java=str(a.java_home/'bin/java');javac=str(a.java_home/'bin/javac')
cache=Path.home()/'.gradle/caches/modules-2/files-2.1/org.openjdk.jmh'
gens=[]
for artifact in ('jmh-generator-bytecode','jmh-generator-reflection','jmh-generator-asm'):
    matches=sorted((cache/artifact/'1.36').glob('*/*.jar'))
    if len(matches)!=1:raise RuntimeError(f'Expected cached JMH1.36 {artifact}: {matches}')
    gens.append(str(matches[0]))
with tempfile.TemporaryDirectory(prefix='branchline-engine-comparison-') as tmp:
    root=Path(tmp);classes=root/'classes';generated=root/'generated';classes.mkdir();generated.mkdir()
    subprocess.run([javac,'-proc:none','-cp',str(jar),'-d',str(classes),str(Path(__file__).with_name('EngineComparisonBench.java'))],check=True)
    cp=os.pathsep.join([str(classes),str(jar)])
    result=subprocess.run([java,'-cp',cp,'audit.EngineComparisonBench'],capture_output=True,text=True)
    if result.returncode:
        print(result.stdout, result.stderr, flush=True)
        result.check_returncode()
    out.with_suffix('.validation.txt').write_text(result.stdout);print(result.stdout,flush=True)
    if not a.validate_only:
        subprocess.run([java,'-cp',os.pathsep.join([cp,*gens]),'org.openjdk.jmh.generators.bytecode.JmhBytecodeGenerator',str(classes),str(generated),str(classes),'reflection'],check=True)
        subprocess.run([javac,'-proc:none','-cp',cp,'-d',str(classes),*map(str,generated.rglob('*.java'))],check=True)
        with out.with_suffix('.log').open('w') as log:
            subprocess.run([java,'-cp',cp,'org.openjdk.jmh.Main','audit.EngineComparisonBench.execute','-prof','gc','-foe','true','-rf','json','-rff',str(out)],stdout=log,stderr=subprocess.STDOUT,check=True)
        results=json.loads(out.read_text());assert len(results)==26,len(results)
        print(out)
