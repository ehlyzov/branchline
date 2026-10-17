#!/usr/bin/env python3
"""Run the same external JMH harness against a supplied Branchline benchmark jar."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--jar', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--java-home', type=Path)
parser.add_argument('--gradle-cache', type=Path, default=Path.home() / '.gradle/caches/modules-2/files-2.1')
args = parser.parse_args()
jar = args.jar.resolve()
out = args.output.resolve()
out.parent.mkdir(parents=True, exist_ok=True)

def binary(name):
    path = args.java_home / 'bin' / name if args.java_home else shutil.which(name)
    if not path:
        parser.error(f'{name} not found; supply --java-home')
    return str(path)

generators = []
for artifact in ('jmh-generator-bytecode', 'jmh-generator-reflection', 'jmh-generator-asm'):
    matches = sorted((args.gradle_cache / 'org.openjdk.jmh' / artifact / '1.36').glob('*/*.jar'))
    if len(matches) != 1:
        parser.error(f'Need cached JMH1.36 {artifact}; found {matches}')
    generators.append(str(matches[0]))

with tempfile.TemporaryDirectory(prefix='branchline-vm-jmh-') as temp:
    root = Path(temp)
    classes, generated = root / 'classes', root / 'generated'
    classes.mkdir()
    generated.mkdir()
    source = Path(__file__).with_name('VMPerformanceBench.java')
    subprocess.run([binary('javac'), '-proc:none', '-cp', str(jar), '-d', str(classes), str(source)], check=True)
    subprocess.run([binary('java'), '-cp', os.pathsep.join([str(classes), str(jar), *generators]),
        'org.openjdk.jmh.generators.bytecode.JmhBytecodeGenerator', str(classes), str(generated), str(classes), 'reflection'], check=True)
    subprocess.run([binary('javac'), '-proc:none', '-cp', os.pathsep.join([str(classes), str(jar)]),
        '-d', str(classes), *map(str, generated.rglob('*.java'))], check=True)
    with out.with_suffix('.log').open('w') as log:
        subprocess.run([binary('java'), '-cp', os.pathsep.join([str(classes), str(jar)]),
            'org.openjdk.jmh.Main', 'audit.VMPerformanceBench.execute', '-prof', 'gc', '-foe', 'true',
            '-rf', 'json', '-rff', str(out)], stdout=log, stderr=subprocess.STDOUT, check=True)
    results = json.loads(out.read_text())
    if len(results) != 10:
        raise RuntimeError(f'Expected10 scenarios; got{len(results)}')
print(out)
