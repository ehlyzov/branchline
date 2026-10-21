#!/usr/bin/env python3
"""Behavioral installer checks; network and runtime stubs, real shell/filesystem/hash/tar."""
import hashlib
import io
import os
from pathlib import Path
import subprocess
import tarfile
import tempfile

SCRIPT = Path(__file__).resolve().parents[1] / 'docs/install.sh'
VERSION = 'v1.0.0-rc.1'

def executable(path, text):
    path.write_text(text)
    path.chmod(0o700)

with tempfile.TemporaryDirectory(prefix='branchline-installer-test-') as work:
    root = Path(work)
    fake = root / 'tools'
    fake.mkdir()
    assets = root / 'assets'
    assets.mkdir()
    jar = assets / f'branchline-cli-{VERSION}-all.jar'
    jar.write_bytes(b'fixture jar\n')
    tgz = assets / f'branchline-cli-js-{VERSION}.tgz'
    with tarfile.open(tgz, 'w:gz') as archive:
        info = tarfile.TarInfo('bin/bl.cjs')
        payload = b'fixture node launcher\n'
        info.size = len(payload)
        archive.addfile(info, io.BytesIO(payload))
    sums = assets / 'SHA256SUMS'
    good_sums = ''.join(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n' for p in (jar, tgz))
    sums.write_text(good_sums)
    executable(fake / 'curl', '''#!/bin/sh
set -eu
url=; output=
while [ "$#" -gt 0 ]; do
 case "$1" in https://*) url=$1; shift ;; --output) output=$2; shift 2 ;; *) shift ;; esac
done
[ "${TEST_DOWNLOAD_FAIL:-0}" != 1 ] || exit 22
cp "$TEST_ASSETS/${url##*/}" "$output"
''')
    executable(fake / 'java', '''#!/bin/sh
if [ "$1" = -version ]; then echo 'openjdk version "'"${TEST_JAVA_MAJOR:-21}"'.0.1"' >&2; exit 0; fi
shift 2
if [ "$1" = --version ]; then echo "Branchline CLI ${TEST_CLI_VERSION:-v1.0.0-rc.1}"; exit 0; fi
printf '%s\n' "$@"
exit 37
''')
    executable(fake / 'node', '''#!/bin/sh
if [ "$1" = --version ]; then echo "v${TEST_NODE_MAJOR:-24}.0.0"; exit 0; fi
shift
if [ "$1" = --version ]; then echo "Branchline CLI ${TEST_CLI_VERSION:-v1.0.0-rc.1}"; exit 0; fi
printf '%s\n' "$@"
exit 37
''')
    install = root / "user's app data"
    bindir = root / "user's bin"
    env = dict(os.environ, PATH=f'{fake}:/usr/bin:/bin', TEST_ASSETS=str(assets),
               BRANCHLINE_INSTALL_ROOT=str(install), BRANCHLINE_BIN_DIR=str(bindir))
    for key in ('BRANCHLINE_RUNTIME', 'BRANCHLINE_VERSION'):
        env.pop(key, None)
    def run(*args, ok=True, extra=None):
        result = subprocess.run(['sh', str(SCRIPT), *args], env=env | (extra or {}), text=True, capture_output=True)
        assert (result.returncode == 0) == ok, (args, result.returncode, result.stdout, result.stderr)
        return result
    count = 0
    def checked(label):
        global count
        count += 1
        print(f'PASS {count}: {label}')
    run()
    launcher = bindir / 'bl'
    previous = launcher.read_bytes()
    assert 'jvm' in str(next((install / 'releases').iterdir()))
    result = subprocess.run([str(launcher), 'file with spaces.bl', '--input', '-', ''], env=env, capture_output=True, text=True)
    assert result.returncode == 37 and result.stdout == 'file with spaces.bl\n--input\n-\n\n', result
    checked('auto prefers Java; quoted paths, arguments and exit status preserved')
    run('--runtime', 'node')
    previous = launcher.read_bytes()
    checked('explicit Node install and version check')
    run('--runtime', 'node')
    previous = launcher.read_bytes()
    checked('repeat installation safely replaces managed launcher')
    for change in ({'TEST_DOWNLOAD_FAIL':'1'}, {'TEST_CLI_VERSION':'v9.9.9'}):
        run(ok=False, extra=change)
        assert launcher.read_bytes() == previous
        checked(f'failed install preserves active command: {next(iter(change))}')
    sums.write_text(good_sums.replace(good_sums[:64], '0' * 64))
    run('--runtime', 'jvm', ok=False)
    assert launcher.read_bytes() == previous
    checked('checksum mismatch preserves active command')
    sums.write_text(good_sums + good_sums.splitlines()[0] + '\n')
    run('--runtime', 'jvm', ok=False)
    assert launcher.read_bytes() == previous
    checked('duplicate checksum rejected')
    sums.write_text(good_sums)
    run(ok=False, extra={'TEST_JAVA_MAJOR':'17', 'TEST_NODE_MAJOR':'18'})
    assert launcher.read_bytes() == previous
    checked('missing suitable runtime fails before activation')
    run(extra={'TEST_JAVA_MAJOR':'17', 'TEST_NODE_MAJOR':'22'})
    checked('auto falls back to maintained Node')
    for args in (('--version','../../bad'), ('--runtime','other'), ('--runtime',)):
        run(*args, ok=False)
    checked('invalid options rejected')
    launcher.write_text('#!/bin/sh\necho unrelated\n')
    run(ok=False)
    assert launcher.read_text() == '#!/bin/sh\necho unrelated\n'
    checked('unrelated existing command not overwritten')
    launcher.unlink()
    launcher.symlink_to(jar)
    run(ok=False)
    assert launcher.is_symlink()
    checked('existing symlink not overwritten')
    launcher.unlink()
    # Archive safety is checked even when the release checksum agrees.
    with tarfile.open(tgz, 'w:gz') as archive:
        entry = tarfile.TarInfo('../outside')
        entry.size = 1
        archive.addfile(entry, io.BytesIO(b'x'))
    sums.write_text(f'{hashlib.sha256(tgz.read_bytes()).hexdigest()}  {tgz.name}\n')
    run('--runtime','node',ok=False)
    assert not launcher.exists() and not (install/'releases/outside').exists()
    checked('unsafe archive rejected before extraction')
    assert not (bindir / '.branchline-install-lock').exists()
    assert not list(bindir.glob('.bl-launcher.*'))
    checked('temporary launchers and lock cleaned on failures')
print(f'{count} installer behavior checks passed.')
