---
status: Implemented; parent acceptance pending
last_updated: 2026-10-17
---

# Playground body diagnostic coordinates

Parent browser acceptance found `OUTPUT { broken: }` reported at line 2 although
the editor has one line. Read-only execution of the built facade confirms that
the implicit wrapper adds one header line and Kotlin trimIndent removes the four
inserted body spaces. body.trim() also removes leading blank lines and leading
indentation on the first content line.

Fix the worker mapping to this actual layout for Run and Inspect, including shared
declaration offsets. Keep full-program coordinates unchanged. Replace only
wrapper-prefixed parser diagnostic text with its concise error summary so raw
wrapped coordinates/snippets cannot contradict the corrected marker/span.
Add targeted JS checks using actual built facade errors and explicit expected
editor coordinates; include the script in the existing npm test gate.

This is a worker-only correction. No language grammar/runtime/layout change and
no service contour trigger. Parent owns final asset/browser acceptance.

## Verification

`npm test` passes, including the new `test:wrapper-diagnostics` script. It loads
the actual built facade and checks seven explicit body error coordinates for Run
and Inspect, with no shared declarations, one declaration and two declarations.
Coverage includes leading whitespace/blank lines, later-line indentation, CRLF,
multiline bodies, missing closing syntax and full-program preservation.

The original one-line repro now maps to line 1, column 18. Run and Inspect replace
raw wrapper-prefixed parser text with the concise summary; full-program diagnostic
text is unchanged. No Kotlin source changed in this slice, so the previously
passing JVM/Node gates were not repeated. Final assets/browser acceptance pending.
