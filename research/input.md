# Plan: Rename default input binding and support custom aliases

## 1. Baseline analysis
- [ ] Inventory all code paths that special-case the `row` binding today (parser/sema visibility, IR runners in `interpreter` and `vm`, helper utilities, docs/tests) so we know every site that needs to understand the new alias.
- [ ] Confirm how `SOURCE <id>;` declarations are used today and whether multiple sources appear in real programs, because the alias wiring has to handle overrides without breaking existing semantics.

## 2. Define shared alias metadata
- [ ] Introduce a single source of truth for the default input alias (e.g., `DEFAULT_INPUT_ALIAS = "in"`) in a common module that both the interpreter and VM can depend on.
- [ ] Expose a small helper that resolves the active alias from a parsed `Program` (pick the declared source name if present, otherwise fall back to the default). This keeps downstream stages from duplicating the logic.

## 3. Parser & semantic adjustments
- [ ] Allow programs to omit an explicit `SOURCE in;` declaration by making the alias implicit—treat the absence of a `SourceDecl` as using the default alias.
- [ ] Update `SemanticAnalyzer` to track the resolved alias: mark that identifier as always visible, forbid `LET` redefinitions of it, and stop reserving `in` when a different alias is configured so users can reuse the identifier.
- [ ] Add new validation to detect conflicting explicit aliases (e.g., multiple `SOURCE` declarations targeting the same binding) while preserving existing adapter parsing.

## 4. Execution environment wiring
- [ ] Extend `compileStream` (and any other runner factories such as `TransformRegistry`, `RunnerMP`, VM helpers, and playground loaders) to accept the resolved alias and seed the evaluation environment with that name instead of hard-coding `row`.
- [ ] Ensure both interpreter and VM execution paths mirror the alias map, and keep copying the incoming payload into top-level keys for backward compatibility if that behaviour is still required.
- [ ] Thread the alias through higher-level loaders (e.g., `BranchlineTransformLoader`) by extracting it from the parsed program before invoking `compileStream`.

## 5. Test coverage
- [ ] Update existing test runners to explicitly define `SOURCE row` so that current tests remain unchanged.
- [ ] Add regression tests that cover: (a) implicit default alias without any `SOURCE` declaration; (b) explicit alias override that frees `in` for user variables; (c) rejection of duplicate alias declarations if applicable.
- [ ] Verify any VM/interpreter parity or benchmark fixtures still compile after the alias change.

## 6. Documentation & migration notes
- [ ] Refresh user-facing docs and playground instructions to reference `in` as the implicit input variable and describe how to opt into a custom alias.

## 7. Rollout checks
- [ ] Re-run the standard Gradle test suite (and Detekt if relevant) to confirm no regressions. 
