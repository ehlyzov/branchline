# Repository Guidelines

## Coordination & Safety (multi-agent workflow)
- Assume multiple Codex tabs and local edits are active at the same time.
- Always run `git status -sb` before editing; if a file has changes you did not make, do not edit it and ask first.
- If you notice unexpected changes while working, stop immediately and ask how to proceed.
- Never run destructive git commands (reset/checkout/restore/hard) unless explicitly instructed.
- Never edit `.env` or environment variable files.

## Project Structure & Priorities
- `cli/`: CLI tools and scripts.
- `interpreter/`: parser, IR, and runtime execution for the language.
- `vm/`: VM execution pipeline and examples.
- `conformance-tests/`: cross-platform test suite (commonTest is the source of truth for behavior).
- `docs/language/`: language documentation.
- `playground/`: online demo assets; **all new language features must include a demo in `playground/examples/`**.
- `research/`: design notes and proposals.
- `test-fixtures/`: shared test data.

Language design priorities:
- Prefer desugaring over VM/interpreter complexity when possible.
- Update the canonical grammar in `interpreter/src/jvmTest/resources/v2/ebnf.txt` for syntax changes.
- Add conformance tests in `conformance-tests/src/commonTest` for new behavior.
- Update `docs/language/` and add a playground demo for new features.

## Build, Test, and Development Commands
- Use `./gradlew` only; avoid system Gradle binaries.
- Full build: `./gradlew clean build`.
- Focused tasks: `./gradlew :interpreter:jvmTest`, `./gradlew :interpreter:jsTest`, `./gradlew :conformance-tests:jvmTest`, `./gradlew :conformance-tests:jsTest`.

## Coding Style & Naming Conventions
- Use four-space indents, explicit visibility, and trailing commas where Kotlin allows them.
- Import only what you use; no wildcard imports.
- Do not use local functions; lift helpers to private top-level declarations or companion objects.
- Do not use `runCatching` in production code; prefer explicit error handling.

## Testing Guidelines
- Tests live in each module’s `src/test/kotlin`; name unit tests `*Test` and integration scenarios `*IT`.
- For multiplatform behavior, use `conformance-tests/src/commonTest`.

## Commit & PR Guidelines (when asked)
- Keep commits atomic; include only files you touched.
- Never amend commits unless explicitly requested.
