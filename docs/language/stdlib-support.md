---
title: Standard library support inventory
---

# Standard library support inventory

This is the intended 1.0 **stable-candidate** allowlist, not a declaration that the
v1.0.0-rc.1 prerelease runtime is stable. All 68 names in the default JVM and JS registries
are classified below, including the `COUNT` alias. A candidate contract covers only
the admitted domain in its row; behavior outside that domain remains experimental.
An experimental row is deliberately excluded from the 1.0 compatibility promise.
Classification changes require changing both this inventory and its conformance gate.

All rows specify positional arity; `a..b` is inclusive and `n+` has no upper bound.
Every bounded-arity candidate rejects wrong arity. Rejection means a synchronous
failure, without a promise about exception classes, wording, or stack traces.
Unspecified host classes, cyclic values, resource exhaustion, mutation during a call,
concurrent registry/store changes, callback argument-list retention, and callbacks
with external effects are excluded. Callback calls are synchronous and ordered;
callback failure propagates. Predicates use the `BOOLEAN` rule for admitted values.
Objects retain insertion order and have String or nonnegative integer keys; host
null keys are excluded. List indices in the candidate surface are nonnegative Ints.
Copies preserve element references and never mutate the source collection.

Numeric domains, representations and platform bounds are defined in
[numeric semantics](numeric.md); no row widens its exactness guarantees. In this
table, the common integer profile requires **every intermediate result** within
`-9007199254740991..9007199254740991`. Explicit `DEC` profiles differ by platform;
JS decimals are Double-backed. Ordinary finite floating operations promise their
specified floating behavior, not exact decimal mathematics. `ROUND` here admits
precision -12 through 0; other precisions remain experimental even if callable.
Strings use UTF-16 indices; case conversion and whitespace guarantees are bounded
to ASCII where stated. Host object rendering and Unicode/regex equivalence across
platforms are outside the candidate surface.

## Executable inventory

This Markdown table is also the machine-readable inventory. The JVM and Node
`StdlibSupportContractTest` read the checked-out file, require these exact contract
rows, compare names to `StdLib.names`, check shared-access metadata, and execute
independent successful, null/empty, wrong-arity and wrong-domain probes for every
candidate. The table is a test task input, so documentation-only changes rerun the
gate. Negative controls demonstrate missing names, alias changes, and changed
classification/contract text are rejected. The gate checks the default registry;
custom host functions and JVM ServiceLoader extensions are outside this allowlist.

Evidence codes `core`, `arrays`, `aggregate`, `numeric`, `strings`, and `hof` refer
to corresponding cases in `candidate_contracts_have_independent_oracles` in
`conformance-tests/src/commonTest/kotlin/io/github/ehlyzov/branchline/conformance/StdlibSupportContractTest.kt`.
The same gate checks all rows' arity/classification/domain fields. Codes ending in
`-source` indicate an experimental implementation review, **not acceptance evidence**:
`interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/std/StdCoreModule.kt`,
`interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/std/StdArraysModule.kt`,
`interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/std/StdAggModule.kt`,
`interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/std/StdNumericModule.kt`,
`interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/std/StdStringsModule.kt`,
`interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/std/StdTimeModule.kt`,
`interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/std/StdDebugModule.kt`,
and `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/std/StdSharedModule.kt`.
Existing numeric conformance suites add interpreter/strict-VM and platform-specific
precision/error oracles; these registry probes do not replace them.

