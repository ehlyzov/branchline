# JSONata reference: Dashjoin JVM 0.9.10

Author one JSONata expression evaluated against the input JSON document. The input is the root: `rows` reads a root field, `$` denotes the current context, and `$$` is the original root. There is no injected `$input` variable. No custom host functions are assumed. Examples are generic and independent of problem specifications.

```jsonata
(
  $label := $lowercase($trim(label));
  {"label": $label}
)
```

## Values, expressions, and scope

JSON literals work: `null`, `true`, `false`, strings, numbers, arrays, objects. Object keys are quoted text or expressions yielding text. Bind variables with `$name := expression`; use a parenthesized block and semicolons between bindings. The final expression is the result. Functions use `function($x){ expression }`; a multistep function body can contain a parenthesized block.

Arithmetic: `+ - * / %`; string concatenation: `&`; equality: `=` and `!=`; Boolean: `and`, `or`, `$not(x)`. Conditional: `condition ? thenExpression : elseExpression`. Variable names start with `$`; ordinary names select fields.

Use explicit ternary defaults compatible with this pinned port. Do not assume recently documented `??` or `?:` syntax exists in 0.9.10. Functions and operators have their own type rules; automatic truthiness is not a null-only default.

## Missing, null, and access

An absent path produces an empty sequence (undefined), which differs from JSON null. `$exists(x)` tests definedness: it is false for absent values and true for null, false, 0, and empty text. `$type(x)` yields a type name, or undefined for absent values. `$boolean(x)` follows effective Boolean rules; for arrays it asks whether any element is truthy, and empty arrays are false.

```jsonata
(
  $v := settings.level;
  {
    "missingOnly": $exists($v) ? $v : 9,
    "missingOrNull": ($exists($v) and $v != null) ? $v : 9
  }
)
```

Undefined object values omit the key: `{"value": missing}` is `{}`. Emit explicit `null` when a key must remain present. Undefined mapped values can disappear; return `null` or a constructed object when each input element must produce an output slot.

Read a dynamic object key with `$lookup(object, key)`, not JavaScript indexing. `items[0]` is positional selection; negative positions count from the end. `items[condition]` is filtering, so use `$lookup` for dynamic string keys. Names with punctuation can use backticks: ``settings.`some-key` ``. Capture root values in variables before changing path context.

## Sequences and array shape

Path evaluation maps and flattens result sequences. Zero matches disappear, one match can collapse to a scalar, and multiple matches appear as an array. An explicit JSON array is different from a result sequence. Avoid assuming that a path or `$map` always returns an array.

- `[$map($xs, function($x){ ... })]` forces the outer mapped result to an array, including empty and singleton cases.
- `[$filter($xs, function($x){ ... })]` likewise gives an array.
- A function returning `[$x,$x+1]` produces nested arrays when used as the map result; `[$map([4,8], function($x){[$x,$x+1]})]` is `[[4,5],[8,9]]` in the pinned engine.
- `[[...]]` explicitly adds another array level. Do not add it merely to fix singleton collapse.

To default an expected input array without converting null into `[null]`:

```jsonata
$xs := ($exists(values) and values != null) ? values : []
```

This does not validate wrong scalar input types. Use `$type($xs) = "array"` if validation is part of the requirements. Direct array values can preserve their shape, whereas later path navigation can apply sequence rules. Check the constructed output shape at each boundary.

## Map, filter, reduce, loops

`$map(array, function($value,$index,$array){...})` accepts optional index/list parameters. Raw `$map` can return undefined for empty input and a scalar for singleton input. Wrap with `[...]` when producing an array field.

`$filter(array, function($value,$index,$array){predicate})` retains matching elements in input order. A path predicate, e.g. `rows[enabled = true]`, is another filtering form. `$single` throws unless exactly one match exists; it is not a nullable first-match helper.

`$reduce(array, function($acc,$value,$index,$array){...}, initial)` puts the callback before the seed, unlike some languages. Always provide a seed for empty arrays. A sum example is `$reduce($xs, function($a,$x){$a+$x}, 0)`. `$sum`, `$average`, `$min`, `$max`, and `$count` are available. `$sum([])` is 0; average/min/max of empty input are undefined.

There are no imperative `for` statements. Use paths, map/filter/reduce, or recursive local functions. `[0..3]` is an inclusive integer range; `[1..0]` is empty. Use `$map`'s index argument for normal indexed iteration.

