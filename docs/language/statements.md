---
title: Statements
---

# Statements

Statements control execution and side effects within blocks.

## Quick patterns
- Use `LET` for new bindings and `SET` for mutation.
- Guard code with `IF` and `TRY/CATCH`.
- `FOR EACH` iterates collections; comprehensions provide shorthand for arrays.
- `OUTPUT { ... }` shapes final payloads.

## Output {#output}
`OUTPUT` specifies how results are emitted from the pipeline.

```branchline
OUTPUT { id: input.id, total: input.total };
```

## Using {#using}
`USING` references adapters and external modules.

## Transform {#transform}
`TRANSFORM` defines transformation steps. Use `TRANSFORM Name { ... }` or include `{ buffer }` after the name to mark buffer mode explicitly.

## For loops {#for}
`FOR` and `FOR EACH` iterate over collections.

```branchline
FOR EACH item IN input.items WHERE item.qty > 0 {
    OUTPUT { name: item.name, qty: item.qty };
}
```

## If statements {#if}
`IF` provides conditional branching.

```branchline
IF input.enabled {
    OUTPUT { status: "enabled" };
} ELSE {
    OUTPUT { status: "disabled" };
}
```

## Enumerations {#enum}
`ENUM` defines enumerated types inside `TYPE` declarations.

## For each shorthand {#foreach}
`FOREACH` is a shorthand loop form.

## Input {#input}
`INPUT` references the pipeline input data.

## Abort {#abort}
`ABORT` terminates execution immediately.

## Throw {#throw}
`THROW` raises an error.

## Try/Catch {#try}
`TRY` handles errors and exceptions.

```branchline
TRY {
    OUTPUT { value: input.value };
} CATCH {
    OUTPUT { value: null };
}
```

## Call {#call}
`CALL` invokes host-provided functions.

```branchline
CALL inventoryService(input) -> payload;
```

## Shared {#shared}
`SHARED` declares shared memory resources.

```branchline
SHARED session MANY;
```

## Functions {#func}
`FUNC` declares reusable functions.

## Types {#type}
`TYPE` declares custom types.

## Return {#return}
`RETURN` exits from functions.

## Modify {#modify}
`MODIFY` changes existing values.

## Where {#where}
`WHERE` filters loop iterations and comprehensions.

## Set/Append/Init {#set}
`SET`, `APPEND`, `TO`, and `INIT` are assignment operations used in loops and shared writes.

## Init {#init}
`INIT` provides an initial value when appending to a missing target.

## Related
- [Expressions](expressions.md)
- [Declarations](declarations.md)
- [FOR EACH Loops](../guides/for-each.md)