<!-- stdlib-inventory:start -->
| Name | Alias of | Arity | Admitted domain | Result | Null | Errors beyond arity | Platform | 1.0 classification | Evidence |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ABS | — | 1 | finite ordinary numbers with absolute value in common integer profile | absolute numeric value | propagate | nonnumeric argument | JVM+JS | candidate | numeric |
| APPEND | — | 2 | list + any admitted value | copy with value last | element preserved; collection reject | wrong collection | JVM+JS | candidate | core |
| APPLY | — | 1+ | synchronous callback followed by positional values | callback result | values passed through; callback reject | bad callback; callback failure propagates | JVM+JS | candidate | hof |
| ASSERT | — | 1+ | condition + optional message; surplus ignored | true when truthy | condition fails; message defaults ASSERT failed | false condition throws supplied/default message | JVM+JS | experimental: diagnostic/error presentation | debug-source |
| AVG | — | 1 | finite primitive numeric list; integer partial sums within common profile | Double division of sum by count; empty null | empty result null; element/collection reject | nonnumeric element or wrong collection | JVM+JS | candidate | aggregate |
| AWAIT_SHARED | — | 2 | resource/key strings + configured store | blocks until shared value available | arguments reject; stored value follows store | missing resource/store; JS cannot block pending read | JVM+JS | experimental: blocking/store lifecycle and platform behavior | shared-source |
| BASE64_DECODE | — | 1 | canonical standard padded Base64 text | ByteArray | reject | wrong type; malformed input outside candidate domain | JVM+JS | candidate | strings |
| BASE64_ENCODE | — | 1 | ByteArray | standard padded Base64 text | reject | wrong type | JVM+JS | candidate | strings |
| BOOLEAN | — | 1 | null/boolean/finite ordinary number/string/list/object | false for null/false/zero/empty string; otherwise true | false | arity; other host values outside candidate domain | JVM+JS | candidate | strings |
| CEIL | — | 1 | finite ordinary numbers with result in common integer profile | smallest integer-valued number >= input | propagate | nonnumeric argument | JVM+JS | candidate | numeric |
| CHECKPOINT | — | 0+ | arbitrary values; first becomes label | tracer Call event when configured; returns true | label null | tracer failure propagates | JVM+JS | experimental: global debug/tracer integration | debug-source |
| COLLECT | — | 1 | host Iterable or Sequence | eager list | reject | wrong source; iteration errors propagate | JVM+JS | experimental: arbitrary host iteration effects | core-source |
| CONTAINS | — | 2 | string + string/null | literal case-sensitive containment | second false; first reject | wrong first type | JVM+JS | candidate | strings |
| COUNT | LENGTH | 1 | string/list/object/null | UTF-16 length or collection size; null zero | zero | other scalar rejected | JVM+JS | candidate | aggregate |
| DEC | — | 1 | numeric profile admitted integer/decimal text or finite number | explicit BI/BD; precision follows numeric profile | propagate | malformed text or unsupported type | JVM+JS | candidate | numeric |
| DELETE | — | 2 | object + admitted key; list + index 0..size-1 | copy with removal; absent object key unchanged | reject | wrong collection/key or list bounds | JVM+JS | candidate | core |
| DISTINCT | — | 1 | list | first occurrence order under host equality | elements preserved; collection reject | wrong collection | JVM+JS | candidate | arrays |
| ENTRIES | — | 1 | object with admitted keys | ordered objects with key and value fields | reject; values preserved | wrong collection | JVM+JS | candidate | core |
| EVERY | — | 2 | list + synchronous callback(value/index/list) | false on first falsy result; empty true | elements passed through; collection/callback reject | wrong collection/callback; callback failure propagates | JVM+JS | candidate | hof |
| EXPLAIN | — | 1 | variable name string | provenance or no-provenance object | reject | wrong arity/type | JVM+JS | experimental: provenance representation | debug-source |
| FILTER | — | 2 | list + synchronous callback(value/index/list) | retain truthy callback results | elements passed through; collection/callback reject | wrong collection/callback; callback failure propagates | JVM+JS | candidate | hof |
| FIND | — | 2 | list + synchronous callback(value/index/list) | first value with truthy callback; absent null | elements passed through; collection/callback reject | wrong collection/callback; callback failure propagates | JVM+JS | candidate | hof |
| FLATTEN | — | 1 | list | flatten exactly one list level; scalars retained | elements preserved; collection reject | wrong collection | JVM+JS | candidate | arrays |
| FLOOR | — | 1 | finite ordinary numbers with result in common integer profile | largest integer-valued number <= input | propagate | nonnumeric argument | JVM+JS | candidate | numeric |
| FORMAT | — | 2 | string template + list/object of primitive string/boolean/null/common-profile integers | replace {index/key}; escape {{ and }}; missing placeholder retained | value rendered null; collection/template reject | wrong template or args type | JVM+JS | candidate | strings |
| GET | — | 2..3 | object + admitted key + optional default | present value including null; absent default or null | value/default preserved; collection/key reject | wrong collection/key | JVM+JS | candidate | core |
| INT | — | 1 | boolean or integral number/text in common integer profile | integer value; boolean 1/0 | propagate | fractional/malformed/unsupported input | JVM+JS | candidate | strings |
| IS_FUNCTION | — | 1 | any admitted value | whether value is callable Function1 | false | arity | JVM+JS | candidate | hof |
| IS_OBJECT | — | 0+ | any values; only first argument inspected | true exactly when first value is object | false; absent also false | none for admitted values | JVM+JS | candidate | core |
| JOIN | — | 1..2 | list of string/null; optional string/null separator | concatenation; omitted separator empty | element/separator become empty; collection reject | wrong collection | JVM+JS | candidate | strings |
| KEYS | — | 1 | object or list; shared handles excluded | insertion-order object keys or zero-based list indices | reject | wrong collection | JVM+JS | candidate | core |
| LENGTH | — | 1 | string/list/object/null | UTF-16 length or collection size; null zero | zero | other scalar rejected | JVM+JS | candidate | aggregate |
| LISTIFY | — | 1 | list/object/null | list unchanged; object singleton; null empty | empty list | other scalar rejected | JVM+JS | candidate | core |
| LOWER | — | 1 | ASCII string | lowercase | reject | wrong type | JVM+JS | candidate | strings |
| MAP | — | 2 | list + synchronous callback(value/index/list) | map callback results | elements passed through; collection/callback reject | wrong collection/callback; callback failure propagates | JVM+JS | candidate | hof |
| MATCH | — | 2 | string + host regex pattern | all nonoverlapping match texts | pattern/replacement become empty; first reject | wrong first type or invalid regex | JVM+JS | experimental: JVM/JS regex dialect and replacement semantics | strings-source |
| MAX | — | 1 | list | max under numeric/boolean/string or host text comparator | empty null; null elements use text ordering | wrong collection; incompatible numeric domains | JVM+JS | experimental: heterogeneous host-text ordering | aggregate-source |
| MIN | — | 1 | list | min under numeric/boolean/string or host text comparator | empty null; null elements use text ordering | wrong collection; incompatible numeric domains | JVM+JS | experimental: heterogeneous host-text ordering | aggregate-source |
| NOW | — | 0 | none | system clock ISO instant text | not applicable | arity | JVM+JS | experimental: ambient wall clock | time-source |
| NUMBER | — | 1 | finite ordinary number/boolean; integer text in common profile; finite decimal/exponent text | number unchanged; boolean 1/0; text Int/Long/Double | propagate | malformed or out-of-Long integer text; unsupported type | JVM+JS | candidate | strings |
| PAD | — | 2..3 | string + numeric width + optional pad text | pad right for positive width; left for negative | pad defaults space; string/width reject | wrong types; empty pad can fail to terminate | JVM+JS | experimental: empty-pad termination and width conversion | strings-source |
| PARSE_INT | — | 1..2 | string whose extracted ASCII digits fit common profile; boolean/integral number; any fallback | extract and concatenate all ASCII digits; no sign; default zero or supplied fallback | fallback | nonintegral nonstring input or unsupported type | JVM+JS | candidate | strings |
| POWER | — | 2 | finite ordinary numbers; nonnegative base or integral exponent; finite result | Double power | either propagates | nonnumeric/nonfinite; negative fractional power; zero negative power | JVM+JS | candidate | numeric |
| PREPEND | — | 2 | list + any admitted value | copy with value first | element preserved; collection reject | wrong collection | JVM+JS | candidate | core |
| PRINT | — | 0+ | arbitrary host values | prints each argument; returns null | prints null | host output errors propagate | JVM+JS | experimental: process output and host rendering | core-source |
| PUT | — | 3 | object + admitted key + value; list + index 0..size + value | copy with replacement or append; source unchanged | value preserved; collection/key reject | wrong collection/key or list bounds | JVM+JS | candidate | core |
| RANDOM | — | 0 | none | pseudo-random Double in [0,1) | not applicable | arity | JVM+JS | experimental: nondeterminism and seed policy | numeric-source |
| RANGE | — | 1..3 | index-like n or start/end/step | inclusive ascending/descending integer list; one argument starts at zero | reject | invalid index; allocation/overflow not bounded | JVM+JS | experimental: zero-count and overflow semantics unresolved | arrays-source |
| REDUCE | — | 3 | list + initial value + synchronous callback(acc/value/index/list) | left fold; empty returns initial value | acc/elements passed through; collection/callback reject | wrong collection/callback; callback failure propagates | JVM+JS | candidate | hof |
| REPLACE | — | 3 | string + host regex pattern + replacement | regex replacement text | pattern/replacement become empty; first reject | wrong first type or invalid regex | JVM+JS | experimental: JVM/JS regex dialect and replacement semantics | strings-source |
| REVERSE | — | 1 | list | reversed copy | elements preserved; collection reject | wrong collection | JVM+JS | candidate | arrays |
| ROUND | — | 1..2 | finite ordinary number; optional integer precision -12..0; result within common profile | half-even rounded integer-valued result | first argument propagates; precision reject | nonnumeric value or invalid precision | JVM+JS | candidate | numeric |
| SHARED_WRITE | — | 3 | resource/key strings + value + configured synchronous store | set once and return value | value allowed; arguments reject | missing store; unsupported sync writes; already set | JVM+JS | experimental: store configuration and write lifecycle | shared-source |
| SIFT | — | 2 | object + synchronous predicate(value/key/object) | ordered object retaining truthy predicate results | values passed through; collection/callback reject | bad callback; callback failure propagates | JVM+JS | candidate | hof |
| SOME | — | 2 | list + synchronous callback(value/index/list) | true on first truthy result; empty false | elements passed through; collection/callback reject | wrong collection/callback; callback failure propagates | JVM+JS | candidate | hof |
| SORT | — | 1 | list | copy sorted by numeric/boolean/string or host text comparator | ordered by fallback text | wrong collection; incompatible numeric domains | JVM+JS | experimental: heterogeneous host-text ordering | arrays-source |
| SPLIT | — | 2 | string + nonempty string separator | literal split preserving empty segments | separator becomes empty; first reject | wrong first type | JVM+JS | candidate | strings |
| SQRT | — | 1 | finite nonnegative ordinary number | Double square root | propagate | nonnumeric/nonfinite or negative | JVM+JS | candidate | numeric |
| STRING | — | 1 | any host value | host toString result | propagate | host rendering can fail | JVM+JS | experimental: host rendering is not a data format | strings-source |
| SUBSTRING | — | 2..3 | string; nonnegative Int start/length with nonoverflowing sum; start <= UTF-16 length | substring using UTF-16 indices; omitted length to end | reject | wrong type; start beyond end | JVM+JS | candidate | strings |
| SUBSTRING_AFTER | — | 2 | two strings | text after first occurrence; unchanged when absent | reject | wrong type | JVM+JS | candidate | strings |
| SUBSTRING_BEFORE | — | 2 | two strings | text before first occurrence; unchanged when absent | reject | wrong type | JVM+JS | candidate | strings |
| SUM | — | 1 | integer list; inputs and all partial sums in common integer profile | integer sum; empty zero | element/collection reject | nonnumeric element or wrong collection | JVM+JS | candidate | aggregate |
| TRIM | — | 1 | ASCII string | remove leading/trailing ASCII whitespace | reject | wrong type | JVM+JS | candidate | strings |
| UPPER | — | 1 | ASCII string | uppercase | reject | wrong type | JVM+JS | candidate | strings |
| VALUES | — | 1 | object with admitted keys | insertion-order values | reject | wrong collection | JVM+JS | candidate | core |
| WALK | — | 1 | acyclic tree with String/Int/Long/BigInt map keys | lazy preorder sequence of path/key/value/depth/isLeaf records | scalar leaf | arity; invalid keys fail during consumption | JVM+JS | experimental: lazy consumption and host tree lifetime | core-source |
| ZIP | — | 2 | two lists | pairs up to shorter length | elements preserved; collection reject | wrong collection | JVM+JS | candidate | arrays |
<!-- stdlib-inventory:end -->

## Deliberate exclusions and known limits

`NOT` and `EXISTS` exist in `StdBooleanModule` source but are absent from both default
loaders. They are **unregistered and excluded**, not aliases or stable candidates.
Registering them later must update this inventory and its drift gate.

`KEYS` is a candidate only for lists/objects. Its shared-resource-handle overload
remains experimental; the metadata gate verifies shared access is conditional on
that handle. `AWAIT_SHARED` and `SHARED_WRITE` always require shared access. No other
default function carries that metadata.

`RANGE(0)` currently yields `[0, -1]` rather than an empty list, and fixed-width
endpoint arithmetic can overflow. `PAD("x", 3, "")` cannot make progress in its
padding loop. Neither operation is admitted; the gate does not execute the hanging
case. These are concrete reasons for exclusion, not promises to preserve defects.
Numeric inputs beyond admitted bounds, nonfinite numbers, arbitrary host renderings,
regex dialect details, global debug state, wall clocks, random streams and shared
store scheduling have no 1.0 candidate guarantee here.
