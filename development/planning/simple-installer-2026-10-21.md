# One-command Branchline installation

Status: published and independently accepted. User requests installation as simple as Codeclew.

Reference inspected: `/Users/eugene/repo/codeclew/site/install.sh` and its README.
Scope: a POSIX shell installer published as `docs/install.sh`, installing the
already released v1.0.0-rc.1 CLI and a `bl` launcher without sudo. Use an available
Java 21+ (preferred) or Node 22/24; explicit runtime choice is supported. Do not
silently install a system runtime or modify shell profiles. Explain JS numeric
limits and RC status. Pin the default release instead of GitHub latest (which
excludes RCs). Verify the release SHA256SUMS and actual CLI version before switching
the launcher. Preserve an existing installation on failed downloads/checks and
refuse to overwrite an unrelated command. Offer a version override and rerun for
updates, with clear PATH and uninstall instructions.

Integrate README, install/getting-started guides and generated six-recipe local
handoffs. Keep downloaded edited input separate from optional original downloads.
Installer is a new distribution entrypoint: update service map/verification and
add focused shell/fixture checks to the Pages build. No changes to runtime packages
or language semantics; no new RC is required.

Acceptance: fixture tests cover checksum/version failure, unavailable runtime,
non-owned launcher, argument forwarding/exit status, repeat installation and
paths with spaces/apostrophes. Independently install actual release JVM and Node
packages into temporary prefixes and execute an edited order input. Build docs,
check generated recipes and retain evidence. Publication is a continuation of the
requested public-site installation journey; deploy only accepted changes, retaining
unrelated research artifacts outside the implementation commit.

## Local acceptance

- 14 behavioral installer checks passed: runtime selection, repeated install,
  spaces/apostrophes, argv/exit propagation, checksum/duplicate/version/download
  failure preservation, unrelated file/symlink refusal and archive traversal.
- Actual GitHub release packages downloaded by the installer on macOS arm64:
  Temurin Java21 and Node22.23.3 both report v1.0.0-rc.1 and produce
  `{"order_id":"A-104","city":"London","skus":["BOOK","PEN"]}`
  for an independently edited input. Temporary install roots only.
- Playground npm tests passed, including 36 recipe executions (13 expected
  rejections); MkDocs build passed, installer copied byte-for-byte. Existing
  benchmark-relative-link warnings remain unrelated to this slice.
- Contour audit passes without warnings. Generated change overlay is explicitly
  bounded to implementation source paths, excluding unrelated output artifacts.
- Evidence: output/simple-installer-2026-10-21/. No CLI package rebuild or version
  bump: the installer consumes the already published checksum-verified RC.

## Public acceptance

- Implementation commit: 2f07dee39dea307e2aa5c5379d7add49c267b484.
- Test Suite: https://github.com/ehlyzov/branchline/actions/runs/39642395341
  completed successfully; all five jobs passed and no failed step was hidden by
  continue-on-error.
- Pages: https://github.com/ehlyzov/branchline/actions/runs/39643408669 completed
  successfully, including installer fixture checks on Ubuntu. Public metadata
  identifies the exact implementation SHA; public install.sh matches source bytes.
- Actual public curl-to-sh installations on macOS arm64 passed for Java21 and
  Node22.23.3 in fresh temporary prefixes. Both report rc.1 and produce BOOK/PEN
  from public recipe downloads with PEN qty changed to1.
- Public browser: install guide shows the command/prerequisites, editor runs the
  order example to BOOK, local handoff shows bl and preserves the edited-file
  context. No browser console warnings/errors in the tested path.
- This did not install into the user's default home prefix, install a runtime,
  change shell profiles, or qualify a new OS/CPU/runtime matrix.
- Raw acceptance JSON/logs and screenshots: output/simple-installer-2026-10-21/.
