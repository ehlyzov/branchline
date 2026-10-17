# Kotlin JVM 2.3.0 reference

Author a complete function `fun transform(input: Any?): Any?`. The host supplies ordinary JSON values as `Map`, `List`, `String`, `Number`, `Boolean`, or null. Return equivalent ordinary values. Use default Kotlin imports and, if needed, `java.math.*` and `kotlin.math.*`; no extra helper library, JSON wrapper API, or predeclared transformation helper is available. Helpers you define are part of the submitted solution code. Examples below use generic identifiers and do not implement a problem specification.

```kotlin
fun transform(input: Any?): Any? {
    val root = input as? Map<*, *> ?: emptyMap<Any?, Any?>()
    val label = (root["label"] as? String ?: "").trim().lowercase()
    return mapOf("label" to label)
}
```

## Types, access, and defaults

`val` declares an immutable binding; `var` allows reassignment. Nullable types end in `?`. `as?` is a safe cast, returning null on failure; `as` throws. Smart casts narrow types after `is` checks. `Any?` has no dynamic JSON-field properties: cast an object to `Map<*, *>` and use `obj["key"]`.

```kotlin
val root = input as? Map<*, *> ?: emptyMap<Any?, Any?>()
val child = root["child"] as? Map<*, *>
val code = child?.get("code") as? String ?: "unknown"
val values = root["values"] as? List<*> ?: emptyList<Any?>()
val first = values.getOrNull(0)
```

Both missing map entries and present null values read as null; `obj.containsKey("k")` distinguishes them. `?:` replaces null only and retains 0, false, and empty strings. `obj.getOrDefault("k", fallback)` defaults only when the key is absent; present null remains null. Safe cast plus fallback also replaces values of the wrong type, which may be inappropriate when wrong types should fail.

Kotlin has no truthiness conversion: conditions must be Boolean. Use `x != null`, `text.isNotEmpty()`, `list.isEmpty()`, and explicit numeric comparisons. `!!` throws for null; use it only where the input contract or a preceding guard establishes non-nullness. Prefer `==` for structural equality; `===` is reference identity.

## Objects, arrays, and output shape

Use `mapOf("code" to "q", "amount" to 3L)`, `listOf(7L)`, `emptyList<Any?>()`, and `emptyMap<String, Any?>()`. Maps preserve explicit null values when an entry is included. Omit a key by omitting the map entry, not by inserting null.

Lists keep empty, singleton, and nested shape automatically: `emptyList<Any?>()`, `listOf(7L)`, and `listOf(listOf(7L))` are distinct. `map` returns a list even for zero or one result; `mapNotNull` intentionally drops null results. `flatten` removes one nesting level, while `flatMap` maps and concatenates. Do not use flattening for a required list of lists.

Use a mutable builder when keys are conditional:

```kotlin
val out = linkedMapOf<String, Any?>("code" to "q")
if (root.containsKey("note")) out["note"] = root["note"]
```

Map key order is usually not semantically important in JSON. Use lists and explicit sorting whenever output order is required. Do not return Kotlin data classes, `Pair`, sequences, or maps with non-string keys as substitutes for the requested JSON objects/arrays.

## Operators and branching

Arithmetic: `+ - * / %`; comparisons: `< <= > >= == !=`; Boolean operations: `&& || !`. `if` and `when` are expressions:

```kotlin
val category = when {
    score > 10L -> "high"
    score == 10L -> "equal"
    else -> "low"
}
val label = if (enabled) "on" else "off"
```

Strings concatenate with `+` or use interpolation: `"item-$index"`. Use parentheses to clarify mixed comparisons and arithmetic. Ordinary `Number` has no arithmetic operators; convert or validate its concrete numeric type first.

## Mapping, filtering, folding, loops

Standard collection operations are available through default imports:

```kotlin
val xs = listOf(2L, 5L, 8L)
val doubled = xs.map { x -> x * 2L }
val positive = xs.filter { x -> x > 0L }
val total = xs.fold(0L) { acc, x -> acc + x }
val indexed = xs.mapIndexed { i, x -> mapOf("index" to i, "value" to x) }
```

`fold(seed, fn)` returns the seed on empty input. `reduce(fn)` has no seed and throws on an empty collection. `sum`, `sumOf`, `count`, `any`, `all`, `none`, `firstOrNull`, `find`, `minOrNull`, and `maxOrNull` cover common aggregation and selection. `any`/`all` on empty collections return false/true. `firstOrNull` cannot distinguish a matched null from no match without an additional presence check.

```kotlin
val rows = root["rows"] as? List<*> ?: emptyList<Any?>()
val accumulated = mutableListOf<Any?>()
for ((index, value) in rows.withIndex()) {
    accumulated.add(mapOf("index" to index, "value" to value))
}
```

`accumulated.add(value)` appends one item including a nested list. `addAll(values)` concatenates. `0 until n` excludes n and is empty when n is zero; `0..n` includes n. `for (x in rows)`, `while`, `break`, and `continue` are available. A local helper can be declared with `fun name(x: Type): Result = expression` or a block with `return`.

