# TODO

- Bundle Node dependencies (e.g., `fast-xml-parser`) alongside the packaged CLI so consumers do not have to run `npm install` manually.
- Track the JS runtime numeric quirks observed while porting the JUnit summary flow:
  - `NUMBER("2")` returns a `BLBigDec`, and mixing that with plain integers in expressions (`0 + NUMBER("2")`) crashes with `ClassCastException`.
  - Emitting a raw `BLBigDec` (e.g., `OUTPUT NUMBER("2")`) is rejected (`Expected object or list of objects in OUTPUT, got BLBigDec`).
  - Replacing arithmetic with host-side parsing (Node `Number.parseInt`) is the current workaround; the CLI should probably normalise numeric conversions for mixed-precision math on the JS target.
