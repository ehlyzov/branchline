# Branchline JVM reference

Authoring surface: a complete Branchline source file containing exactly one transform named `Main`, as required by this evaluator. The JSON document is available as `input`. Emit one object with `OUTPUT`. Use newline-separated statements; semicolons are unnecessary. Examples below are generic language demonstrations, not a complete problem solution.

```branchline
TRANSFORM Main {
    LET label = LOWER(TRIM(input.label ?? ""))
    OUTPUT { label: label }
}
```

## Values, access, and defaults

Literals: `NULL`, `TRUE`, `FALSE`, numbers, double-quoted strings, `[]`, `{}`. Object fields use `{code: "q", amount: 3}` or quoted names. Arrays preserve nesting and cardinality: `[]`, `[7]`, `[[7]]` are different values. JSON input objects are maps and arrays are lists.

Access fields with `input.child.code`, quoted keys with `input["some-key"]`, list positions with `items[0]`. Use explicit defaults for optional containers before further access. `??` falls back on absent/null values; it retains `0`, `FALSE`, and `""`.

```branchline
LET child = input.child ?? {}
LET code = GET(child, "code", "unknown")
LET xs = input.values ?? []
LET first = CASE { WHEN LENGTH(xs) > 0 THEN xs[0] ELSE NULL }
```

`GET(object, key[, default])` accepts an object, not a list. Its default applies to an absent key only, preserving an explicitly present null. `GET(obj, "k", 8) ?? 8` also replaces null. To distinguish presence from null, test `SOME(KEYS(obj), (k) -> k == "k")`. `IS_OBJECT(x)` tests for a map. Invalid container types can error; `??` does not repair a value of the wrong type.

`LISTIFY(x)` is narrowly defined: null becomes `[]`, a list remains a list, and an object becomes `[object]`. It rejects scalar strings/numbers/booleans; use `[x]` to wrap an arbitrary scalar.

## Runtime kinds without coercion

The registered stdlib has `IS_OBJECT` and `IS_FUNCTION`, but no `TYPEOF`, `TYPE`, `IS_STRING`, `IS_NUMBER`, or `IS_ARRAY`. `STRING`, `NUMBER`, `BOOLEAN`, `INT`, and `LISTIFY` are conversions, not type predicates. In particular, a numeric-looking string is not a JSON number merely because a conversion accepts it.

For JSON values, a strict operation guarded by `TRY` can test a supported domain without coercing the original value:

```branchline
LET isText = TRY LENGTH(TRIM(input.value)) >= 0 CATCH(err) => FALSE
LET isList = TRY LENGTH(MAP(input.value, (v) -> v)) >= 0 CATCH(err) => FALSE
LET isNumeric = TRY SUM([input.value]) != NULL CATCH(err) => FALSE
```

`TRIM` strictly requires a string; `MAP` strictly requires a list; `SUM([x])` strictly requires a numeric element and rejects null, booleans, strings, lists, and objects. These are ordinary expressions, so they may be placed in local helper functions. Retain the original value after testing it and apply any normalization only when requested. Test null explicitly with `x == NULL` and object kind with `IS_OBJECT(x)`. Do not use broad truthiness, `LENGTH`, or successful number parsing alone as a kind test.

## Operators and branches

Arithmetic: `+ - * / // %`; comparisons: `== != < <= > >=`; Boolean operations: `&& || !`. Parenthesize mixed operations. `+` also concatenates strings. `/` uses floating arithmetic unless explicit decimal operands are involved. `//` is integer division truncating toward zero, available for integer kinds only. `%` is remainder.

```branchline
LET category = CASE {
    WHEN input.score > 10 THEN "high"
    WHEN input.score == 10 THEN "equal"
    ELSE "low"
}
```

`CASE` requires `ELSE`. `IF condition THEN { ... } ELSE { ... }` controls statements. Truthiness makes null, false, zero, and empty strings false; list/object values are truthy even when empty. Prefer explicit comparisons where an empty collection matters.

## Mapping, filtering, reduction, and loops

Calls are uppercase free functions, e.g. `LOWER(TRIM(text))`. Do not invent instance methods such as `text.trim()` or JavaScript chains. Lambdas use `(x) -> expression` and object-producing expression lambdas require parentheses: `(x) -> ({field: x.field})`. A bare `{ ... }` after the arrow is a statement block; use `(x) -> { RETURN {field: x.field} }` if a block is intended.

- `MAP(list, fn)`: one result per item, preserving empty/singleton lists and nested arrays.
- `FILTER(list, fn)`: matching items in input order.
- `REDUCE(list, initial, fn)`: explicit seed first, callback third. Empty input returns the seed.
- `MAP`/`FILTER` callbacks receive `(value, index, list)` if declared.
- `REDUCE` callback receives `(accumulator, value, index, list)` if declared.
- `FIND(list, fn)`: first match, or null. `SOME`/`EVERY`: any/all; empty input gives false/true.

```branchline
LET xs = input.values ?? []
LET doubled = MAP(xs, (x) -> x * 2)
LET positive = FILTER(xs, (x) -> x > 0)
LET total = REDUCE(xs, 0, (acc, x) -> acc + x)
LET positions = MAP(xs, (x, i) -> ({index: i, value: x}))
LET squares = [x * x FOR EACH x IN xs WHERE x > 0]
```

Comprehensions return lists. `MAP` can build a list of lists without flattening. `FLATTEN(listOfLists)` removes exactly one list level; non-list items pass through.