`$append(a,b)` concatenates arrays; a non-array operand is treated as one array element. To append a nested array as one item, wrap that item: `$append($acc, [[$x,$x+1]])`. `$distinct`, `$reverse`, `$zip`, and `$join` are available. `$zip` stops at the shortest input. `$join` concatenates an array of strings with an optional separator.

## Objects, grouping, and lookup tables

`$keys(obj)`, `$lookup(obj,key)`, `$each(obj,function($value,$key){...})`, `$sift(obj,predicate)`, `$spread(obj)`, and `$merge(arrayOfObjects)` operate on objects. `$merge` makes later duplicate fields win. Explicitly wrap `$each` results if they must be an array. Construct a dynamic key with `{$key: value}`.

A generic grouping fold preserves all rows and their order:

```jsonata
$reduce($rows, function($acc,$row){(
  $k := $row.kind;
  $old := $lookup($acc,$k);
  $merge([$acc, {$k: $append($exists($old) ? $old : [], [$row])}])
)}, {})
```

This assumes `kind` is a string and stored groups are arrays. Declarative grouping also exists: `rows{kind: $sum(amount)}` groups rows by their text key and evaluates the value expression over each group. Grouping evaluates after collecting matching keys; it is not always one independent object construction per row. Use the explicit fold when list shape needs close control.

A lookup index can use `$merge([$map($rows, function($r){ {$r.code: $r} })])`; duplicate codes choose the last row. The empty result is an object. Read with `$lookup` and an explicit missing policy. Filtering and then selecting `[0]` is an alternative when first-match behavior is required.

## Stable sorting in the pinned engine

`$sort(array)` sorts homogeneous numbers or strings. `$sort(array, function($left,$right){...})` uses a Boolean comparator: true means the left item belongs after the right item. It does not take a negative/zero/positive comparator result. JSONata also has order-by syntax such as `rows^(<rank, >label)`.

Official JSONata documentation describes stable sorting, but a direct probe of Dashjoin 0.9.10 reordered equal keys with `$l.rank > $r.rank`. Preserve ties explicitly by decorating with the original index:

```jsonata
(
  $indexed := $map($rows, function($r,$i){ {"row":$r,"index":$i} });
  $ordered := $sort($indexed, function($l,$r){
    $l.row.rank = $r.row.rank
      ? $l.index > $r.index
      : $l.row.rank > $r.row.rank
  });
  [$map($ordered, function($entry){$entry.row})]
)
```

Use explicit missing/null key policy and homogeneous key types. The index can also be a final tie breaker after several key comparisons. Descending keys reverse the comparison, not the original-index comparison.

## Text and numeric operations

`$lowercase(s)`, `$uppercase(s)`, `$trim(s)`, `$substring(s,start[,length])`, `$contains(s,part)`, `$split(s,separator)`, `$replace(s,pattern,replacement)` operate on strings. `$trim` also collapses internal whitespace runs to one space; it is not just removal at the edges. Use a deliberate regex if internal spaces must be retained. Strings use `&` for concatenation. `$string(x)` serializes non-string JSON values as text; `$number(text)` parses a legal numeric string and throws on invalid text.

`/` is numeric division; there is no integer `//` operator. `$floor(a/b)` is floor division for positive divisors, including negative dividends; truncation toward zero instead requires `a/b < 0 ? $ceil(a/b) : $floor(a/b)`. `%` is remainder. `$abs`, `$floor`, `$ceil`, `$round(x[,precision])`, `$power`, and `$sqrt` are available. `$round` uses half-to-even; `$round(6.5)` is 6, `$round(7.5)` is 8. Numeric arithmetic is not an arbitrary-precision decimal guarantee; use bounded integer arithmetic when exact whole units are required.

## Limits and reproducibility

This is the real JVM port, not an ad hoc JSONata translator. Do not depend on Java/Kotlin functions inside expressions or functions supplied by an imagined host. Avoid ambient `$now`, `$millis`, `$random`, and `$shuffle` for deterministic transformations. A type error aborts evaluation; a missing sequence is not a general error-recovery mechanism.

Source links and pinned-engine probe details are in `SOURCES.md`. The reference uses the stable language surface documented by JSONata 1.8 and directly checks array/null/sort behavior on Dashjoin 0.9.10; newer upstream documentation is not a promise of port support.

All fenced snippets and concrete inline syntax examples were checked through the actual experiment evaluator with synthetic inputs; fragment wrappers and outputs are recorded in `SOURCES.md`.
