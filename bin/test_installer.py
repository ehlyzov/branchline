#!/usr/bin/env python3
"""Behavioral installer checks; network and runtime stubs, real shell/filesystem/hash/tar."""
import hashlib
import io
import os
import re
from pathlib import Path
import subprocess
import tarfile
import tempfile

SOURCE = Path(__file__).resolve().parents[1] / 'docs/install.sh'
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
case "$url" in *OpenJDK*|*node-v24*) [ "${TEST_RUNTIME_DOWNLOAD_FAIL:-0}" != 1 ] || exit 22 ;; esac
cp "$TEST_ASSETS/${url##*/}" "$output"
''')
    java_stub = '''#!/bin/sh
if [ "$1" = -version ]; then echo 'openjdk version "'"${TEST_RUNTIME_VERSION:-21.0.12.1}"'"' >&2; exit 0; fi
shift 2
if [ "$1" = --version ]; then echo "Branchline CLI ${TEST_CLI_VERSION:-v1.0.0-rc.1}"; exit 0; fi
printf '%s\n' "$@"
exit 37
'''
    node_stub = '''#!/bin/sh
if [ "$1" = --version ]; then echo "${TEST_RUNTIME_VERSION:-v24.22.0}"; exit 0; fi
shift
if [ "$1" = --version ]; then echo "Branchline CLI ${TEST_CLI_VERSION:-v1.0.0-rc.1}"; exit 0; fi
printf '%s\n' "$@"
exit 37
'''
    # Only the test copy receives fixture archive hashes; production has no checksum override.
    source = SOURCE.read_text()
    runtime_archives = []
    for match in re.finditer(r'      (mac|darwin|linux):(aarch64|arm64|x64)\) RUNTIME_SHA=([0-9a-f]{64})', source):
        os_name, arch, pinned = match.groups()
        is_java = source.index(match.group()) < source.index('RUNTIME_BIN=bin/java')
        if is_java:
            name = f'OpenJDK21U-jre_{arch}_{os_name}_hotspot_21.0.12.1_1.tar.gz'
            archive_root = 'jdk-21.0.12.1+1-jre'
            binary = 'Contents/Home/bin/java' if os_name == 'mac' else 'bin/java'
            payload = java_stub.encode()
        else:
            archive_root = f'node-v24.22.0-{os_name}-{arch}'
            name = archive_root + '.tar.gz'
            binary = 'bin/node'
            payload = node_stub.encode()
        archive_path = assets / name
        with tarfile.open(archive_path, 'w:gz') as archive:
            info = tarfile.TarInfo(f'{archive_root}/{binary}')
            info.size = len(payload); info.mode = 0o700
            archive.addfile(info, io.BytesIO(payload))
            license_info = tarfile.TarInfo(f'{archive_root}/LICENSE')
            license_info.size = 7
            archive.addfile(license_info, io.BytesIO(b'license'))
        source = source.replace(pinned, hashlib.sha256(archive_path.read_bytes()).hexdigest())
        runtime_archives.append(archive_path)
    assert len(runtime_archives) == 8
    SCRIPT = root / 'test-install.sh'
    SCRIPT.write_text(source)
    executable(fake / 'uname', '#!/bin/sh\ncase "$1" in -s) echo "${TEST_OS:-Linux}" ;; -m) echo "${TEST_ARCH:-x86_64}" ;; esac\n')
    executable(fake / 'getconf', '#!/bin/sh\necho "${TEST_LIBC:-glibc 2.36}"\n')
    for host_runtime in ('java', 'node'):
        executable(fake / host_runtime, '#!/bin/sh\necho "Host runtime must not be used" >&2\nexit 99\n')
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
    checked('default downloads private Java; host runtimes ignored; quoted argv and exit preserved')
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
    for change in ({'TEST_RUNTIME_VERSION':'bad'}, {'TEST_RUNTIME_DOWNLOAD_FAIL':'1'}):
        run(ok=False, extra=change)
        assert launcher.read_bytes() == previous
        checked(f'private runtime failure preserves active command: {next(iter(change))}')
    archive = assets / 'OpenJDK21U-jre_x64_linux_hotspot_21.0.12.1_1.tar.gz'
    saved = archive.read_bytes(); archive.write_bytes(saved + b'corrupt')
    run(ok=False)
    assert launcher.read_bytes() == previous
    archive.write_bytes(saved)
    checked('runtime checksum mismatch preserves active command')
    for os_name, arch in [('Darwin','arm64'),('Darwin','x86_64'),('Linux','aarch64'),('Linux','x86_64')]:
        for runtime in ('jvm','node'):
            run('--runtime',runtime,extra={'TEST_OS':os_name,'TEST_ARCH':arch})
    checked('all eight OS/CPU/runtime mappings install private executable')
    for extra in ({'TEST_OS':'FreeBSD'},{'TEST_ARCH':'riscv64'},{'TEST_LIBC':'musl'},{'TEST_LIBC':'glibc 2.27'}):
        run(ok=False,extra=extra)
    checked('unsupported platform and libc rejected')
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
