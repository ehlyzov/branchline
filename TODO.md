# TODO

- Investigate why top-level `FUNC` declarations in `cli/scripts/*.bl` fail under the current CLI parser. Attempting helpers such as `FUNC toNumber(value) { ... }` led to errors like `Undefined identifier 'value'` and "Expect THEN after IF condition" even when the syntax matched the grammar. Until parameter recognition is fixed (likely an indentation/off-side parsing bug in the JS runtime), the Branchline scripts inline their logic instead of using reusable functions.
- Track the JS runtime numeric quirks observed while porting the JUnit summary flow:
  - `NUMBER("2")` returns a `BLBigDec`, and mixing that with plain integers in expressions (`0 + NUMBER("2")`) crashes with `ClassCastException`.
  - Emitting a raw `BLBigDec` (e.g., `OUTPUT NUMBER("2")`) is rejected (`Expected object or list of objects in OUTPUT, got BLBigDec`).
  - Replacing arithmetic with host-side parsing (Node `Number.parseInt`) is the current workaround; the CLI should probably normalise numeric conversions for mixed-precision math on the JS target.
