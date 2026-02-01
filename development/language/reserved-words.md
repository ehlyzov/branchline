---
status: Implemented
depends_on: []
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-01
changelog:
  - date: 2026-02-01
    change: "Migrated from research/reserved.md and added YAML front matter."
---
# Reserved Words and Contextual Keywords


## Status (as of 2026-01-31)
- Stage: Implemented.
- Lexer/parser/grammar/tests/playground updated; keyword rules documented.
- Next: none unless new keywords are added.

## Goal
Allow keyword-looking names in identifier positions when unambiguous (for example,
`LET output = 1`), while keeping truly ambiguous words reserved.

## Decisions
- Hard-reserved (require backticks when used as names):
  `IF`, `THEN`, `ELSE`, `CASE`, `WHEN`, `FOR`, `EACH`, `IN`, `WHERE`,
  `TRY`, `CATCH`, `AWAIT`, `SUSPEND`, `TRUE`, `FALSE`, `NULL`.
- Soft keywords (accepted as names without backticks when a name is expected):
  `ABORT`, `APPEND`, `AS`, `BACKOFF`, `BUFFER`, `CALL`, `ENUM`, `FOREACH`,
  `FUNC`, `INIT`, `LET`, `MANY`, `MODIFY`, `OUTPUT`, `RETRY`, `RETURN`,
  `SET`, `SHARED`, `SINGLE`, `SOURCE`, `STREAM`, `THROW`, `TIMES`, `TO`,
  `TRANSFORM`, `TYPE`, `UNION`, `USING`.
- Statement-start disambiguation stays the same: if a statement starts with a
  keyword like `OUTPUT`, it is parsed as that statement. To start a statement
  with a name that collides with a statement keyword, use backticks or wrap the
  expression.

## Lexer changes
- Added backtick identifiers: `` `name` `` lexes as `IDENTIFIER`.
- Backtick identifiers accept any characters except a backtick or newline.
- Empty backticks are rejected.

## Parser changes
- Added name helpers (`checkName`, `matchName`, `consumeName`) so identifier
  positions accept `IDENTIFIER` plus soft keywords.
- `parsePrimary` treats soft keywords as `IdentifierExpr`.
- Removed the special-case that required `APPEND` to be followed by `(`.
- Path segments, object keys, record fields, parameter names, and other name
  positions accept soft keywords.
- `enum` in type expressions is treated as an enum literal only when followed
  by `{`; otherwise it can be a named type.

## Grammar updates
- Canonical grammar updated in
  `interpreter/src/jvmTest/resources/io/github/ehlyzov/branchline/ebnf.txt`.
- Docs updated in `docs/language/grammar.md` and `docs/language/lexical.md`.
- Added `name` and `softKeyword` non-terminals in the EBNF.

## Tests
- Added conformance coverage in
  `conformance-tests/src/commonTest/kotlin/io/github/ehlyzov/branchline/conformance/ConformIdentifierNamesTest.kt`.
  This validates `LET output = 1`, `LET enum = 2`, and backtick escapes like
  ``LET `if` = 3``, plus object keys and path access.

## Playground demo
- Added `playground/examples/keyword-identifiers.json` demonstrating soft
  keywords as identifiers and backtick escapes.

## Notes and edge cases
- `OUTPUT` at statement start always parses as an `OutputStmt`. If you need a
  shared write like `output[...] = ...`, write `` `output`[...] = ... ``.
- For hard-reserved words (`if`, `for`, `when`, etc.), use backticks to use them
  as names: `` `if` ``.
- Backtick identifiers are the escape hatch for any remaining ambiguities.
