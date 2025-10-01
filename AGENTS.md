# Repository Guidelines

## Project Structure & Module Organization
- `language/` contains domain logic; `platform/` and `platform-private/` host adapters and internal integrations.
- Execution pipelines live in `interpreter/` and `vm/`; shared fixtures stay in `test-fixtures/`.
- Documentation sources sit in `docs/` with generated assets in `docs-compiled/`; experiments belong in `research/` or `playground/`.
- Keep tests beside sources under `src/test/kotlin` and register any new module root in `settings.gradle`.

## Build, Test, and Development Commands
- Run `gradle wrapper` once, then rely on `./gradlew` for every task.
- `./gradlew build` performs a full compile plus static analysis; `./gradlew test` executes the JUnit 5 suite (`--info` helps debug flakes).
- `./gradlew detekt` enforces style; prefer scoped tasks like `./gradlew :language:compileKotlin` or `./gradlew :compiler:run` for module work.
- Avoid system Gradle binaries and keep builds reproducible inside the wrapper.

## Coding Style & Naming Conventions
- Use four-space indents, explicit visibility, and trailing commas where Kotlin allows them.
- Import only what you use; wildcard imports are disallowed. Lift helpers to private top-level declarations or companion objects—local functions are not permitted.
- Choose descriptive identifiers (e.g., `parseResult`, `JvmRuntimeConfig`) and favor immutable data in public APIs.
- Document any Detekt rule adjustments in `detekt.yml` with a short justification.

## Testing Guidelines
- Tests live in each module’s `src/test/kotlin`; name unit tests `*Test` and integration scenarios `*IT`.
- Use JUnit 5 with Kotlin test utilities and place reusable fixtures in `test-fixtures/`.
- Run `./gradlew test` before pushing; add focused regression cases for bug fixes and annotate quarantined tests with TODOs referencing owners.

## Commit & Pull Request Guidelines
- Write commit messages in imperative present (e.g., `Add parser guard`) and group related changes.
- Pull requests should summarize scope with inline path references such as `language/parser/...`, list executed commands (e.g., `./gradlew test`), and link Jira tickets or issues.
- Include screenshots for UI-affecting assets, confirm wrapper-based builds, and call out any follow-up tasks explicitly.

## Agent Workflow Tips
- Respect pre-existing worktree changes and never revert edits you did not author.
- Prefer clear, maintainable solutions over clever shortcuts; add concise comments only where logic is non-obvious.
- When uncertain about next steps, leave TODOs with handles and file references to keep progress traceable.
