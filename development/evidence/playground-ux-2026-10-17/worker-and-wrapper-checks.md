# Worker input and implicit-wrapper checks

Date: 2026-10-17. Scope: bounded XML validation, implicit transform detection and
recovery from a rejected interpreter initialization. Parent owns screenshots,
UI acceptance and final assets build.

## Confirmed before changes

- Installed fast-xml-parser accepted mismatched tags, two roots and plain text;
  existing normalization kept the first root or supplied an empty object.
- Its XMLValidator also accepts `<first/><second/>` and `<first/>text`; validator
  success alone does not establish a single complete document.
- The built facade successfully ran `OUTPUT { value: "ok" }` but failed
  `OUTPUT { value: "TRANSFORM" }` and a valid body preceded by a comment mentioning
  TRANSFORM with `Unexpected token at top-level`.
- Worker initialization retained a rejected facadePromise, making a transient
  bundle-load failure permanent until worker/page replacement.

## Accepted implementation evidence

`node playground/scripts/test-xml-input.mjs` passes. Explicit expected mappings
cover empty elements, attributes, repeated elements, namespace maps, mixed text,
CDATA, XML declaration/PI, BOM and internal DOCTYPE entities. Negative inputs cover
empty/whitespace/plain text, no root, mismatched/unclosed tags, boolean attributes,
multiple ordinary/self-closing roots and text outside the root.

The helper uses the installed XMLValidator and a synthetic parser-only container
to expose top-level text and roots that the parser otherwise drops. It preserves
the original normalization functions. A read-only comparison against HEAD's old
converter confirmed exact equality for the checked-in XML example, BOM/declaration
and DOCTYPE/entity fixtures. Existing numeric entity behavior is retained:
`&#65;` stays literal with the pinned settings. This is bounded validation evidence,
not a claim of full XML standards compliance or full CLI XML parity.

The facade now classifies the first lexed declaration starter instead of searching
raw source for a word. Worker span adjustment calls that same facade decision.
Comments, strings, quoted identifiers, keyword object keys and member access no
longer select the full-program path. Lexically invalid input retains its prior
lexing/error path.

Final commands used the installed Chrome path in CHROME_BIN:

| Gate | Observed JUnit XML |
| --- | --- |
| `./gradlew :interpreter:jvmTest --tests '*PlaygroundFacadeTest' --console=plain` | 16 tests, 0 failures/errors/skips |
| `./gradlew :interpreter:jsNodeTest --console=plain` (no filter) | 64 tests / 8 suites, 0 failures/errors/skips; facade 16 included |

A Node VM simulation executed actual worker loadFacade/loadScript source with a
stubbed first fetch failure. First load rejected, second succeeded, third reused
the successful facade without another fetch. Total fetches were eight: one failed
first dependency, then seven successful dependency/interpreter requests.

No service contour trigger fired: module topology, public language semantics,
verification entry points, dependencies and operational interfaces are unchanged.
No commits, publication or public deployment were performed by this subtask.

## Parent browser follow-up: body diagnostic positions

Parent acceptance found the one-line body `OUTPUT { broken: }` displayed at line
2. The old worker assumed three wrapper lines and four inserted body spaces.
Actual built-facade output establishes one header line; Kotlin trimIndent removes
those four body spaces, while body.trim() removes the first content line's
original margin. Worker Run and Inspect mapping now follows that observed layout,
including generated shared declarations, removed blank lines and first-line
indentation. Errors on generated closing syntax map to the editor's last content
line and end column.

`npm test` passes with the added `test:wrapper-diagnostics` script. It executes
actual built facade errors (rather than fabricated raw locations), asserting seven
body cases against independently specified editor positions. Each Inspect case
also runs without shared declarations, with one and with two declarations. It
covers whitespace, CRLF, multiline and missing-close cases; full programs and
errors without source positions remain unchanged.

The original repro is now line 1, column 18. For body errors with parser-style
prefixes, Run/Inspect retain the concise summary and remove the generated wrapper
snippet/raw coordinates. Other error text is retained. Previously passing Kotlin
gates were not repeated because this follow-up changed only worker helpers/tests
and npm wiring. Parent owns final asset rebuild and browser acceptance.
