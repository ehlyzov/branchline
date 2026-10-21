---
status: Implemented
depends_on: ['planning/one-zero-platform-2026-10-21']
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-10-21
---
# Linux ARM64 packaged CLI acceptance

The actual Linux gate passed **144/144 CLI invocations**, with zero failures,
at `2026-10-21T09:49:33Z`. It used the same candidate jar/tarball and unchanged
[cli-matrix.py](cli-matrix.py) accepted on macOS. No product change, rebuild or
Gradle task was performed for this Linux gate.

This is Linux ARM64 execution inside a Docker Desktop VM on the macOS ARM64
host. It is not native Linux-host acceptance, x86-64 execution, or emulation of
Windows. The earlier Docker unavailability was repaired separately by the parent;
the previous macOS-only evidence remains an accurate account of its earlier run.

## Actual environment and matrix

| Property | Observed value |
| --- | --- |
| Image tag selected | Official `python:3.13-slim-trixie` |
| Image executed | `python@sha256:98ce6fb13b3803744fbca11434f00818f1e300e4e48a4f204e07b2c928b6218e` |
| Userland | Debian GNU/Linux 13.7 (trixie) |
| libc | glibc 2.41, package `libc6:arm64 2.41-12+deb13u4` |
| Kernel | `7.0.12-linuxkit`, build `#1 SMP PREEMPT Wed Aug 12 20:18:49 UTC 2026` |
| CPU reported inside container | `aarch64`; Node reports `linux` / `arm64` |
| Docker engine / desktop | Engine 29.7.2, Docker Desktop 4.87.0 |
| Harness Python | CPython 3.13.16 |

The Debian image is 13.7, independently read from `/etc/debian_version`; the
parent's earlier PostgreSQL recovery smoke used a different Debian image.
The exact image inspection and `/etc/os-release`, libc, Python and kernel
observations are retained under `output/one-zero-platform-2026-10-21/linux/`.

| Actual Linux runtime | CLI invocations | Result |
| --- | ---: | --- |
| Temurin JRE 21.0.12.1+1-LTS | 35 | Pass |
| Temurin JRE 25.0.4.1+1-LTS | 35 | Pass |
| Node 22.23.3, ARM64 | 36 | Pass |
| Node 24.22.0, ARM64 | 36 | Pass |
| Retained v2 reader, executed using the Linux Temurin 21 JRE | 2 | Rejects new JVM and Node v3 artifacts |

JRE distributions suffice for the actual packaged JVM CLI; no JDK or compilation
toolchain was added. JRE archives were selected from official Adoptium GitHub
release metadata and matched the release SHA-256 files. The Adoptium API endpoint
returned HTTP 403 during discovery; official GitHub metadata and release assets
worked. Node archives matched their official versioned SHASUMS256.txt entries.
Exact URLs, archive sizes/hashes and extracted roots are in
`linux/runtime-downloads.json`; binary SHA-256 and full version output are in
`linux/summary.json`.

## Frozen identities

| Input | SHA-256 |
| --- | --- |
| Candidate JVM jar | `e3b726e448be86bb9daafa7c5925ef8f8efc763a4ae4b6a400a52c5f0ff85812` |
| Candidate Node tarball | `82ee715e5d100bf130c9291a9bdb7c5e4749a6a510e65bb2a1bc37676c3b1a1f` |
| Unchanged independent CLI matrix | `4de87bcb9a3042e85577bff9e1b499dff41f7a451c89c48e1e332fa46aa7e15f` |
| Linux driver | `b82a262b8686f850e90c6c789f53e6f93380db7a7b5be02a92d7d0ebf9067a28` |
| Official Temurin JRE 21 ARM64 archive | `14be1f35ebdbd1f6e8d57eb911a3ffb74d6d9aa255abc5daf2b1302002cf2cf2` |
| Official Temurin JRE 25 ARM64 archive | `34828cbb93ed31c281c84ecb31ddab655d11a802f263c1fc019d42e9e0230fed` |
| Official Node 22 Linux ARM64 archive | `5ced2d48d1d7198739b7f86804de0171aefb6823b684b12341d3321afc3cb0b2` |
| Official Node 24 Linux ARM64 archive | `b7e7991d5407815adfa74a2a9cd4db683552c8ce32ff4a295c22e972849c3f5d` |

