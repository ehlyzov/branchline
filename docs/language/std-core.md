---
title: Core Standard Library
---

# Core

Utilities for working with objects, arrays, and iterables.

## Functions
- `KEYS(coll)` → list of keys or indices.
- `VALUES(obj)` → list of values from an object.
- `ENTRIES(obj)` → list of `{ key, value }` pairs.
- `GET(obj, key[, default])` → value at key or `default` when missing.
- `PUT(coll, key, value)` → new list/object with entry added or replaced.
- `DELETE(coll, key)` → remove an entry by key/index.
- `APPEND(list, value)` / `PREPEND(list, value)` → new lists with value added.
- `WALK(tree)` → iterable of nodes with `path`, `key`, `value`, and `depth`.
- `COLLECT(iterable)` → list from a sequence/iterable.
- `IS_OBJECT(x)` → true if value is a map/object.
- `LISTIFY(x)` → `[]` for null, list as-is, or `[x]`.
- `PRINT(...args)` → write to stdout and return `null` (CLI/runtime only).

## Examples
- [KEYS/VALUES/ENTRIES](../playground.md?example=stdlib-core-keys-values)
- [PUT/DELETE](../playground.md?example=stdlib-core-put-delete)
- [APPEND/PREPEND/COLLECT](../playground.md?example=stdlib-core-append-prepend)
- [WALK](../playground.md?example=stdlib-core-walk)
- [LISTIFY/GET](../playground.md?example=stdlib-core-listify-get)

## Notes
- Object helpers error if used on non-objects.
- List helpers require valid indices (append at `size`).

## Related
- [Arrays](std-arrays.md)
- [Higher-Order Functions](std-hof.md)
