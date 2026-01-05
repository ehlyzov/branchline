# CASE/WHEN expression plan

## Goal
Provide a readable, beginner-friendly alternative to nested `IF/ELSE IF/ELSE` for expression selection.

## Proposed syntax
### Guard-only form (primary)
```
LET status = CASE {
    WHEN totals.tests == 0 THEN "error"
    WHEN failed == 0 THEN "passing"
    ELSE "failing"
};
```

### Subject form (optional but useful)
```
LET status = CASE totals AS t {
    WHEN t.tests == 0 THEN "error"
    WHEN t.failed == 0 THEN "passing"
    ELSE "failing"
};
```

Notes:
- `CASE` is an expression.
- `ELSE` is required (avoid implicit nulls).
- Evaluation is top-to-bottom; first match wins.
- Conditions are normal boolean expressions (no new pattern syntax yet).

## Semantics (desugaring)
The `CASE` expression is equivalent to a nested `IF/ELSE` expression chain.

Guard-only form:
```
CASE {
  WHEN c1 THEN v1
  WHEN c2 THEN v2
  ELSE v3
}
```

Desugars to:
```
IF c1 THEN v1
ELSE IF c2 THEN v2
ELSE v3
```

Subject form:
```
CASE expr AS name {
  WHEN cond1 THEN v1
  WHEN cond2 THEN v2
  ELSE v3
}
```

Desugars to:
```
LET name = expr;
IF cond1 THEN v1
ELSE IF cond2 THEN v2
ELSE v3
```

## Grammar updates
Extend expression grammar with `caseExpr`.

Sketch:
```
primary ::= ... | caseExpr
caseExpr ::= **CASE** caseSubject? "{" caseWhen+ caseElse "}"
caseSubject ::= expression ( **AS** IDENTIFIER )?
caseWhen ::= **WHEN** expression **THEN** expression
caseElse ::= **ELSE** expression
```

Notes:
- `caseExpr` should parse at the same precedence as primary expressions.
- `caseSubject` is optional; if present without `AS`, use an implicit name in the lowering phase.

## Compiler/IR implementation plan
1) Parser: add AST node `CaseExpr` with fields:
   - optional `subject` (expression + optional alias identifier)
   - list of `when` pairs (condition expression + value expression)
   - `elseExpr`
2) Desugar pass (parser or IR lowering):
   - If subject present, synthesize a `LET` binding in a block expression context.
   - Lower into nested `IfExpr` (or block with `LET` + `IF`).
3) Interpreter/VM: no new runtime support if lowered to existing constructs.

## Docs updates
- `docs/language/expressions.md`: add `CASE` section with syntax and examples.
- `docs/language/grammar.md`: include `caseExpr` in grammar snapshot.
- `interpreter/src/jvmTest/resources/v2/ebnf.txt`: update canonical grammar to keep tests aligned.
- Optional: add a short mention in `docs/language/statements.md` under “Quick patterns”.

## Examples
Add playground examples:
- `playground/examples/stdlib-case-basic.json` (simple numeric status mapping).
- `playground/examples/stdlib-case-subject.json` (subject alias form).
Update the playground index if it maintains a list.

## Script rewrites (post-implementation)
Update CLI scripts to use `CASE` for readability:

`.github/scripts/junit-summary.bl`:
```
LET status = CASE {
    WHEN totals.tests == 0 THEN "error"
    WHEN failed == 0 THEN "passing"
    ELSE "failing"
};
```

`.github/scripts/junit-file-summary.bl`:
```
LET status = CASE {
    WHEN totalTests == 0 THEN "error"
    WHEN failed == 0 THEN "passing"
    ELSE "failing"
};
```

## Conformance tests (commonTest)
Add `ConformCaseTest` under `conformance-tests/src/commonTest/kotlin/v2/conformance`:
- `case_guard_ordering`: verifies first-match behavior.
- `case_requires_else`: parse error when `ELSE` missing.
- `case_subject_alias`: subject bound with `AS name` works.
- `case_nested_exprs`: `CASE` inside expressions (object fields, list literals).

## Migration/compat
- No breaking changes; `CASE` is additive.
- Avoid keyword conflict: ensure `CASE`, `WHEN`, `THEN`, `ELSE` are reserved in lexer.

## Follow-up (optional)
- Pattern matching variants: `{ WHEN { tests: 0 } THEN ... }` or array destructuring.
- Exhaustiveness checking when `CASE` is used with `TYPE` enums.
