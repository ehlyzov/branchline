# Optional semicolon rollout notes

> **Status:** ⏳ Proposal — parser productions still require explicit semicolons, so these notes capture the pending implementation plan.【F:interpreter/src/commonMain/kotlin/Parser.kt†L95-L198】

## Overview
We want to relax statement terminators across the Branchline parser so that semicolons become optional whenever the parser can detect a clear boundary between statements. The core idea is to reuse a single helper that:

1. Consumes an explicit `;` if it is present.
2. Treats a change in source line between `previous()` and `peek()` as an implicit boundary.
3. Falls back to permitting only structural delimiters (currently `RIGHT_BRACE` and `EOF`).
4. Emits a targeted parse error when none of the above signal a boundary.

## Parser updates
To implement the behaviour we need to replace every `consume(TokenType.SEMICOLON, …)` at the end of a declaration or statement production with a call to `optionalSemicolon()`. This will affect:

- `parseSource`
- `parseShared`
- `parseFuncDecl`
- `parseTypeDecl`
- `parseStatement` (expression statements)
- `parseLet`
- `parseAppend`
- `parseSet`
- `parseReturn`
- `parseAbort`

The new helper should live near the other parser utilities:

```diff
+    private fun optionalSemicolon() {
+        if (match(TokenType.SEMICOLON)) return
+        val next = peek()
+        if (next.line != previous().line) return
+        if (isStatementBoundary(next.type)) return
+        error(next, "Expect ';' or newline between statements")
+    }
+
+    private fun isStatementBoundary(type: TokenType): Boolean {
+        return when (type) {
+            TokenType.EOF,
+            TokenType.RIGHT_BRACE -> true
+            else -> false
+        }
+    }
```

This helper would replace the current keyword-heavy semicolon enforcement with a minimalist structural guard. Until the change lands the parser still requires explicit semicolons after each statement.

## Test coverage
Once the helper lands, add parser tests that exercise both declaration- and block-level flows without semicolons:

```diff
+    @Test
+    fun `statements in block do not require semicolons`() { … }
+
+    @Test
+    fun `expression statements split by newline parse`() { … }
+
-    fun `missing semicolon throws`() { … }
+    fun `source without semicolon parses`() { … }
```

Existing fixtures will need rewrites to drop trailing semicolons wherever the grammar now allows it, covering `SOURCE`, `SHARED`, `FUNC`, `TYPE`, and block statements.
## Rollout checklist
- [ ] Update parser productions to call `optionalSemicolon()`.
- [ ] Regenerate fixtures/tests to cover newline-terminated statements.
- [ ] Run `./gradlew test --console=plain` to confirm the grammar updates.
- [ ] Capture before/after examples in the language reference.
