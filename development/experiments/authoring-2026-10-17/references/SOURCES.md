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

No scored Kotlin solution was read or authored. Kotlin fenced snippets and concrete inline examples were subsequently compiled and executed through the pinned experiment evaluator, as recorded in the v2 audit below.

## Reseal v2: exact-example evaluator audit

The first cohort was stopped and retained as unscored after a reference defect was discovered. No task, hidden fixture, scored source, or oracle was consulted for this correction. The corrected Branchline pack changes only three concrete syntax/surface issues: the complete skeleton uses evaluator-required `TRANSFORM Main`; statement IF includes `THEN`; and object expression lambdas parenthesize their object (or explicitly RETURN from a block). The original IF and bare-object-lambda forms were retained as negative controls and failed with the expected parser diagnostics.

This audit uses the actual `evaluator/evaluate.py` interface, its frozen interpreter artifact, Dashjoin 0.9.10, Kotlin 2.3.0, sandbox, JSON parsing, and output comparison. It does not use the earlier Playground smoke interface as a substitute. All 21 language-tagged fences were extracted byte-for-byte from the corrected packs and verified to appear unchanged inside the evaluated source. Fragments received only enclosing code, bindings for their documented free variables, and an output/return that exposes their values. Template signatures and ellipsis syntax were instantiated with generic values; they are not literal standalone programs. No claim is made that placeholder notation itself compiles.

Evidence root: `/tmp/branchline-reference-audit-v2/`. Each table ID has `LANGUAGE/ID/source`, `cases.json`, and `result.json`, containing exact evaluator input, expected JSON, and actual output/error. `audit-results.json` records pass status and SHA-256 of those files and every literal fence. The replay script is `audit_references_v2.py` under the same root. It asserts literal fence inclusion before evaluation. The parent must preserve these evidence artifacts with the reseal.

Totals: **29 positive programs, 58 positive input/output checks, all passing**; **4 negative controls, all rejected as expected**. Branchline: 8 fences + 3 inline programs, 25 positive checks; JSONata: 5 fences + 2 inline programs, 14 checks; Kotlin: 8 fences + 3 inline programs, 19 checks. The same requested operation topics were checked across languages; Branchline receives syntax corrections, not task-specific solutions.

### Literal fenced snippets

Fence numbering is one-based in each language pack. `body` below means the literal fenced text, not a rewritten equivalent. Exact full expected/actual JSON is in each result artifact.

| Language / ID | Enclosing code and bindings | Output checks |
| --- | --- | --- |
| branchline/fence-1 | Complete Main source, no wrapper | label `" Ab C "` -> `"ab c"`; missing -> empty text |
| branchline/fence-2 | `TRANSFORM Main` around body; OUTPUT code and first | Missing child/list -> unknown/null; child q/list [4] -> q/4 |
| branchline/fence-3 | Main around body; OUTPUT text/list/number flags | Six inputs null, true, 3, a, [], {} classify only their strict domain |
| branchline/fence-4 | Main around body; OUTPUT category | scores 9/10/11 -> low/equal/high |
| branchline/fence-5-corrected-object-lambda | Main around body; OUTPUT all five derived bindings | values [-2,0,3]: doubled [-4,0,6], positive [3], total 1, three index/value objects, squares [9]; missing -> empty lists/0 |
| branchline/fence-6 | Main around body, which already contains OUTPUT | [2,5] -> two value objects; missing -> empty entries |
| branchline/fence-7 | Main around body; OUTPUT grouped | rows u:a:2,v:b:1,u:c:2 -> u=[a,c],v=[b]; missing -> {} |
| branchline/fence-8 | Main around body; OUTPUT ordered | Same rows -> b,a,c, preserving rank ties; missing -> [] |
| jsonata/fence-1 | Complete expression, no wrapper | label normalization |
| jsonata/fence-2 | Complete expression, no wrapper | Missing -> 9/9; explicit null -> null/9; zero -> 0/0 |
| jsonata/fence-3 | Parenthesized body, followed by object exposing $xs | Missing/null -> []; singleton [4] remains [4] |
| jsonata/fence-4 | Bind $rows to root rows, then evaluate literal body | u=[a,c],v=[b]; empty -> {} |
| jsonata/fence-5 | Bind $rows to root rows, then literal complete block | b,a,c stable ties; empty -> []; singleton retained |
| kotlin/fence-1 | Complete transform function, no wrapper | Label normalization and missing label |
| kotlin/fence-2 | transform around body; return code/first map | Missing -> unknown/null; child q/list [4] -> q/4 |
| kotlin/fence-3 | transform; bind root as Map; body; return out | Missing note omitted; present-null note retained |
| kotlin/fence-4 | transform; bind score as Long and enabled as Boolean; body; return category/label | 9/10/11 and enabled false/true -> low/equal/high and off/on |
| kotlin/fence-5 | transform around body; return all four derived bindings | [2,5,8] -> doubled [4,10,16], positive unchanged, sum 15, indexed objects |
| kotlin/fence-6 | transform; bind root as Map; body; return accumulated values | [4,8] -> indexed objects; missing -> [] |
| kotlin/fence-7 | transform; bind root as Map; body; return groups/index/wanted | u=[a,c],v=[b], all code lookups retained, missing q null; empty -> empty maps/null |
| kotlin/fence-8 | transform; bind rows as List<Map>; body; return both orders | Equal-label rank sort -> b,a,c; descending -> a,c,b; empty -> []/[] |

