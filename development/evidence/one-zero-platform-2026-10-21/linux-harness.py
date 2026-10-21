#!/usr/bin/env python3
"""Run the unchanged CLI matrix inside one pinned, actual Linux ARM64 container."""
import argparse
import datetime
import hashlib
import json
from pathlib import Path
import platform
import shutil
import subprocess
import sys

P = argparse.ArgumentParser()
P.add_argument('--inside', action='store_true')
A = P.parse_args()
ROOT = Path(__file__).resolve().parents[3]
REL = Path('development/evidence/one-zero-platform-2026-10-21')
OUT = ROOT / 'output/one-zero-platform-2026-10-21/linux'
IMAGE = 'python@sha256:98ce6fb13b3803744fbca11434f00818f1e300e4e48a4f204e07b2c928b6218e'
JAR = Path('cli/build/libs/branchline-cli-0.11.0-alpha.1-all.jar')
TARBALL = Path('cli/build/distributions/branchline-cli-js-0.11.0-alpha.1.tgz')
EXPECTED = {
    JAR: 'e3b726e448be86bb9daafa7c5925ef8f8efc763a4ae4b6a400a52c5f0ff85812',
    TARBALL: '82ee715e5d100bf130c9291a9bdb7c5e4749a6a510e65bb2a1bc37676c3b1a1f',
    REL / 'cli-matrix.py': '4de87bcb9a3042e85577bff9e1b499dff41f7a451c89c48e1e332fa46aa7e15f',
}
def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
for path, expected in EXPECTED.items():
    actual = sha(ROOT/path)
    assert actual == expected, (str(path), actual, expected)

if A.inside:
    out = Path('/results')
    identity = {
        'timestamp_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'uname': list(platform.uname()), 'platform': platform.platform(),
        'libc': platform.libc_ver(), 'os_release': Path('/etc/os-release').read_text(),
        'debian_version': Path('/etc/debian_version').read_text().strip(),
        'python': sys.version, 'image': IMAGE,
        'libc_package': subprocess.run(['dpkg-query', '-W', 'libc6'], capture_output=True, text=True, check=True).stdout,
        'candidate_hashes': {str(p): sha(ROOT/p) for p in EXPECTED},
    }
    assert platform.system() == 'Linux' and platform.machine() == 'aarch64', identity
    (out/'container-identity.json').write_text(json.dumps(identity, indent=2)+'\n')
    command = [sys.executable, str(ROOT/REL/'cli-matrix.py'), '--config', str(ROOT/REL/'linux-runtime-config.json'),
        '--out', str(out), '--jar', str(ROOT/JAR), '--tarball', str(ROOT/TARBALL)]
    (out/'harness-command.json').write_text(json.dumps(command, indent=2)+'\n')
    with (out/'run.log').open('w') as log:
        process = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
    summary_path = out/'summary.json'
    if summary_path.exists():
        summary = json.loads(summary_path.read_text())
        work = Path(summary['work_directory'])
        preserved = []
        for path in work.rglob('*'):
            if path.is_file() and path.suffix in {'.bl', '.blc', '.json'} and path.name not in {'package.json', 'package-lock.json'}:
                target = out/'work-fixtures'/path.relative_to(work)
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(path, target)
                preserved.append({'original':str(path),'preserved':str(target),'sha256':sha(target)})
        (out/'preserved-fixtures.json').write_text(json.dumps(preserved, indent=2)+'\n')
        print(json.dumps({k:summary[k] for k in ['host','cases','passed','failures']}, indent=2), flush=True)
    raise SystemExit(process.returncode)

assert shutil.disk_usage(ROOT).free >= 3*1024**3, 'Host disk reserve below 3 GiB'
OUT.mkdir(parents=True, exist_ok=True)
image = subprocess.run(['docker', 'image', 'inspect', IMAGE], capture_output=True, text=True, check=True)
(OUT/'pinned-image-inspect.json').write_text(image.stdout)
name = 'branchline-linux-platform-20261021'
command = ['docker', 'run', '--rm', '--name', name, '--platform', 'linux/arm64', '--network', 'none', '--read-only',
    '--tmpfs', '/tmp:rw,exec,size=268435456', '--memory', '2g', '--cpus', '2',
    '--mount', f'type=bind,source={ROOT},target=/repo,readonly',
    '--mount', f'type=bind,source={OUT},target=/results',
    '--mount', f'type=bind,source={OUT / "runtimes"},target=/runtimes,readonly',
    IMAGE, 'python', f'/repo/{REL}/linux-harness.py', '--inside']
(OUT/'docker-command.json').write_text(json.dumps(command, indent=2)+'\n')
before = shutil.disk_usage(ROOT).free
process = subprocess.run(command, cwd=ROOT)
after = shutil.disk_usage(ROOT).free
(OUT/'host-disk-budget.json').write_text(json.dumps({'before_run_free_bytes':before,'after_run_free_bytes':after,'minimum_required_free_bytes':3*1024**3}, indent=2)+'\n')
assert after >= 3*1024**3, 'Host disk reserve below 3 GiB after run'
raise SystemExit(process.returncode)
