# Independent hidden-failure review

Reviewed the completed `scored-002` cohort: 270 unique task/language/repetition cells. All 270 passed public examples; 267 passed hidden tests. This audit inspected every stored first/final hidden outcome, the sealed public specifications and hidden fixtures. It did not execute or repair generated programs or change the oracle.

## Oracle integrity

The public tasks, hidden fixtures and builder match their SHA-256 entries in `seal.json`. All 205 hidden expected values were recomputed with the independent builder references and matched; its 30 hand-written assertions passed. This verifies the stored oracle against its references; the natural-language check below separately establishes whether the observed failure is specified.

## Complete hidden-failure inventory

| Task | Language | Repetitions | Failing hidden case | Classification |
| --- | --- | --- | --- | --- |
| A08 — Constrain line quantity bounds | JSONata | 1, 2, 3 | Zero-based case 4, first array element | Real specification violation; no oracle ambiguity |

There are no other hidden-failing task/language combinations, including stored initial submissions. The same mismatch appears in all three JSONata repetitions, each on submission 0, which is also the selected final submission.

Representative input:

```json
{"lines":[null,{}, {"quantity":null},{"quantity":true},{"quantity":1001}]}
```

Expected:

```json
{"valid":false,"errors":["lines.0.object","lines.1.quantity.required","lines.2.quantity.required","lines.3.quantity.integer","lines.4.quantity.range"]}
```

Actual in all three repetitions:

```json
{"valid":false,"errors":["lines.0.quantity.required","lines.1.quantity.required","lines.2.quantity.required","lines.3.quantity.integer","lines.4.quantity.range"]}
```

A08 explicitly says to inspect each element and emit `lines.i.object` for a non-object, **else** inspect its quantity; it also permits arbitrary JSON kinds and specifies zero-based indexes and one error per element. JSON null is not an object. Therefore the first error must be `lines.0.object`. A missing quantity in an actual object is a separate branch, correctly exercised by element 1 (`{}`). The other four actual errors match the oracle. This is a type/validation-order error rather than an unspecified absent/null interpretation.

## Scope and limits

Final hidden success counts are Branchline 90/90, Kotlin 90/90, and JSONata 87/90. Twenty-nine of thirty tasks passed all nine cells. These high success rates create a ceiling for distinguishing correctness on this bounded task panel; this review makes no broader language, maintenance-productivity or timing claim. Parent analysis owns statistics, budget validity and isolation acceptance.

All-results SHA-256: `d6be5615d84171d7624b358baa5c7ba0a47ccfc6d05386b94d97b253cab8c9f2`.
Seal SHA-256 at review: `2af8e89ae44e2d1c2cb70116d0d6d73c20e18d4f84d7c25de124187ee187dd6a`.

## Bounded post-hoc null/type diagnostics

Three generic JSONata probes were run separately through the frozen evaluator after scoring; source, input and captured results are under `/tmp/posthoc/`. Each probe used an intentionally empty expected object to capture the actual output, so its evaluator mismatch flag is expected and is not another scored failure.

- `direct-types`: both `$type(null)` and `$type(value)` with input `{"value":null}` return `"null"`; `$type(null) = "object"` is false and `$type(null) != "object"` is true.
- `mapped-types`: mapping `[null,{},1]` gives kinds `null`, `object`, `number`; the corresponding `notObject` predicates are true, false, true. Thus a null literal array element is retained and correctly typed in this generic mapping.
- `conditional`: both a literal null and input null select the `non-object` branch of a `$type(...) != "object"` conditional; input null also gives `$type(value) = "null"` true and `$exists(value)` true.

These observations do **not** demonstrate a general Dashjoin null/type defect. The scored A08 source itself contains `$type($line) != "object"`, so the observed output alone must not be attributed to an author omitting that check. The cause within the complete expression remains unlocalized by these bounded probes; the scored output still violates the explicit task contract. No original JavaScript JSONata implementation was tested, and no cross-implementation behavior claim is made. Scored programs, results and oracle were unchanged.
