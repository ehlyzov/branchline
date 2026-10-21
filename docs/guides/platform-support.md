---
title: Platform Support and Acceptance
---

# Platform support and acceptance

Branchline v1.0.0-rc.1 is a release candidate. This page separates the intended 1.0 platform scope
from evidence for a particular candidate. A Java class-file minimum or a Node
launcher requirement is not proof that every newer runtime, OS and CPU works.

## Candidate support policy

- JVM target majors: Java 21 and 25. Record the vendor and exact update in every
  acceptance record. The distribution's class-file minimum remains Java 21.
- Node target majors: Node 22 and 24. The older Node 18 launch requirement is
  checked for backward compatibility; it is not a maintained-runtime promise.
  Node 18 and 25 are EOL in the [official release table](https://nodejs.org/en/about/previous-releases)
  checked on 2026-10-21. Prefer a maintained LTS runtime for a new integration.
- OS/CPU scope must be explicit. macOS arm64 development acceptance and the
  selected Linux server distribution/CPU are separate required cells. Windows,
  Intel macOS, other Linux variants and other architectures are unqualified until
  independently tested. Container evidence must state the actual architecture;
  it does not establish Windows or native desktop behavior.
- Browser acceptance names actual browser/engine versions. Automated WebKit is
  an engine check, not certification of the Safari application. Mobile browsers
  and untested versions are outside the accepted matrix.

For 1.x, each release must qualify the currently selected updates of its admitted
runtime majors on the supported OS/CPU cells. Retain the admitted minimum and
source/CLI behavior for the rest of 1.x under the
[compatibility policy](release-readiness.md#candidate-10-compatibility-contract).
A vendor's EOL changes our maintenance recommendation; it does not silently
authorize a breaking change or a raised minimum in a patch/minor release.
An incompatible support transition requires a major release and migration notes.
This policy promises neither vendor security fixes nor an independent security SLA.

## Pre-RC acceptance record

The 2026-10-21 pre-RC acceptance used the local candidate based on commit `9de86a0`,
including the subsequent API/stdlib and artifact-v3 changes. Its distributions
carried the development version `0.11.0-alpha.1`; they are **not identical to
the already published release with that name**. Source manifests and artifact
checksums identify that local candidate. The versioned `v1.0.0-rc.1` packages
require separate release acceptance with their exact commit and artifact hashes;
the results below do not establish those newly packaged assets or a public deployment.

The final per-cell outcomes and exact hashes are recorded in the repository at
`development/evidence/one-zero-platform-2026-10-21/README.md`.
Pending or unavailable required cells prevent closing the full platform gate.

| Environment | Actual runtime / browser | Candidate result |
| --- | --- | --- |
| macOS 27.0.1 arm64 | Temurin 21.0.11; Liberica 21.0.9 | Packaged JVM CLI checks passed on both |
| macOS 27.0.1 arm64 | Liberica 25.0.1 | Packaged JVM CLI checks passed |
| macOS 27.0.1 arm64 | Node 22.23.3; 24.22.0 | Packaged Node CLI checks passed on both |
| macOS 27.0.1 arm64 | Node 18.20.8; 25.3.0 | Compatibility checks passed; EOL runtime recommendation excluded |
| macOS 27.0.1 arm64 | Chrome 155.0.8059.40 | 14 actual editor/run/error/recovery checks passed |
| macOS 27.0.1 arm64 | Playwright WebKit 26.6 | Same 14 checks passed; not Safari application acceptance |
| macOS 27.0.1 arm64 | Playwright Firefox | Launch failed in this environment; application behavior unverified |
| Debian 13.7 arm64 container; Linux 7.0.12-linuxkit; glibc 2.41 | Temurin JRE 21.0.12.1+1; 25.0.4.1+1; Node 22.23.3; 24.22.0 | 144 packaged checks passed, including two retained-reader rejection checks |
| Windows, Intel macOS, mobile/Safari applications | Not exercised | Unqualified |

The **bounded candidate platform gate passes** for macOS arm64 and the Linux
container cell above, using identical candidate artifact hashes. Container tests
execute on a real Linux kernel but do not qualify native Linux hosts, x86_64 or
other distributions. Each external pilot still needs its actual host environment
qualified before admission.

## What the checks establish

Packaged CLI checks extract the Node tarball or copy the standalone JVM jar to
fresh paths with spaces. They run real launchers, independent JSON/error oracles,
stdin, inspect, contracts, compile/save/exec, crossed JVM/Node artifacts and legacy
format boundaries. Node dependencies must work from the extracted distribution
without installing repository dependencies. Version and artifact hashes are saved.

Browser checks use a freshly built local MkDocs playground and its actual runtime
assets: examples, editing, independent transformations, reruns, syntax failure and
recovery, numeric regressions, console and resource loading. These local checks do
not establish that the public website has received the candidate. Publication
requires a separate public-site check tied to the deployed revision.

Existing unchanged JVM/Node conformance, numeric, stable API and stdlib evidence
is reused only when its source identity matches. Packaged smoke tests supplement
that evidence; they do not prove every API on every platform. Playground tests
exercise the interpreter; compiled CLI execution provides the ordinary VM checks.

The [external pilot](pilot-plan.md) additionally requires the actual host environment,
representative data, host-enforced limits, independent installation and maintenance,
rollback reconciliation and two representative 14-day observation records.
