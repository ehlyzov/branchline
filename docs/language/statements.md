---
title: Statements
---

# Statements

Statements control execution and side effects within blocks.

## Quick patterns
- Use `LET` for new bindings and `SET` for mutation inside loops.
- Guard code with `IF` and `TRY/CATCH`; pair `ASSERT`/`THROW` for explicit failures.
- `FOR`/`FOR EACH` iterate collections; comprehensions in expressions provide a shorthand for building arrays.
- `OUTPUT { ... }` shapes final payloads; nested `OUTPUT` is allowed for intermediate blocks.
- `CALL` bridges to host functions; `AWAIT`/`SUSPEND` require host support for async/concurrency.

## Output {#output}

The `OUTPUT` statement specifies how results are emitted from the pipeline.

## Using {#using}

The `USING` clause references adapters and external modules.

## Transform {#transform}

The `TRANSFORM` statement defines transformation steps in the pipeline. Use
`TRANSFORM Name { ... }` or include `{ buffer }` after the name to mark buffer
mode explicitly.

## For Loops {#for}

The `FOR` and `FOR EACH` statements provide iteration over collections.

## If Statements {#if}

The `IF` statement provides conditional branching.

## Enumerations {#enum}

The `ENUM` statement defines enumerated types.

## For Each {#foreach}

The `FOREACH` statement provides a shortcut for iteration.

## Input {#input}

The `INPUT` keyword references the pipeline input data (alias: `input`).

## Abort {#abort}

The `ABORT` statement terminates execution immediately.

## Throw {#throw}

The `THROW` statement raises an exception.

## Try {#try}

The `TRY` statement handles errors and exceptions.

## Call {#call}

The `CALL` statement invokes host-provided functions and binds the result to
an identifier.

Example:

```branchline
CALL inventoryService(input) -> payload;
```

## Shared {#shared}

The `SHARED` statement declares shared memory resources.

To wait for a shared entry from within a program, use the `AWAIT_SHARED(resource, key)` stdlib function (requires a configured shared store in the host). This cannot be demonstrated in the playground because no shared store is wired there.

Example:

```branchline
SHARED session MANY;

session["lastSeen"] = NOW();
session["userId"] = input.user.id;
```

For `SINGLE` shared resources, you can omit the key (`cache[] = value`) and the default key is used. `MANY` resources require an explicit key.

## Functions {#func}

The `FUNC` statement declares functions.

## Types {#type}

The `TYPE` statement declares custom types.

## Return {#return}

The `RETURN` statement exits from functions.

```
returnStmt ::= RETURN expression? ";"
```

## Modify {#modify}

The `MODIFY` statement changes existing values.

```
modifyStmt ::= MODIFY modifyTarget modifyBlock ";"
```

## Where {#where}

The `WHERE` clause provides filtering conditions.

```
forStmt   ::= ( FOR EACH | FOR ) IDENTIFIER IN expression ( WHERE expression )? block
arrayComp ::= "[" expression FOR EACH IDENTIFIER IN expression ( WHERE expression )? "]"
```

## Set {#set}

The `SET` statement performs assignments.

```
setStmt ::= SET setTarget "=" expression ";"
```

## Init {#init}

The `INIT` statement provides initial values.

```
appendStmt ::= APPEND TO setTarget expression ( INIT expression )? ";"
```

```
statement ::= letStmt | ifStmt | forStmt | tryStmt | callStmt
            | setStmt | appendStmt | modifyStmt | returnStmt
            | sharedWrite | suspendStmt | abortStmt | throwStmt
            | nestedOutput | expressionStmt | ;
```

## Variable binding

```
letStmt ::= LET IDENTIFIER "=" expression ";"
```

`LET` introduces a new variable bound to the result of an expression.

## Control flow

```
ifStmt ::= IF expression block ( ELSE block )?
forStmt ::= ( FOR EACH | FOR ) IDENTIFIER IN expression
            ( WHERE expression )? block
```

Conditionals and loops evaluate expressions to drive branching and iteration.

## Error handling

```
tryStmt ::= TRY expression CATCH "(" IDENTIFIER ")" retrySpec? arrow expression
retrySpec ::= RETRY NUMBER TIMES ( BACKOFF STRING )?
```

`TRY` evaluates an expression and binds an error to the given identifier if one
occurs. The same syntax is available as an expression; see
[Expressions](expressions.md#try).

## Calls and mutation

```
callStmt    ::= optAwait CALL IDENTIFIER "(" argList? ")" arrow IDENTIFIER ";"
setStmt     ::= SET setTarget "=" expression ";"
appendStmt  ::= APPEND TO setTarget expression ( INIT expression )? ";"
modifyStmt  ::= MODIFY modifyTarget modifyBlock ";"
returnStmt  ::= RETURN expression? ";"
sharedWrite ::= IDENTIFIER "[" expression? "]" "=" expression ";"
```

Call statements invoke suspended functions, while `sharedWrite` mutates shared
memory slots.

## Mutation statements

```
setStmt    ::= SET setTarget "=" expression ";"
appendStmt ::= APPEND TO setTarget expression ( INIT expression )? ";"
modifyStmt ::= MODIFY modifyTarget modifyBlock ";"
```

`SET` assigns values to variables or paths, `APPEND TO` pushes values into lists
(optionally seeding with `INIT`), and `MODIFY` applies updates to a static
object path.

## Suspension and termination

```
suspendStmt ::= SUSPEND expression ";"
abortStmt  ::= ABORT expression? ";"
throwStmt  ::= THROW expression? ";"
```

These statements pause or terminate execution, optionally carrying an
expression.

## Nested output and expressions

```
nestedOutput   ::= OUTPUT expression
expressionStmt ::= expression ";"
```

Nested `OUTPUT` blocks and bare expressions complete the statement set.

## Constraints

- `TRY` wraps expressions and binds errors to the identifier in `CATCH`.
- `CALL`, `AWAIT`, and `SUSPEND` require host support to execute.
- `SHARED` resources must be declared before use and rely on host-provided storage.

## Performance tips

- Bind frequently used paths with `LET` to avoid repeated traversal.
- Prefer `FOREACH`/comprehensions over manual index loops when shaping arrays.
- Keep `OUTPUT` blocks focused; build intermediate objects with `LET` for clarity.