### Concrete inline syntax and operation coverage

| Language / ID | Instantiations and observed outputs |
| --- | --- |
| branchline/inline-if-object-lambda-helper | Both IF branches with THEN; parenthesized-object and RETURN-block lambdas each return [{field:q}]; expression/block FUNC helpers return 5; TRY invalid INT falls back to 7 |
| branchline/inline-core-collections | Literal object/array/path/quoted/index access; GET absent/default/null; presence; IS_OBJECT; IS_FUNCTION on a lambda; LISTIFY null/object/list; KEYS/VALUES/ENTRIES; PUT/DELETE; single-item append/prepend and flatten; last-wins index vs first FIND; DISTINCT/SORT/REVERSE/ZIP/RANGE; null length/empty aggregate/empty any-all identities. Full expected/actual object is in result.json |
| branchline/inline-text-number-operators | Casing, TRIM internal spaces, substring/contains/split/join/regex replacement and matches; conversions; precise DEC addition; half-even 6/8; -7 integer division/remainder -2/-1; ordinary division; abs/floor/ceil; ?? retains zero/false/empty text; arithmetic/Boolean/string concat; nested arrays |
| jsonata/inline-collections-and-kinds | Undefined object field omission; $type over six JSON kinds; null exists; empty truthiness; forced empty/singleton/nested arrays; filter/reduce/ranges/append; dynamic keys; lookup index; declarative grouping; sort/order-by; keys/lookup/each/sift/spread/distinct/reverse/zip/join/sum/count; first/predicate/positive-negative position/backtick access |
| jsonata/inline-text-numbers | Casing, whitespace collapse, substring/contains/split/replace; $string object and numeric parse; negative floor/truncating division; remainder; half-even 6/8; abs/floor/ceil/power/sqrt; root $$; string concat and Boolean ternary |
| kotlin/inline-values-kinds-null | Missing/default/present-null; explicit literals and empty/singleton/nested lists; map/mapNotNull/flatten/flatMap; add vs addAll; range bounds; fold/sum/sumOf/count/any/all/none/first/find/min/max; interpolation; is Map/as? List; helper function |
| kotlin/inline-object-operations | keys/values/entries/mapValues/filterKeys/filterValues/filter; mutable copy/put/assignment/remove; getOrPut present-null default; distinct/distinctBy; sorted/sortedDescending/reversed |
| kotlin/inline-text-numbers | trim/case/contains/prefix/suffix/substring/split/join; literal/regex replacements; null-toString; valid/invalid tolerant parse; Long division/remainder; Double division; floor/ceil/abs/round vs roundToLong; BigDecimal half-even; BigInteger; decimal compareTo vs equals scale; pow/sqrt |

Negative control IDs: `branchline/negative-original-if`, `branchline/negative-original-object-lambda`, `jsonata/negative-wrong-type`, `kotlin/negative-wrong-type`. These are expected evaluator failures, not positive example passes. A draft audit also made an unsupported assumption about passing a declared FUNC identifier to IS_FUNCTION; that was an audit-only case, absent from the pack. Its positive check was corrected to pass a lambda value. The pack does not assert that declared function names are first-class values.

## File measurements

The values below count encoded UTF-8 file bytes, including Markdown markup and final newline; they exclude this provenance file. Hashes identify the exact reference texts measured.

| Pack | UTF-8 bytes | SHA-256 |
| --- | ---: | --- |
| branchline.md | 10492 | `9c04a080b6bae53d92c0b6dfffbfdf29ae4ee72e81338dba14fa39af254340d3` |
| jsonata.md | 9345 | `d71bb7a99cd5fffabaf97cb8995c908cbface5bec21f1361bd1f565700459b95` |
| kotlin.md | 10705 | `673577fb39d62efb8428ba553f4661183edbb0fa42e6e6068057917cc370dcc3` |

Audit replay script SHA-256: `befab1bc3c5aa56f98257e2048c82a0dd13d6a75cff696e7b4e97a383bb995b1`.
Audit results manifest SHA-256: `148fd6a1e1ae09c4969847b68f9c8f1e26cb48f8d783a34cab48281dbb29b495`.
