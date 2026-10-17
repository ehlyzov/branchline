# Authoring reference provenance

Prepared 2026-10-17. These packs were authored without reading `tasks.json`, `hidden.json`, or any scored solution. They are generic language/runtime references. No task answers or problem-specific output examples were authored. The parent experiment records their inclusion in the development index.

Each language pack has the same maximum size of **16,000 UTF-8 bytes**. Coverage is balanced by topic rather than padding: input surface, absent/null access, object/list construction and empty/singleton/nested shape, map/filter/reduce/loops, grouping and lookup, stable ordering, strings, integer arithmetic/rounding, and runtime limits. Different lengths reflect language-specific explanations and pitfalls. This manifest is provenance, not an extra language assistance pack.

## Branchline

Authority: the current local Branchline JVM implementation in this checkout, with local docs and grammar as supporting sources. No claim of JVM/JS parity is made by this reference.

Repository paths consulted:

- `interpreter/src/jvmTest/resources/io/github/ehlyzov/branchline/ebnf.txt`
- `docs/language/expressions.md`, `statements.md`, `std-core.md`, `std-hof.md`, `std-arrays.md`, `std-strings.md`, `std-agg.md`, `numeric.md`
- `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/std/StdCoreModule.kt`
- `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/std/StdHofModule.kt`
- `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/std/StdArraysModule.kt`
- `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/std/StdStringsModule.kt`
- `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/std/StdNumericModule.kt`
- `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/std/StdSupport.kt`
- `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/ir/Exec.kt` (native OUTPUT restriction)
- `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/playground/PlaygroundFacade.kt` (generic probe entrypoint)

The Branchline Transformer skill's quick reference was a navigation aid; signatures were checked against registered functions, not assumed from the skill. In particular, `SORT` has one argument; there is no registered `SORT_BY` or `GROUP_BY`; `LISTIFY` rejects scalars; `GET` distinguishes missing from present-null; `REDUCE` argument order is list/seed/function.

Generic smoke probes used `playground.PlaygroundFacade.INSTANCE.run(source, json, false, false)` from the already-built interpreter JVM jar and cached runtime dependencies. They established:

| Probe | Result |
| --- | --- |
| Missing value `?? 7`; explicit null via `GET(obj,"none",7)`; presence via `SOME(KEYS(...),...)` | 7; null; true |
| `MAP([],...)`; `MAP([4], x -> x+1)` inside an output object | []; [5] |
| `[[4]]` inside an output field | [[4]] |
| Group rows with `REDUCE`/`PUT`/`GET`/`APPEND` | Arrays retained; rows stay in input order |
| Sort distinct numeric keys, map each to a filtered row group, flatten once | rank tags b,a,c from a:2,b:1,c:2 |
| Direct root `OUTPUT` of a nested list | Rejected: expected list of objects; arbitrary arrays are therefore documented as fields of an object |

A follow-up generic six-kind probe `[NULL, TRUE, 3, "a", [], {}]` verified strict `TRY` domain tests: TRIM accepted only the text element, MAP only the list, and SUM of a singleton list only the number. Source inspection confirmed Branchline TRIM preserves internal spaces and REPLACE uses a regex with a strict first-string argument.

An initial one-line multi-statement probe was rejected until statement separators were fixed to newlines. No product source or test was changed for these probes. The ordinary CLI module jar has no main manifest and was not treated as a runnable shaded CLI.

## JSONata / Dashjoin

Runtime authority: cached Maven artifact `com.dashjoin:jsonata:0.9.10`, executed directly through `Jsonata.jsonata(expression).evaluate(Json.parseJson(input))`. Native API inspection used `javap` on that same jar. No custom functions or simulated evaluator were used.

Primary upstream documentation and source consulted:

