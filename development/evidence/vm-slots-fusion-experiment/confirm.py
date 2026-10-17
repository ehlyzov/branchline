"""Longer confirmation, run only after measure.py exits. No source changes."""
import pathlib, subprocess, json, time
root = pathlib.Path.cwd()
out = root / 'development/evidence/vm-slots-fusion-experiment'
jar = root / 'vm-benchmarks/build/libs/vm-benchmarks-v0.11.0-SNAPSHOT-jmh.jar'
args = ['java', '-jar', str(jar), '.*VMFactorialBenchmark.execute',
        '-t', '1', '-f', '2', '-wi', '3', '-i', '5', '-w', '1s', '-r', '1s',
        '-jvmArgs', '-Xms256m -Xmx768m -XX:+UseG1GC', '-prof', 'gc', '-foe', 'true',
        '-p', 'dataset=large', '-p', 'engine=both,fusion,slots,baseline,interpreter',
        '-p', 'workload=paths,comprehension,reduction', '-rf', 'json',
        '-rff', str(out / 'confirmation.json')]
start = time.time()
with (out / 'confirmation.log').open('w') as f:
    result = subprocess.run(args, stdout=f, stderr=subprocess.STDOUT)
(out / 'confirmation-command.json').write_text(json.dumps(
    dict(argv=args, exit=result.returncode, seconds=time.time()-start), indent=2)+'\n')
print('Confirmation complete', result.returncode, round(time.time()-start, 1), flush=True)
raise SystemExit(result.returncode)
