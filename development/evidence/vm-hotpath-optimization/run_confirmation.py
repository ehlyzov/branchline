#!/usr/bin/env python3
"""Longer confirmation for unchanged native JMH large workloads; run variants serially."""
import argparse, json, subprocess
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--java-home',type=Path,required=True)
p.add_argument('--jar',type=Path,required=True)
p.add_argument('--engine',choices=['vm','interpreter'],required=True)
p.add_argument('--output',type=Path,required=True)
a=p.parse_args();a.output.parent.mkdir(parents=True,exist_ok=True)
klass='io.github.ehlyzov.branchline.'+('vm.VMTransformBenchmark' if a.engine=='vm' else 'benchmarks.InterpreterTransformBenchmark')
cmd=[str(a.java_home/'bin/java'),'-jar',str(a.jar),klass+r'\.(pathExpressions|arrayComprehensions|typicalTransform)$',
 '-p','dataset=large','-bm','avgt','-tu','us','-t','1','-f','2','-wi','3','-w','1s','-i','5','-r','1s',
 '-jvmArgs','-Xms256m -Xmx768m','-prof','gc','-foe','true','-rf','json','-rff',str(a.output.resolve())]
if a.engine=='interpreter':cmd+=['-p','collectionType=hash']
a.output.with_suffix('.command.json').write_text(json.dumps(cmd,indent=2)+'\n')
with a.output.with_suffix('.log').open('w') as log:subprocess.run(cmd,stdout=log,stderr=subprocess.STDOUT,check=True)
assert len(json.loads(a.output.read_text()))==3
print(f'{a.engine} confirmation complete: {a.output}',flush=True)
