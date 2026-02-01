---
status: Implemented
depends_on: []
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-01
changelog:
  - date: 2026-02-01
    change: "Migrated from research/case.md and added YAML front matter."
  - date: 2026-02-01
    change: "Marked implemented and aligned to guard-only CASE syntax and existing tests."
---
# CASE/WHEN expression

## Status (as of 2026-02-01)
- Stage: Implemented (guard-only form).
- Subject form (CASE expr AS name) is not supported; use an explicit LET before CASE instead.
- Parser, lowering, tests, and playground examples are in place.

## Supported syntax (guard-only)
```
LET status = CASE {
    WHEN totals.tests == 0 THEN "error"
    WHEN failed == 0 THEN "passing"
    ELSE "failing"
};
```

Notes:
- `CASE` is an expression.
- `ELSE` is required (no implicit nulls).
- Evaluation is top-to-bottom; first match wins.

## Not supported yet

Subject form is not implemented:
```
// NOT supported
CASE totals AS t { WHEN t.tests == 0 THEN "error" ELSE "ok" }
```
Use this instead:
```
LET t = totals;
LET status = CASE {
    WHEN t.tests == 0 THEN "error"
    ELSE "ok"
};
```

## Semantics

`CASE` lowers to a nested `IF/ELSE` chain. The interpreter handles this via `CaseLowering` and `Exec.handleCase`.

## References
- Parser: `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/Parser.kt`
- Lowering: `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/CaseLowering.kt`
- Exec handling: `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/ir/Exec.kt`
- Conformance: `conformance-tests/src/commonTest/kotlin/io/github/ehlyzov/branchline/conformance/ConformCaseTest.kt`
- Docs: `docs/language/expressions.md`, `docs/language/grammar.md`
- Playground example: `playground/examples/stdlib-case-basic.json`
