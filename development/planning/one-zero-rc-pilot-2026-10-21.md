---
status: In Progress
depends_on: ['planning/one-zero-platform-2026-10-21']
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-10-21
---
# Public RC release

User requests GPT-6.1 Sol agents to freeze an RC version, build packages and prepare
two integrator handoffs, with independent parent acceptance. Entry verification:
all 68 accepted platform-manifest paths match. Preserve accepted work and unrelated
untracked research; no external messages or public publication are requested.

## Planned result and allocation

- Packaging agent: verify unused `1.0.0-rc.1`, update only canonical root build
  version, serialize necessary Gradle packaging and focused CLI tests. Build JVM
  standalone and Node CLI, assess library distribution needs, record provenance.
- Integrator agent: new `development/pilot/1.0.0-rc.1/` handoff content, independent
  A/B admission records, executable smoke fixtures and instructions. No invented
  names, production data, limits or observed operation. Package runnable commands.
- Review agent: read-only audit of release identity, packaging/compatibility and
  pilot requirements; produce bounded findings. No duplicate builds.
- Parent: freeze source snapshot and checksums, inspect source/version delta,
  verify actual RC packages on maintained macOS and Linux runtimes, independently
  execute integrator instructions and validate failure controls, integrate results.

## Acceptance

Use a uniquely named, checksummed local handoff archive with a source snapshot and
file manifest including the accepted uncommitted product changes. Base HEAD alone
must not be presented as the RC source revision. Record whether a Git release tag
exists; no invented publication URL. RC label does not imply stable 1.0 readiness.
Both CLI packages must report the exact RC version. Include license and numeric,
API/stdlib/platform/migration limits and rollback guidance. Instructions must work
from extracted delivery directories without repository dependencies. Two independent
integrator records remain unfilled until the actual people and environments exist.

Reuse unchanged semantic/browser evidence with explicit source identity; rerun
packaged acceptance for new RC artifacts and required focused version/packaging
tests. Preserve previous evidence. Keep at least 3 GiB free host disk; do not prune
user Docker data. External pilot requires actual host admission, independent business
oracles, resource ceilings, rollback drill, withheld maintenance exercises and two
14-day representative windows. Final report names the remaining human inputs.

## Scope correction before implementation

User cancelled pilot preparation and explicitly requested publishing the RC instead.
Do not create integrator kits or request pilot inputs. The active deliverable is
public `v1.0.0-rc.1`, released from a precise commit with packages/checksums and
honest prerelease notes, followed by Pages deployment and public browser acceptance.
Parent owns selected-path commit/push, full CI qualification, release dispatch and
published-asset validation. Agents own bounded packaging/version and independent
review. Reuse successful checks for unchanged exact commit; never bypass failures.
The earlier unexecuted pilot-kit allocation and local-only publication boundary
above are superseded by this explicit instruction. Actual pilot remains unstarted.