The driver verifies candidate and CLI-matrix hashes before starting Docker and
again inside the Linux container. No cross-platform engine supplies the expected
JSON. The frozen matrix directly specifies outputs, exit statuses and structured
error categories.

## Checks executed

Each runtime gets a fresh installation directory with spaces in `/tmp`. Each JVM
uses the actual copied standalone jar and Linux `java -jar`; each Node uses the
actual tarball's executable `bin/bl.cjs` shebang with its exact Linux runtime on
PATH. No npm install or network access occurs during the gate. The additional Node
case exercises the XML parser bundled in the package.

All cases described in [the CLI evidence](cli.md) execute on these four runtimes:
intro projection/defaults/filter, file and stdin input, independent signed numeric
oracles, explicit strict contracts in source and saved VM execution, inspect and
syntax diagnostics, usage/input/runtime/I/O status and JSON error categories,
five actual I/O failures, genuine v1/v2 reads, v3 numeric admission/rejection,
legacy relabelling rejection, future-version rejection, and real compile/save/exec.
Thus the repaired native Node filesystem error path is exercised on Linux too.

Crossed artifacts use the Linux Node 22 writer for both JVMs and the Linux Temurin
21 writer for both Node runtimes, with filter and numeric programs. All four
writers' artifacts are also executed locally. The historical actual v2 reader
rejects both current baseline writers' v3 files. This does not establish all pairs
of writers, universal snapshot migration, or arbitrary-precision JVM/JS parity.

No product defect or harness change was needed after the macOS gate. The same
oracles passed on Linux, including all five structured I/O failures on Node.

## Reproduction and retained evidence

[linux-harness.py](linux-harness.py) launches the pinned image and calls the frozen
matrix with [linux-runtime-config.json](linux-runtime-config.json), explicit
`--config`, `--out`, `--jar` and `--tarball` arguments. The image and recorded
checksum-verified runtime extractions must already be present:

```sh
docker pull --platform linux/arm64 python@sha256:98ce6fb13b3803744fbca11434f00818f1e300e4e48a4f204e07b2c928b6218e
python3 development/evidence/one-zero-platform-2026-10-21/linux-harness.py
```

One container runs with `--rm --platform linux/arm64 --network none --read-only`.
The repository and runtime mount are read-only; only this task's `/results` mount
and a 256 MiB executable `/tmp` tmpfs are writable. No existing container or volume
was touched. The task container no longer exists after successful `--rm` cleanup.

The raw directory is `output/one-zero-platform-2026-10-21/linux/`:

- `results.json`, `summary.json`, `run.log`, `docker-run.log`: all 144 commands,
  stdout/stderr, assertions, results, runtime identity and artifact hashes.
- `container-identity.json`, `image-inspect.json`, `pinned-image-inspect.json`:
  real kernel/userland/libc/CPU and selected image identity.
- `docker-command.json`, `harness-command.json`, `runtime-downloads.json`:
  exact launch arguments and official runtime provenance.
- `work-fixtures/`, `preserved-fixtures.json`: source/input fixtures and real
  generated compiled files copied from container tmpfs before removal, with
  original-to-preserved paths and SHA-256 values.
- `host-disk-budget.json`: free host bytes before/after the actual run were
  6,759,387,136 / 6,758,703,104, both above the required 3 GiB reserve. Dependency
  preparation reduced the initial approximately 7.0 GiB to approximately 6.3 GiB.
  No image, container, volume or unrelated file was pruned to make room.

## Bounded admission

This closes the previously unexecuted **Debian 13 ARM64 container** cells for the
four exact runtimes and candidate hashes above. It is evidence for a containerized
Linux ARM64 candidate support row. Native Linux hosts, Linux x86-64, musl/Alpine,
other distributions/libc versions, Windows, Node 18 on Linux, other Java/Node
versions and all newer patches remain untested by this gate. The operational
pilot and any explicitly admitted additional platform remain separate requirements.
