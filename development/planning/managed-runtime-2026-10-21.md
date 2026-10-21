# Managed runtime installation

Status: implemented; local acceptance passed, publication pending. User explicitly requests removal of Java/Node prerequisites,
clean-machine acceptance and updated public instructions.

Default installer will provision a private, pinned Temurin 21 JRE regardless of
system Java/Node. `--runtime node` provisions a private pinned Node 24 runtime;
`auto` remains accepted as an alias for the deterministic JVM default. Host OS/CPU
selects an official vendor archive, with SHA-256 pinned in the installer. No runtime
is installed globally, no sudo or shell-profile mutation. Preserve vendor license
files and previous installation until runtime + actual CLI version checks succeed.
No silent switch between JVM and JS numeric semantics. Supported archive mapping:
macOS arm64/x64 and Linux glibc arm64/x64; unsupported platforms fail explicitly.

Use official immutable vendor asset URLs and independently fetched checksums.
Existing CLI packages/version remain v1.0.0-rc.1. Update README, install guide,
generated recipe handoff, service contour and focused installer tests. Add a clean
Debian container test to Pages qualification, containing curl/CA/tar/native system
libraries but no Java/Node/Python, running installer as a non-root user. Verify
both defaults and explicit Node in fresh containers, version, successful edited
order output, malformed input and reinstallation. Also check macOS with PATH
restricted so preinstalled Java/Node are unavailable to installer/launcher.

Publish accepted source and instructions; verify public installer bytes, clean
container installation from public URL, and public browser documentation/editor.
Keep unrelated local audit artifacts outside this implementation commit.

## Local acceptance

- Pinned Temurin21.0.12.1+1 vendor SHA256 files agree with GitHub asset digests
  for macOS/Linux arm64/x64. Node24.22.0 pins read from official SHASUMS256.txt.
- 17 fixture behavior checks pass, covering all eight archive mappings, blocked
  host runtime use, runtime corruption/download/version failures preserving the
  old launcher, unsupported OS/CPU/glibc and existing command protection.
- Clean Debian12 arm64 containers: UID1000, no Java/Node/Python, no mounted
  runtimes/caches. Both default JVM and explicit Node install real vendor runtimes
  and published RC, produce independent missing-city/filter/projection output,
  reject malformed JSON and survive reinstall. Docker image is pinned by digest.
- macOS arm64: fresh prefixes, poisoned java/node commands in restricted PATH;
  both private runtimes run actual published CLI and produce BOOK/PEN. This is
  host-runtime isolation, not a claim of a freshly provisioned physical Mac.
- Playground tests and MkDocs build pass; contour audit passes without warnings.
- Logs, vendor pins, image manifest and raw results: output/managed-runtime-2026-10-21/.
