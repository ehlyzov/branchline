---
status: Implemented
depends_on: ['planning/one-zero-surface-codec-2026-10-18']
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-10-21
---
# Platform acceptance before 1.0

User authorizes GPT-6.1 Sol agents and independent parent acceptance. Prior-stage
54-file manifest matches the working tree; preserve those changes and unrelated
research artifacts. This slice verifies a bounded candidate matrix and records
remaining external pilot obligations. No stable release or completed field trial
is inferred from local checks.

## Work allocation and gates

- CLI/platform agent: inspect distribution requirements, exercise actual packaged
  JVM/Node artifacts on available runtime versions, including Java 21 and Node 18;
  pursue Linux/container checks when a real execution environment is available.
  Pin exact OS/CPU/runtime/artifact hashes. Independent JSON/error/artifact oracles,
  fresh installation paths, source and compiled execution, strict contracts,
  old/new artifact boundaries. Report unsupported/unavailable cells honestly.
- Browser agent: use Playwright CLI against freshly built candidate assets in a
  local HTTP site; real installed browser engines, editor/examples, independent
  transforms, edit/rerun, syntax failure/recovery, numeric regressions, console
  and resource errors. Record browser version, URL and asset identity; do not
  confuse current public deployment with this uncommitted candidate.
- Pilot/review agent: read-only audit of matrix/compatibility/pilot gates, produce
  a concrete external admission checklist with owners/data/thresholds/rollback
  and 14-day evidence requirements. No claims of independent field participation.
- Parent: serialize Gradle packaging, verify evidence and source identity, integrate
  platform policy and report, update development index and canonical contour.

Agents may write only their new evidence/harness files unless a defect is reported
and a bounded repair is coordinated. No competing Gradle builds, commit/push or
deployment. Reuse unchanged previous semantic tests; run new platform/build gates
and targeted reruns for material changes. Do not launch broad CI just to count
the same checks again. Unknown environments remain release admission blockers
unless deliberately outside the published candidate support matrix.

## Findings during acceptance

Prior manifest matches. Fresh JVM/Node packaging and playground asset tests build
successfully. Docker's daemon status/info and Unix socket /_ping time out even
after opening the installed app; do not restart an unknown shared daemon or claim
Linux coverage from macOS. A Linux host has been requested while independent
local checks continue. README's identical-platform/no-dependencies statement
conflicts with numeric and preview dependency contracts; parent will narrow it
and document exact tested versions and outstanding cells in the install guide.

Packaged Node 18/22/24 reproduces uncaught native ENOENT for a missing source file:
exit 1 with a JS stack rather than the documented IO exit 74/JSON envelope.
The CLI agent owns a bounded native-filesystem exception-boundary correction in
JsPlatform and targeted regressions; preserve the original evidence. This is a
required platform defect repair, not an oracle change. Parent will rebuild and
run CLI JVM/Node tests before the final packaged matrix. Browser assets remain
unchanged if the repair stays in the CLI JS source set.

## Completed cells and initial Linux blocker

Three GPT-6.1 Sol High agents completed their slices. Parent accepted 251 packaged
CLI cases across three JVMs and four Node versions on macOS arm64; 99 CLI test
executions have no failures; Chrome and WebKit each passed 14 actual UI checks.
Eight independent parent UTF-8/cross-artifact checks passed. Node native I/O
boundary repair is included and tested. Exact identities, evidence and limitations
are in `development/evidence/one-zero-platform-2026-10-21/README.md`.

At the initial checkpoint, the plan remained In Progress because Docker was
unavailable and no alternative Linux host was supplied. The follow-up below
resolves that blocker with actual Linux container execution.
Firefox also failed before application startup and remains unqualified. The
Linux cell requires a real execution host, fixed OS/CPU/runtime config and the
portable packaged acceptance harness. The external pilot remains a separate
unfilled admission/operation process; local agent tests cannot close it.

## Docker recovery follow-up

The user explicitly requests diagnosing/recovering local Docker to enable the
remaining Linux cell. Logs prove host ENOSPC on 2026-10-13, followed by VM disk
I/O errors/read-only filesystem and a disk-full shutdown. Preserve Docker.raw,
images, volumes and settings; do not reset or prune user data. A bounded ordinary
restart could not stop wedged backend processes; the documented forced stop
succeeded. Parent owns restart/recovery and health checks; GPT-6.1 Sol agent owns
read-only root-cause evidence. Resume the Linux matrix only after successful
daemon, storage and container checks, keeping host free-space limits visible.

Recovery succeeded: Docker Engine 29.7.2 responds, an isolated Debian 13.4 arm64
container runs on Linux 7.0.12-linuxkit and passes write/sync/read checks. No reset,
prune, settings edit or user image/volume deletion was performed. Approximately
7 GiB host space is available after restart. The CLI agent now owns a bounded
Linux arm64 packaged matrix on the identical candidate artifacts, using isolated
containers and verified runtime downloads; native Linux/x86 parity is not inferred.


The unchanged packaged harness now passes 144/144 checks on Debian 13.7 arm64,
glibc 2.41, Linux 7.0.12-linuxkit: Temurin JRE 21.0.12.1+1 and 25.0.4.1+1,
Node 22.23.3 and 24.22.0, plus retained-v2-reader rejections. The parent independently
verified raw exit codes/JSON oracles, runtime archive/binary hashes, candidate and
harness hashes. No product changes were needed. This closes the bounded platform
slice (macOS arm64 and this Linux container cell). Actual pilot-host qualification,
other architectures/OS/browsers and external observation are separate obligations.
See `linux.md` and `docker-recovery.md` under the platform evidence directory.
