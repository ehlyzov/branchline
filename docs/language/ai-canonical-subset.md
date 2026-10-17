---
title: AI Canonical Subset
---

# AI Canonical Subset

Use this subset when generating Branchline from tools, agents, examples, or repair loops. It is stricter than the parser: accepted legacy syntax may still run, but AI-facing output should use one predictable style.

## Authoring workflow

Generate a program, inspect it, then run it:

```bash
bl inspect program.bl --contracts --contracts-json --normalized
```

Use the normalized source as the canonical version when it is present. If `normalizedSource` is `null`, read diagnostics and warnings first; null can mean normalization was not requested, the source used unsupported subset features, or the normalizer could not render a node safely.

For a visual loop, open the playground and filter the catalog to **AI compatible** examples. Run an example, then use the Inspect workbench tabs for normalized source, diagnostics, and subset blockers. This mirrors the CLI inspect-first flow without replacing the CLI JSON contract used by automation.

## Inspect JSON envelope

`bl inspect --contracts --contracts-json` returns a stable machine envelope:

```json
{
  "success": true,
  "diagnostics": [],
  "warnings": [],
  "featureUsage": { "features": ["output", "object"] },
  "subsetCompatibility": "COMPATIBLE",
  "normalizedSource": null,
  "transforms": [
    {
      "name": "Main",
      "contractSource": "inferred",
      "explicitContract": null,
      "inferredContract": {},
      "mergedContract": {},
      "witness": null
    }
  ]
}
```

For single-transform programs, the root object also keeps the historical contract fields such as `input`, `output`, `name`, and `source` so existing contract consumers can migrate incrementally.

Envelope evolution is additive by default. Consumers should ignore unknown fields, treat enum additions as possible, and depend on `success`, `diagnostics`, `warnings`, `featureUsage`, `subsetCompatibility`, `normalizedSource`, and `transforms` as the stable root contract.

Diagnostics keep stable base fields: `code`, `message`, `severity`, `span`, `category`, and `payload`. Payload keys such as `operation`, `targetPath`, `expectedKind`, `actualKind`, `expected`, `actual`, and `hint` are optional and additive.

## Canonical constructs

| Area | Generate this |
| --- | --- |
| Input root | `input` |
| Statements | One statement per line, no trailing semicolons |
| Loops | `FOR EACH item IN items WHERE condition { ... }` |
| Direct collection shaping | Array comprehensions or collection expressions |
| Explicit accumulator append | `items += item` after `LET items = []` |
| Local replacement | `SET name = expression` |
| Path replacement | `SET object.path = expression` when parents already exist |
| Grouped object update | `MODIFY object.path { key: value }` |
| Pure list expression | `APPEND(list, value)` only when an expression value is needed |
| Output | `OUTPUT { ... }` |

## Accepted but not canonical

These forms may parse or run for compatibility, but canonical tools should not emit them:

| Accepted form | Canonical form |
| --- | --- |
| `row.name` | `input.name` |
| `LET x = 1;` | `LET x = 1` |
| `FOR item IN input.items { ... }` | `FOR EACH item IN input.items { ... }` |
| `SET items = APPEND(items, item)` | `items += item` |
| `APPEND(list, value)` for accumulation | `+=` or a collection expression |

`APPEND(list, value)` remains the canonical expression-style function when a new list value is needed inside another expression:

```branchline
LET next = APPEND(input.tags ?? [], "verified")
OUTPUT { tags: next }
```

## Unsupported in the AI subset

Do not generate orchestration or host-coupled constructs in the AI canonical subset MVP:

- `SHARED` declarations and shared writes
- `AWAIT` or shared awaits
- `SUSPEND`
- graph orchestration declarations
- ambient clock and randomness through registered `NOW()` / `RANDOM()` calls
- hidden in-place mutation or implicit creation of missing intermediate containers

The parser may support some of these constructs for broader language use. Inspect should classify them as unsupported for AI authoring rather than treating them as canonical style.

## Mutation patterns

| Intent | Preferred pattern |
| --- | --- |
| Map/filter an input list into an output list | Use an array comprehension or collection expression |
| Append one item to an explicit local accumulator | `LET out = []` then `out += item` |
| Append into a nested local accumulator | `state.items += item` after `state.items` already exists |
| Build a new list as an expression value | `APPEND(list, value)` |
| Replace a value | `SET target = value` |

`+=` does not declare variables and does not create missing parent objects or lists. Initialize accumulators explicitly with `LET` or an object literal before updating them.

## Deterministic authoring boundary

Pass timestamps, event identifiers and random choices through `input` when a transformation
must be reproducible. Inspect reports `unsupported_in_ai_subset` with category
`unsupported-subset`, operation `ai-subset-check`, a source span, and `actualKind` of `NOW`
or `RANDOM` for ambient calls. Normalized source is withheld for incompatible programs.

The check follows direct calls from the selected transforms into named `FUNC` bodies, including
indirect wrappers and recursive cycles. It also checks lambda bodies, call arguments and dynamic
access keys. Uncalled named functions and unselected transforms are excluded; the check does
not prove that a branch is reachable for a particular input. Lambda bodies are checked even
when the lambda is not invoked. Known lambda bindings and lambda arguments shadow named functions during
this analysis, so pure callbacks do not inherit an unrelated function's ambient calls.
Scalar or uncertain mutable bindings may fall back to a named function at runtime;
inspect conservatively checks that function too. This can reject programs whose purity
depends on dynamic callback types or mutable bindings.

Registered host functions have priority for direct calls in the current runtime: declaring
`FUNC NOW()` or `LET NOW = () -> 7` does not replace the clock used by `NOW()`. Inspect
therefore still rejects that call. Reading a local `NOW` value, or calling an object property
containing a pure lambda (such as `object.RANDOM()`), is allowed. Bare named `FUNC` and host
identifiers are not first-class function values in the interpreter: use a lambda wrapper when
passing one to `MAP` or another higher-order function. Such wrappers are checked.

Compatibility is an authoring profile, not a security sandbox or a proof of termination,
resource bounds, successful execution, or purity of host-supplied callable inputs. Dynamic
callbacks and host capabilities require separate isolation and validation. The local pilot
uses repository-controlled transforms; accepting arbitrary untrusted programs needs host
resource limits and a capability boundary.
