---
title: Core Standard Library
---

# Core

Utilities for working with objects and arrays.

## Functions
- `KEYS(coll)` → list of keys/indices. Lists return indices `[0,1,...]`. Errors on non list/object.
- `VALUES(obj)` → list of values from an object. Errors if not an object.
- `ENTRIES(obj)` → list of `{ key, value }` pairs. Errors if not an object.
- `GET(obj, key[, default])` → value at key or `default` when missing. Errors if not an object.
- `PUT(coll, key, value)` → new list/object with entry added or replaced. List indexes must be within `0..size` (append when equal to size).
- `DELETE(coll, key)` → remove an entry by key/index. Errors if out of bounds or unsupported type.
- `APPEND(list, value)` / `PREPEND(list, value)` → new lists with value at the end/start.
- `WALK(tree)` → sequence describing each node: `path`, `key`, `value`, `depth`, `isLeaf`. Works on objects and lists; other values yield a single leaf entry.
- `COLLECT(iterable)` → list from a sequence/iterable. Errors on unsupported types.
- `IS_OBJECT(x)` → boolean indicating object-ness (maps only).
- `LISTIFY(x)` → `[]` for `null`, list as-is, or `[object]`. Errors on unsupported types.
- `PRINT(...args)` → writes to stdout, returns `null`. Use in CLI/runtime environments; not demonstrated in the playground.

## Examples
- [stdlib-core-keys-values](../playground.md?example=stdlib-core-keys-values)
- [stdlib-core-put-delete](../playground.md?example=stdlib-core-put-delete)
- [stdlib-core-append-prepend](../playground.md?example=stdlib-core-append-prepend)
- [stdlib-core-walk](../playground.md?example=stdlib-core-walk)
- [stdlib-core-listify-get](../playground.md?example=stdlib-core-listify-get)
