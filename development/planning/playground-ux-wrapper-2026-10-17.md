---
status: Implemented; parent acceptance pending
last_updated: 2026-10-17
---

# Playground implicit transform detection

Bounded follow-up authorized by the parent UX review. The built JS facade fails
valid bodies containing `OUTPUT { value: "TRANSFORM" }` or a comment mentioning
TRANSFORM because raw regex detection treats those as full declarations.

Use the existing Branchline lexer to distinguish actual TRANSFORM tokens from
comments, strings and quoted identifiers. Share the facade decision with worker
error-location adjustment. Preserve the existing error path for lexically invalid
source and keep full programs supported. Add focused common facade regressions
and run JVM facade tests plus the unfiltered interpreter Node suite, checking
actual test counts. The parent owns final asset build and browser acceptance.

No grammar changes, new scanner, dependency changes or broad parser work. This
repairs playground wrapping only; no service contour trigger is expected.

The parent additionally authorized clearing a rejected worker facade-loading
promise so a temporary bundle-fetch failure can be retried by Run. Successfully
loaded dependency scripts remain cached; no component/lifecycle edits are needed.

## Verification

With `CHROME_BIN=/Applications/Google Chrome.app/Contents/MacOS/Google Chrome`:

- `./gradlew :interpreter:jvmTest --tests '*PlaygroundFacadeTest' --console=plain`
  passed: JUnit XML reports 16 tests, zero failures/errors/skips.
- `./gradlew :interpreter:jsNodeTest --console=plain` passed unfiltered: 64 tests
  across eight suites, zero failures/errors/skips; facade suite has 16 tests.
- A Node VM simulation of the actual worker loader source rejected its first
  bundle fetch, succeeded on the next load and cached the successful facade.

First-token classification uses the parser's declaration starters
TRANSFORM/SHARED/FUNC/TYPE. Tests cover strings, line/block comments, backtick
identifiers, `input.TRANSFORM`, object key TRANSFORM, a TYPE-prefixed full program,
normal full programs and malformed declarations/characters. No language grammar
or general interpreter behavior changed.

An initial new test used unsupported TYPE alias syntax; corrected the fixture to
the existing ENUM grammar before the final passing gates. Initial execution
without Chrome env triggered ignored local Yarn-lock drift; rerun with the
canonical Chrome env and `kotlinUpgradeYarnLock` restored the established setup.

Final frontend build and actual browser verification remain with the parent.