```branchline
LET accumulated = []
FOR EACH x IN input.values ?? [] {
    accumulated += {value: x}
}
OUTPUT { entries: accumulated }
```

Initialize accumulators with `LET`; `SET name = expression` replaces a local binding. `+=` appends exactly one value to a list (including a nested list), or adds numeric operands. Avoid multiple `OUTPUT` statements when one final object is required. Native `OUTPUT` accepts an object or list of objects, not a scalar/list of scalars; arbitrary arrays, including empty and nested arrays, are valid inside object fields.

## Objects, grouping, and lookup

`KEYS(obj)`, `VALUES(obj)`, `ENTRIES(obj)` expose object contents; entries are `{key, value}` objects. `KEYS(list)` returns indices. `PUT(collection, key, value)` returns a new object/list, preserving other entries. List keys must be in `0..size` (size appends). `DELETE(collection, key)` returns a copy with an entry removed. `APPEND(list, value)` and `PREPEND(list, value)` add a single value, preserving nesting.

There is no registered `GROUP_BY` helper. Build groups with an object accumulator, choosing string keys for JSON objects:

```branchline
LET grouped = REDUCE(input.rows ?? [], {}, (acc, row) ->
    PUT(acc, row.kind, APPEND(GET(acc, row.kind, []), row)))
```

This preserves row order within each group. For lookup tables, `REDUCE(rows, {}, (acc, row) -> PUT(acc, row.code, row))` makes the last row for a duplicate key win. Use `GET(table, key, fallback)` to read the result. A first-match alternative is `FIND(rows, (row) -> row.code == wanted)`. Choose duplicate and missing policies explicitly.

## Sorting and collection utilities

`SORT(list)` has exactly one argument; it accepts no comparator. For reliable ordering use homogeneous numbers or homogeneous strings. There is no registered `SORT_BY`. `DISTINCT(list)` retains first occurrences; `REVERSE(list)` reverses; `ZIP(a,b)` returns pairs up to the shorter length.

For stable ascending ordering of records by a scalar key, sort distinct keys and concatenate each group in original order:

```branchline
LET rows = input.rows ?? []
LET ranks = SORT(DISTINCT(MAP(rows, (r) -> r.rank)))
LET ordered = FLATTEN(MAP(ranks, (rank) ->
    FILTER(rows, (r) -> r.rank == rank)))
```

This scans rows once per distinct key; it is a correctness idiom, not an efficiency claim. `REVERSE(ranks)` reverses key direction while preserving input order within ties. Supply non-null comparable keys explicitly. Do not sort object maps directly to obtain a field ordering.

`RANGE(start,end[,step])` includes the end; use positive steps. For positions, `KEYS(list)` avoids empty-range edge cases. `LENGTH(x)`/`COUNT(x)` count list/object/string members and return 0 for null. `SUM([])` is 0; `AVG([])`, `MIN([])`, `MAX([])` are null. Numeric aggregators reject non-numeric entries.

## Text and numbers

Text functions: `LOWER(s)`, `UPPER(s)`, `TRIM(s)`, `SUBSTRING(s,start[,length])`, `CONTAINS(s,part)`, `SPLIT(s,separator)`, `JOIN(list,separator)`. `TRIM(s)` removes leading/trailing Kotlin whitespace and preserves internal spaces; `LOWER`/`UPPER` also strictly require strings. `REPLACE(str, pattern, repl)` strictly requires a string first argument, stringifies the pattern/replacement arguments (null becomes empty text), and replaces all regex matches using Kotlin Regex rules. It is not a literal replacement search; invalid regexes can throw and replacement `$`/backslash sequences have regex replacement meaning. `MATCH(s,pattern)` returns matched text values. Normalize optional text before calling text functions, e.g. `UPPER(TRIM(input.label ?? ""))`.

`STRING(x)` uses runtime string representation (not general JSON serialization), returning null for null. `NUMBER(x)` parses ordinary numbers; invalid numeric text errors. `INT(x)` requires an integral value and errors on fractions. `PARSE_INT` is tolerant digit extraction, not a strict general numeric parser.

JVM integer arithmetic promotes overflow to arbitrary-precision integers. `DEC("1.25")` opts into decimal arithmetic; `DEC("17")` produces an explicit big integer. Avoid mixing decimal and ordinary float operands: convert deliberately with `DEC`. Ordinary division and floating values remain approximate. Output serialization of large/precise numeric kinds depends on the host's JSON number mode.

`ABS(x)`, `FLOOR(x)`, `CEIL(x)`, `ROUND(x[,precision])` are registered. `ROUND` uses half-to-even, not half-away-from-zero; `ROUND(6.5,0)` is 6 and `ROUND(7.5,0)` is 8. Decimal places are an integer precision argument. For integral nonnegative quotient calculations, `n // d` avoids floating division; for signed values remember truncation toward zero.

## Local helpers and errors

Top-level helpers use `FUNC name(x) = expression`, or a block with `RETURN expression`. Generic expression fallback: `TRY expression CATCH(err) => fallback`; the error has `message` and `type`. Errors are not silently null values. Do not rely on ambient time, random numbers, external adapters, or extra host functions. Only the standard operations documented here are assumed.

Sources: repository grammar, language docs, and stdlib implementation; see `SOURCES.md`. All fenced snippets and concrete inline syntax examples were checked through the actual experiment evaluator with synthetic inputs; fragment wrappers and outputs are recorded there.