## Grouping, object operations, lookup

After validating/casting each row, `groupBy` creates a map of lists. It preserves the order of rows within each group and key order by first occurrence. `associateBy` creates an index, with the last row winning for duplicate keys. `distinct` and `distinctBy` retain first occurrences. Select duplicate behavior deliberately.

```kotlin
val rows = (root["rows"] as? List<*> ?: emptyList<Any?>())
    .map { it as Map<*, *> }
val groups = rows.groupBy { it["kind"] as String }
val index = rows.associateBy { it["code"] as String }
val wanted = index["q"]
```

The explicit `as` here assumes object rows with string keys as a contract; use validation or safe casts if the requirements allow other values. `filterIsInstance<Map<*, *>>()` silently drops invalid rows, so it is not an equivalent validation strategy.

Maps expose `keys`, `values`, and `entries`. `mapValues { (key,value) -> ... }` transforms values and keeps keys. `filterKeys`, `filterValues`, and `filter` retain selected entries. `toMutableMap()` creates an editable copy; `put`, assignment `map[key] = value`, and `remove` operate on mutable maps. `getOrPut(key) { mutableListOf() }` is useful in grouping loops; it treats a present null value as needing a default. Avoid mutating input structures unless necessary.

## Stable sorting

`sorted`, `sortedDescending`, `sortedBy`, and `sortedWith` return new lists and are stable on the JVM: items comparing equal retain their input order. Use an explicit key type. `reversed()` reverses a list, including ties, so prefer a descending comparator when tie order must stay stable.

```kotlin
val ordered = rows.sortedWith(
    compareBy<Map<*, *>> { (it["rank"] as Number).toLong() }
        .thenBy { it["label"] as String }
)
val descending = rows.sortedByDescending { (it["rank"] as Number).toLong() }
```

`.thenByDescending` supports a descending secondary key. Comparators must be consistent and transitive. If null keys are possible, define their placement explicitly; default null ordering should not silently choose task behavior. `withIndex()` can preserve original positions for custom tie breaking. A lookup map is not an ordered list of results.

## Text

`text.trim()`, `text.lowercase()`, `text.uppercase()`, `text.contains(part)`, `text.startsWith(prefix)`, `text.endsWith(suffix)`, `text.substring(start,endExclusive)`, `text.split(separator)`, and `parts.joinToString(separator)` are available. Kotlin `trim()` removes leading/trailing whitespace without collapsing internal spaces. The no-argument case conversion uses invariant Unicode rules. `replace(oldText,newText)` is literal string replacement; `replace(Regex(pattern),replacement)` is regex replacement. Kotlin regexes on JVM use the JVM regex engine.

`value.toString()` produces Kotlin/JVM text rather than general JSON serialization; null's `toString()` is the text `"null"`. Prefer `value as? String` for a required string and use explicit fallback or validation. `text.toLongOrNull()` and `text.toDoubleOrNull()` permit explicit invalid-input policies; `toLong()` throws on invalid/out-of-range text.

## Numeric domains and rounding

The host can supply different `Number` subtypes. `(x as Number).toLong()` accepts multiple numeric types but truncates fractions; it is not integral validation. `.toDouble()` introduces floating approximation. Ensure a whole-number/range precondition before converting when exact integers are required. Equality on boxed values does not make all numeric subtypes interchangeable.

Use `Long` literals (`3L`) and seeds (`0L`) for bounded integer calculations. `Long` division truncates toward zero: `-7L / 3L` is -2; remainder is -1. Floating division uses `Double`, so `7.0 / 3.0` is fractional. Primitive integer overflow wraps; Kotlin does not promote automatically. `sumOf { ...toLong() }` can overflow as well.

With `import kotlin.math.*`, `floor`, `ceil`, `abs`, `round`, and powers/roots are available. `round(Double)` uses half-to-even and returns Double. `roundToLong()` uses a different tie rule (ties toward positive infinity), so it is not interchangeable. For a requested decimal-place round, define the rounding mode and precision deliberately; multiplying a Double by a power of ten can expose binary rounding error.

With `import java.math.*`, use `BigInteger` or `BigDecimal` for explicit precision. `BigDecimal("6.5").setScale(0, RoundingMode.HALF_EVEN)` is exact decimal rounding. `BigDecimal` division needs an explicit scale/rounding or `MathContext` for nonterminating quotients. Construct from exact text when possible; `BigDecimal(double)` exposes the Double's binary approximation. Compare decimal values with `compareTo`, since `equals` also considers scale. Do not convert arbitrary `Number` through Double when exactness is required. Return JSON-compatible numeric values according to the required output contract.

## Determinism and failures

No external files, networking, clock, random state, or imagined host helpers are needed for pure JSON transformations. A type cast, invalid parse, bad index, or failed `require` throws and fails the invocation. `try/catch` can implement an explicitly requested fallback; it should not conceal a shape or type error unintentionally. The host handles input parsing and output serialization, so submit transformation code rather than a CLI or JSON parser.

Official Kotlin references are listed in `SOURCES.md`; these operations are part of the Kotlin 2.3.0 standard language/library surface.
