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

### Kotlin Multiplatform test tasks
- KMP modules expose target-specific test tasks; there is no generic `:module:test`.
- Interpreter module:
  - JVM: `./gradlew :interpreter:jvmTest`
  - JS (both): `./gradlew :interpreter:jsTest`
  - JS split: `./gradlew :interpreter:jsBrowserTest` and `./gradlew :interpreter:jsNodeTest`
- Conformance module:
  - JVM: `./gradlew :conformance-tests:jvmTest`
  - JS: `./gradlew :conformance-tests:jsTest`
- Full repo: `./gradlew clean build`

Notes:
- JS filesystem and other Node APIs are only available under Node. Node-only tests are guarded to skip in browser; use `jsNodeTest` to run them.
- Ensure a JDK is available locally or use Gradle toolchains; Node is required for JS Node tests.

## Coding Style & Naming Conventions
- Use four-space indents, explicit visibility, and trailing commas where Kotlin allows them.
- Import only what you use; wildcard imports are disallowed. Lift helpers to private top-level declarations or companion objects—local functions are not permitted.
- Choose descriptive identifiers (e.g., `parseResult`, `JvmRuntimeConfig`) and favor immutable data in public APIs.
- Document any Detekt rule adjustments in `detekt.yml` with a short justification.
- Do not use `runCatching` in production code; prefer explicit error handling.

## Testing Guidelines
- Tests live in each module’s `src/test/kotlin`; name unit tests `*Test` and integration scenarios `*IT`.
- Use JUnit 5 with Kotlin test utilities and place reusable fixtures in `test-fixtures/`.
- Run `./gradlew test` before pushing; add focused regression cases for bug fixes and annotate quarantined tests with TODOs referencing owners.
- For multiplatform modules, prefer program-listing tests over IR construction to validate the full parser→IR→exec pipeline.

## Commit & Pull Request Guidelines
- Write commit messages in imperative present (e.g., `Add parser guard`) and group related changes.
- Pull requests should summarize scope with inline path references such as `language/parser/...`, list executed commands (e.g., `./gradlew test`), and link Jira tickets or issues.
- Include screenshots for UI-affecting assets, confirm wrapper-based builds, and call out any follow-up tasks explicitly.

## Agent Workflow Tips
- Respect pre-existing worktree changes and never revert edits you did not author.
- Prefer clear, maintainable solutions over clever shortcuts; add concise comments only where logic is non-obvious.
- When uncertain about next steps, leave TODOs with handles and file references to keep progress traceable.

## Git Safety and Coordination
- Delete unused or obsolete files when your changes make them irrelevant (refactors, feature removals, etc.), and revert files only when the change is yours or explicitly requested. If a git operation leaves you unsure about other agents' in-flight work, stop and coordinate instead of deleting.
- Before attempting to delete a file to resolve a local type/lint failure, stop and ask the user. Other agents are often editing adjacent files; deleting their work to silence an error is never acceptable without explicit approval.
- NEVER edit .env or any environment variable files—only the user may change them.
- Coordinate with other agents before removing their in-progress edits—don't revert or delete work you didn't author unless everyone agrees.
- Moving/renaming and restoring files is allowed.
- ABSOLUTELY NEVER run destructive git operations (e.g., git reset --hard, rm, git checkout/git restore to an older commit) unless the user gives an explicit, written instruction in this conversation. Treat these commands as catastrophic; if you are even slightly unsure, stop and ask before touching them. (When working within Cursor or Codex Web, these git limitations do not apply; use the tooling's capabilities as needed.)
- Never use git restore (or similar commands) to revert files you didn't author—coordinate with other agents instead so their in-progress work stays intact.
- Always double-check git status before any commit
- Keep commits atomic: commit only the files you touched and list each path explicitly. For tracked files run git commit -m "<scoped message>" -- path/to/file1 path/to/file2. For brand-new files, use the one-liner git restore --staged :/ && git add "path/to/file1" "path/to/file2" && git commit -m "<scoped message>" -- path/to/file1 path/to/file2.
- Quote any git paths containing brackets or parentheses (e.g., src/app/[candidate]/**) when staging or committing so the shell does not treat them as globs or subshells.
- When running git rebase, avoid opening editors—export GIT_EDITOR=: and GIT_SEQUENCE_EDITOR=: (or pass --no-edit) so the default messages are used automatically.
- Never amend commits unless you have explicit written approval in the task thread.
