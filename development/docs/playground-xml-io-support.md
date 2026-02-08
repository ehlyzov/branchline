---
status: Implemented
depends_on: ['language/xml-mapping-contract', 'language/xml-output-ordering', 'language/xml-namespaces']
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-07
changelog:
  - date: 2026-02-07
    change: "Created proposal and implementation plan for playground XML input/output support."
  - date: 2026-02-07
    change: "Implemented XML input parity in playground worker and added XML/XML-compact output formats in UI and backend."
---
# Playground XML I/O Support

## Problem Statement
The playground had partial XML support:
- XML input parsing used a simplified browser-side mapping that did not match CLI behavior for mixed content and namespaces.
- Output format options exposed only JSON variants.
- Playground backend formatting only emitted JSON, even when XML behavior was expected.

## Scope
- Add end-to-end XML output support in playground (`xml`, `xml-compact`).
- Align playground XML input mapping with CLI mapping semantics.
- Keep worker response contract stable (`outputJson` field name remains unchanged).
- Add a dedicated playground XML example.

## Non-goals
- No automatic JSON/XML editor content conversion when toggling input format.
- No CLI flag or behavior changes.
- No changes to contract validation modes or trace payload formats.

## API and UI Changes
- `playground/src/playground.tsx`
  - Extended output format union to include `xml` and `xml-compact`.
  - Added output dropdown entries for `XML` and `XML (compact)`.
  - Updated output placeholder text to be format-agnostic.

- `playground/src/worker.ts`
  - Extended worker `OutputFormat` type with XML formats.
  - Replaced simplified XML parsing path with preserve-order XML mapping equivalent to CLI behavior:
    - attributes via `@name`
    - namespace declarations under `@xmlns` (`$` for default)
    - pure text via `$` and `#text`
    - mixed text segments via `$1`, `$2`, ...
    - repeated elements as arrays

- `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/playground/PlaygroundFacade.kt`
  - Added `PlaygroundOutputFormat.XML` and `PlaygroundOutputFormat.XML_COMPACT`.
  - Added `xml`, `xml-compact`, `xml_compact` parsing in output format decoding.
  - Wired XML output rendering through shared XML serializer.

## Shared Serializer Refactor
- Added shared XML serializer implementation:
  - `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/xml/XmlOutput.kt`
- Preserved CLI compatibility by delegating existing CLI entrypoint:
  - `cli/src/commonMain/kotlin/io/github/ehlyzov/branchline/cli/XmlOutput.kt`

This keeps existing imports and behavior stable while allowing the interpreter/playground to reuse the same formatter.

## Playground Example
- Added:
  - `playground/examples/xml-input-output-roundtrip.json`
- Demonstrates raw XML input + XML output mode in a single runnable scenario.

## Compatibility Notes
- Worker response payload still uses `outputJson` for backward compatibility with existing UI wiring.
- XML output keeps strict single-root behavior and fails with explicit errors when output shape is invalid.

## Validation
Executed after implementation:
- `./gradlew --no-daemon :interpreter:jvmTest`
- `./gradlew --no-daemon :interpreter:jsTest`
- `./gradlew --no-daemon :conformance-tests:jvmTest`
- `./gradlew --no-daemon :conformance-tests:jsTest`
- `./gradlew --no-daemon :playgroundBuildAssets`

All commands completed successfully.
