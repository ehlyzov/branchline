---
status: Implemented
depends_on: []
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-01
changelog:
  - date: 2026-02-01
    change: "Migrated from research/optional_semicolons.md and added YAML front matter."
---
# Optional semicolon rollout notes


## Status (as of 2026-01-31)
- Stage: Implemented.
- Parser accepts optional semicolons via newline/`}`/EOF boundaries; tests cover newline-terminated statements.
- Next: no pending items recorded.


## Current behaviour
- `optionalSemicolon()` guards all declaration and statement parsers; it consumes `;` when present, otherwise treats a line break or `}`/`EOF` as a valid boundary, and emits a targeted error if none apply.【F:interpreter/src/commonMain/kotlin/Parser.kt†L99-L365】【F:interpreter/src/commonMain/kotlin/Parser.kt†L887-L910】
- Parser tests assert that block statements, expression statements, and even `SOURCE` declarations parse without trailing semicolons, confirming newline handling across the surface.【F:interpreter/src/jvmTest/kotlin/v2/ParserTests.kt†L71-L118】

## Follow-ups
- Refresh language/reference docs and playground examples to show semicolons as optional rather than mandatory.
- Keep linting/examples consistent: prefer newline termination in docs while allowing `;` for dense one-liners.
