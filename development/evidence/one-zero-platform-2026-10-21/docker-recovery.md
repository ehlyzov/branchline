---
status: Implemented
depends_on: ['planning/one-zero-platform-2026-10-21']
last_updated: 2026-10-21
---
# Docker Desktop recovery

The user requested recovery of local Docker to unblock Linux platform acceptance.
The parent performed recovery; a GPT-6.1 Sol High agent independently inspected
the relevant logs without changing the system.

## Confirmed cause

Host `com.docker.backend.log` records shutdown at 2026-10-13 04:51:08 UTC because
writing the VM log failed with `no space left on device`. The subsequent recovery
attempt repeats ENOSPC and reports `Docker Desktop - Disk full`. VM logs record
vda write errors, an aborted EXT4 journal and read-only filesystem. The VM stopped,
but backend processes remained wedged for days; socket requests and Desktop
status/info did not respond. This is supported by explicit errors, not inferred
solely from a currently full disk.

At diagnosis, macOS had about 4.8 GiB available. Docker.raw was a sparse file with
about 13 GiB allocated; its much larger logical capacity was not host free space.
No reset, prune, image/volume deletion or settings change was attempted.

## Recovery and checks

1. `docker desktop restart --timeout 30` failed to stop the stuck backend/build
   processes within its deadline.
2. `docker desktop stop --force --timeout 20` succeeded using the supported Desktop
   command, followed by `docker desktop start --timeout 45`.
3. Unix-socket `/_ping` returned `OK`. Docker Engine 29.7.2 reports Linux/aarch64.
4. A new isolated container from the existing postgres:18.3 image ran a shell
   instead of its database entrypoint. Debian 13.4 on Linux 7.0.12-linuxkit passed
   file write, sync and read assertions. The ephemeral container was removed with
   `--rm`; no existing database or application container was started or altered.
5. A second writable-filesystem check passed. Host free space was about 7.0 GiB
   after recovery; no user files were deleted to obtain that measurement.

Six existing containers, six images and 73 volumes were visible after recovery.
Existing containers are stopped according to their inspected State fields;
`docker info` retained an inconsistent running-count value, so that summary counter
was not used as proof that user applications were running. No application/data
integrity audit is claimed for those containers. The acceptance checks concern
new isolated containers and the engine/storage boundary.

Official commands: [restart](https://docs.docker.com/reference/cli/docker/desktop/restart/)
and [Desktop troubleshooting](https://docs.docker.com/desktop/troubleshoot-and-support/troubleshoot/).

## Evidence and remaining constraint

Raw bounded commands, outcomes, root-cause excerpts and recovery checks are under
`output/one-zero-platform-2026-10-21/docker/`: `diagnosis-agent.md`,
`restart-attempt.json`, `stop-attempt.json`, `start-attempt.json`,
`container-health.json`, `recovery-health.json`, `existing-container-states.txt`.

Host storage remains constrained. Linux acceptance uses isolated containers and
a bounded download budget, with at least 3 GiB free required during setup. Guest
filesystem free-space figures do not replace the host APFS free-space check.
Unrelated user images, volumes and files must not be pruned to make tests pass.