- [JSONata 1.8 processing model](https://docs.jsonata.org/1.8.0/processing): sequences, undefined, singleton flattening.
- [JSONata programming](https://docs.jsonata.org/programming): blocks, variables, functions. Current docs may contain newer operators, which this pack explicitly does not assume.
- [JSONata 1.8 higher-order functions](https://docs.jsonata.org/1.8.0/higher-order-functions): map/filter/reduce callback and seed conventions.
- [JSONata 1.8 array functions](https://docs.jsonata.org/1.8.0/array-functions): append, sorting, array utilities.
- [JSONata 1.8 object functions](https://docs.jsonata.org/1.8.0/object-functions): lookup, merge, each, sift.
- [JSONata 1.8 grouping](https://docs.jsonata.org/1.8.0/sorting-grouping): grouping evaluation model.
- [JSONata 1.8 Boolean functions](https://docs.jsonata.org/1.8.0/boolean-functions): exists and truthiness.
- [JSONata 1.8 string functions](https://docs.jsonata.org/1.8.0/string-functions): string conversion, whitespace, casing, text operations.
- [JSONata 1.8 numeric functions](https://docs.jsonata.org/1.8.0/numeric-functions): parsing, floor/ceil, half-even rounding.
- [Dashjoin README](https://raw.githubusercontent.com/dashjoin/jsonata-java/main/README.md): Java port, Maven coordinates, evaluate API, native value model. The main branch is supporting background, not proof of pinned-version behavior.
- [Dashjoin Functions source](https://raw.githubusercontent.com/dashjoin/jsonata-java/main/src/main/java/com/dashjoin/jsonata/Functions.java): supporting API context; pinned jar execution takes precedence when behavior matters.

Pinned-engine generic probes:

| Expression / scenario | Observed result |
| --- | --- |
| `[$map([], function($x){$x+1})]` | [] |
| `[$map([4], function($x){$x+1})]` | [5] |
| `[$map([4,8], function($x){[$x,$x+1]})]` | [[4,5],[8,9]] |
| Reduce with `$append($acc,[[$x,$x+1]])` | Nested arrays retained |
| `$exists(null)` | true |
| Absent/null ternary fallback | Explicit null replaced only by the null-aware version |
| `$merge([$map([], function($r){ {$r.code:$r} })])` | {} |
| Same lookup-index expression for one object | Object with that key and row |
| `$trim(" a  b ")` | "a b" |
| `[1..0]` | [] |
| `$sort` by `left.rank > right.rank` for a:2,b:1,c:2 | tags b,c,a |
| Same records decorated with original index; index breaks equal ranks | tags b,a,c |

**Port-specific sorting caution:** upstream documentation calls sort stable, but the pinned comparator probe reordered equal keys. The pack describes the observed behavior and supplies an explicit index tie breaker. This is a generic runtime fact, not a hidden-test hint. JSONata 2.x-only convenience operators were omitted rather than inferred to exist.

## Kotlin JVM

Target surface: Kotlin 2.3.0; default imports plus `java.math.*` / `kotlin.math.*`. The pack relies on long-established APIs, and does not infer newer library features from the current documentation version. It assumes ordinary Map/List/Number values rather than a JSON-tree library.

Primary references consulted:

- [Null safety](https://kotlinlang.org/docs/null-safety.html): nullable types, safe calls/casts, Elvis operator.
- [Map operations](https://kotlinlang.org/docs/map-operations.html): lookup and editing.
- [Collection transformations](https://kotlinlang.org/docs/collection-transformations.html): map/flatMap/associate behavior.
- [Grouping](https://kotlinlang.org/docs/collection-grouping.html): groupBy and group processing.
- [Aggregate operations](https://kotlinlang.org/docs/collection-aggregate.html): fold/reduce and empty collections.
- [Ordering](https://kotlinlang.org/docs/collection-ordering.html): comparator composition.
- [sortedBy API](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/sorted-by.html): stable ordering, present since Kotlin 1.0.
- [Numbers](https://kotlinlang.org/docs/numbers.html): primitive numeric types, conversions, division, overflow.
- [Strings](https://kotlinlang.org/docs/strings.html): text operations.
- [lowercase API](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.text/lowercase.html): invariant Unicode conversion, present since 1.5.
- [round API](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.math/round.html): half-even floating rounding.
- [roundToLong API](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.math/round-to-long.html): different tie rule.
- [JDK 21 BigDecimal](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html): exact text construction, scale, division, rounding, comparison.

No scored Kotlin solution was read or authored. The generic Kotlin snippets were documentation-checked; no Kotlin runtime verification is claimed by this manifest.

## File measurements

The values below count encoded UTF-8 file bytes, including Markdown markup and final newline; they exclude this provenance file. Hashes identify the exact reference texts measured.

| Pack | UTF-8 bytes | SHA-256 |
| --- | ---: | --- |
| branchline.md | 10236 | `69eacf78a52dc343e4aeb8c64735f4978b01574ddee09690a08195838c73df14` |
| jsonata.md | 9152 | `398f7c2031c8370b03c1b268c32249d1160a52a26692c6dd2a9ab76f1fa55145` |
| kotlin.md | 10512 | `ee92f198b2578e67464da6bf39408fa61b4a4d6f3bef9538ac796e6193b31124` |
