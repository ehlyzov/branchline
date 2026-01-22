---
title: Strings Standard Library
---

# Strings

Text processing helpers.

## Conversions
- `STRING(x)` → string representation or `null` for `null`.
- `NUMBER(x)` → parses numbers/booleans/strings into the float (`F`) or integer (`I`) domains. Use `DEC(...)` for BigInt/BigDec parsing; invalid strings error, and `null` stays `null`.
- `BOOLEAN(x)` → truthiness: `false` for `null`, empty string, or zero; `true` otherwise.
- `INT(x)` → strict integer conversion for numbers/booleans/strings; errors on fractional values; returns `null` for `null`.
- `PARSE_INT(x[, default])` → tolerant integer parsing for strings; uses digits only and returns `default` (or `0`) on `null`/no digits.

## Text operations
- `SUBSTRING(str, start[, len])` → substring with safe bounds; errors if start > length.
- `CONTAINS(str, substr)` → boolean containment.
- `MATCH(str, pattern)` → regex matches as a list.
- `REPLACE(str, pattern, repl)` → regex replace.
- `SPLIT(str, sep)` → list of parts.
- `JOIN(list, sep)` → concatenates stringified elements with a separator.
- `UPPER(str)` / `LOWER(str)` / `TRIM(str)` → casing and whitespace helpers.

## Formatting
- `FORMAT(template, args)` → interpolate placeholders in `template`.
  - Use `{name}` for object fields and `{0}` for list indexes.
  - Escape braces with `{{` and `}}`.
  - Unknown placeholders are left as-is.

## Examples
- [stdlib-strings-casts](../playground.md?example=stdlib-strings-casts)
- [stdlib-strings-text](../playground.md?example=stdlib-strings-text)
- [stdlib-strings-format](../playground.md?example=stdlib-strings-format)
