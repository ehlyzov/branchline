# Published RC acceptance — completed

Branchline [v1.0.0-rc.1](https://github.com/ehlyzov/branchline/releases/tag/v1.0.0-rc.1)
is public as a prerelease, with all five packages, release metadata and SHA256SUMS.
Release tag/source: `98c76591f6111d811c02366ee4fa61907cdee3fd`.

- [Exact-commit CI](https://github.com/ehlyzov/branchline/actions/runs/39581974629)
  passed all five jobs and actual test steps, not just summary/badge steps.
- [Release and Pages workflow](https://github.com/ehlyzov/branchline/actions/runs/39582760339)
  passed all three jobs and bound the deployed version/SHA to the release.
- The actual downloaded seven-asset inventory and six checksum entries matched;
  tag, metadata, CLI versions and npm metadata agree. The public packages are
  separately hashed CI builds, not the local packaging files.
- Published CLI packages passed 144/144 macOS arm64 and 144/144 pinned Debian13
  arm64/Linuxkit checks on Java21/25 and Node22/24. Parent independently checked
  every raw exit/JSON oracle and artifact hash. Eight additional parent cases
  passed for UTF-8 paths/data, preserved zero, source execution, compilation and
  both JVM/Node artifact producers/readers.
- The [public editor](https://ehlyzov.github.io/branchline/playground/) passed
  14/14 checks in a fresh Chrome155.0.8059.40 session at 10:19 UTC on 2026-10-21.
  The 40-entry catalog loaded; three examples and independent transform,
  filtering/projection, program/input edits and reruns, draft restoration,
  numeric regressions and syntax-error recovery passed. No editor resource or
  runtime failure occurred. Initial navigation logged the known external GitHub
  `/releases/latest` 404; the final reload had zero console/HTTP/request errors.
  This is a Chrome acceptance result, not a claim of all-browser certification.

The parent used the Playwright CLI skill and inspected the recovered UI screenshot.
[Exact hashes and evidence references](published-result.json) retain provenance;
raw logs and screenshots remain local under `output/rc-1.0.0-2026-10-21/` and
`output/playwright/rc-1.0.0-2026-10-21/`. Release notes also publish the final
qualification summary. The post-publication documentation commit records these
facts only; the release tag and deployed code remain at the qualified SHA above.

No pilot kit was prepared and no external pilot started. RC publication does not
close stable 1.0 field-adoption, production-host admission or observation gates.
