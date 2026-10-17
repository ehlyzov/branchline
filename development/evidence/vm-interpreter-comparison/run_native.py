#!/usr/bin/env python3
"""Run existing repository JMH VM/interpreter pairs, unchanged, sequentially."""
import argparse,subprocess,json
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--java-home',type=Path,required=True)
p.add_argument('--output-dir',type=Path,required=True)
a=p.parse_args();a.output_dir.mkdir(parents=True,exist_ok=True)
for engine,module,klass in [('vm','vm-benchmarks','io.github.ehlyzov.branchline.vm.VMTransformBenchmark'),('interpreter','interpreter-benchmarks','io.github.ehlyzov.branchline.benchmarks.InterpreterTransformBenchmark')]:
    jar=Path(module)/'build/libs'/f'{module}-v0.11.0-SNAPSHOT-jmh.jar'
    output=(a.output_dir/f'native-{engine}.json').resolve()
    command=[str(a.java_home/'bin/java'),'-jar',str(jar),klass+r'\.(pathExpressions|arrayComprehensions|typicalTransform)$',
        '-bm','avgt','-tu','us','-t','1','-f','2','-wi','3','-w','300ms','-i','4','-r','300ms',
        '-jvmArgs','-Xms256m -Xmx768m','-p','dataset=small,medium,large','-prof','gc','-foe','true','-rf','json','-rff',str(output)]
    if engine=='interpreter':command+=['-p','collectionType=hash']
    output.with_suffix('.command.json').write_text(json.dumps(command,indent=2)+'\n')
    with output.with_suffix('.log').open('w') as log:
        subprocess.run(command,stdout=log,stderr=subprocess.STDOUT,check=True)
    records=json.loads(output.read_text());assert len(records)==9,len(records)
    print(f'{engine}:9 existing benchmark states complete',flush=True)
