# 1.0.0-rc.1 release qualification

User cancelled pilot preparation and requested a public RC. No integrator kit or
external pilot is created. The exact release source is identified by the eventual
Git tag; base 9de86a0 alone does not include this release's accepted changes.

Local pre-publication gates pass: all five packages have exact RC identities,
99 CLI JUnit tests have zero failures/errors/skips, and 144 packaged cases each
pass on macOS arm64 and the pinned Debian13 arm64/Linuxkit container. The parent
independently recomputed raw exit/JSON expectations and checked version/artifact
hashes. This reuses the prior matrix with only its exact version assertion changed.
JVM21/25 and Node22/24 are exercised. Prior unchanged semantic and browser evidence
remains separately labelled; public CI and deployed-site acceptance are still required.

- [Packaging](packaging.md), [independent release review](review.md).
- [Matrix summary and raw-evidence hashes](local-matrix-summary.json).
- [Executable CLI matrix](cli-matrix.py).
- Raw logs/results are local under `output/rc-1.0.0-2026-10-21/`; large runtimes,
  recordings and binaries are intentionally not source-controlled.

Local package hashes are not promised to equal the CI build's archive hashes.
Validate the actually published downloads against their SHA256SUMS and metadata,
then execute those downloads before declaring publication accepted. Full CI must
pass for the exact release commit, including test steps as well as summary jobs.
The parent records release/run/deployment URLs and final checks after publication.

Stable1.0 readiness and actual host/integrator field observation remain outside
this RC release. Native Linux/x86, untested browsers and other runtime patches
are not inferred from the bounded local matrix.
