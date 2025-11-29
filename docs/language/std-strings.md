---
title: Strings Standard Library
---

# Strings

Text processing helpers.

## Conversions
- `STRING(x)` → string representation or `null` for `null`.
- `NUMBER(x)` → parses numbers/booleans/strings to numeric; errors on invalid strings; returns `null` for `null`.
- `BOOLEAN(x)` → truthiness: `false` for `null`, empty string, or zero; `true` otherwise.

Run it: [STRING/NUMBER/BOOLEAN example](../playground.md?example=stdlib-strings-casts).

## Text operations
- `SUBSTRING(str, start[, len])` → substring with safe bounds; errors if start > length.
- `CONTAINS(str, substr)` → boolean containment.
- `MATCH(str, pattern)` → regex matches as a list.
- `REPLACE(str, pattern, repl)` → regex replace.
- `SPLIT(str, sep)` → list of parts.
- `JOIN(list, sep)` → concatenates stringified elements with a separator.
- `UPPER(str)` / `LOWER(str)` / `TRIM(str)` → casing and whitespace helpers.

Run it: [Text helpers example](../playground.md?example=stdlib-strings-text).
