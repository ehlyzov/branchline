# v0.11.0-alpha.1 independent release acceptance

The user authorized implementation by GPT-6.1 Sol agents, parent verification,
commit/push, publication and public playground verification on 2026-10-17.

## Candidate result

- 1,236 tests passed, one existing conformance skip; zero failures/errors across
  JVM/Node VM, interpreter, conformance, CLI plus 62 Chrome interpreter tests.
  Per-suite counts: `test-counts.json`.
- Clean rebuild followed by the successful completion gate: `acceptance-gate.log`.
  Initial runs honestly retained as `initial-gate.log` and `clean-gate.log`.
- Fixed the JS archive's overlapping Node/browser output directories and dependency
  declaration. Added runtime/examples/config inputs to playground asset generation.
- Local prerequisites required `npm ci` in playground and updating the ignored
  Kotlin Yarn lock with the same CHROME_BIN environment used for browser testing.
  A first update without CHROME_BIN omitted Karma dependencies and was insufficient.
- MkDocs (the actual Pages generator) and Diplodoc both built successfully.
  Real Chrome local-site check loaded editor/catalog and ran customer-profile to
  the expected Ada Lovelace/London/standard output. A theme-owned GitHub latest
  stable release request returned404 because the repository has prereleases only;
  interpreter/editor resources loaded successfully. Public-site check still required.
- JVM and extracted Node packages report v0.11.0-alpha.1; npm metadata is canonical
  0.11.0-alpha.1 and JVM entrypoint class major65 (Java21). Source/current compiled
  and crossed JVM/Node compiled execution passed (`artifact-smoke.log`).
- Six independent simple/edit/filter-projection outputs matched hand-written
  expectations on JVM/Node (`acceptance-cases.json`, `independent-cli-results.json`).
  An initial authoring probe used an unparenthesized object lambda, which parses as
  a block; corrected the fixture to documented comprehension syntax. No product
  behavior or expected result was changed to accommodate that authoring mistake.
- Kotlin consumer compilation: ordinary API succeeds, unmarked preview fails with
  the expected diagnostic, explicit opt-in succeeds (`optin-acceptance.json`).
  Initial diagnostic oracle assumed uppercase; compiler lowercases that message.
- Legacy numeric Java probe now prints the same result `[7, 11]`, observed `[7]`,
  final x=11 for ordinary/experimental. Before fix it printed `[7, 7]`, `[null]`, x=7.
  The first new snapshot test used unserializable NOP; serializable JUMP filler
  repaired the fixture without changing the runtime fix or source observation PC.
- Genuine old-writer fixture, old-reader rejection and format migration policy:
  `compatibility.md`. Workflow/source/version static checks: `packaging.md`.
- Cross-reviews found no remaining blocking release defect. Release mutations
  are serialized even if manual dispatch uses an empty versus explicit tag.

## Published surface

Core fixes ship together with explicitly unstable experimental VM APIs. Ordinary
VM execution remains default. JVM generated-loop compilers and pinned ASM9.9 are
included, not hidden from the artifact dependency surface. Kotlin opt-in is
compiler-enforced; Java uses the documented contract. Selective JSON remains an
explicitly named prototype, with its measured dense-input regression documented.
There is no production-maturity or universal performance/authoring advantage claim.

## Evidence and publication protocol

Historical experiment text/raw numeric results are included. Large raw transcript,
frozen runtime archives and binary profiling recordings remain local; they are not
required at runtime and are not included in the source release commit. Unrelated
user deep-research reports are untouched and excluded.

Bind release tag/assets/public `/release-metadata.json` to the candidate commit;
record remote CI, asset checksums and fresh public browser acceptance in the GitHub
release notes/attached acceptance report. Local green checks alone do not prove
publication or public editor operation. No benchmark timing was rerun for this
release: only observer safety and annotation/packaging/format behavior changed;
previous performance conclusions remain scoped to their frozen candidates.
