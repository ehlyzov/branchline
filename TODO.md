# TODO

- Investigate why top-level `FUNC` declarations in `cli/scripts/*.bl` fail under the current CLI parser. Attempting helpers such as `FUNC toNumber(value) { ... }` led to errors like `Undefined identifier 'value'` and "Expect THEN after IF condition" even when the syntax matched the grammar. Until parameter recognition is fixed (likely an indentation/off-side parsing bug in the JS runtime), the Branchline scripts inline their logic instead of using reusable functions.
- Track the JS runtime numeric quirks observed while porting the JUnit summary flow (documented in `research/cli.md#js-runtime-numeric-quirks-junit-summary-flow`).
