---
title: Lexical Structure
---

# Lexical Structure

This page outlines the tokens recognized by the Branchline lexer. Tokens are grouped into operators & punctuators, literals, and keywords.

## Operators & Punctuators

| Token(s) | Lexeme(s) | Example |
|---|---|---|
| LEFT_PAREN / RIGHT_PAREN | `(` `)` | `call(arg)` |
| LEFT_BRACE / RIGHT_BRACE | `{` `}` | `{ x: 1 }` |
| LEFT_BRACKET / RIGHT_BRACKET | `[` `]` | `items[0]` |
| COMMA | `,` | `a, b` |
| DOT | `.` | `obj.field` |
| SEMICOLON | `;` | `let x = 1;` |
| COLON | `:` | `name: Type` |
| QUESTION | `?` | `value?` |
| COALESCE | `??` | `a ?? b` |
| PLUS | `+` | `a + b` |
| MINUS | `-` | `a - b` |
| STAR | `*` | `a * b` |
| SLASH | `/` | `a / b` |
| PERCENT | `%` | `a % b` |
| CONCAT | `++` | `xs ++ ys` |
| LT / LE | `<` `<=` | `a <= b` |
| GT / GE | `>` `>=` | `a > b` |
| ASSIGN | `=` | `x = 1` |
| EQ / NEQ | `==` `!=` | `a != b` |
| BANG | `!` | `!flag` |
| AND | `&&` | `a && b` |
| OR | `||` | `a || b` |
| ARROW | `->` | `x -> x * 2` |
| PIPE | `|` | `src | step` |
| DOLLAR | `$` | `$name` |

## Literals

| Token | Example | Description |
|---|---|---|
| IDENTIFIER | `userName` | Name for variables or functions. |
| STRING | `"hello"` | Text enclosed in double quotes. |
| NUMBER | `42` | Integer or floating-point number. |

## Keywords

Reserved words. Each links to a page with usage examples.

### Statement Keywords

| Keyword | Description | Usage |
|---|---|---|
| `OUTPUT` | Specify pipeline output. | [example](statements.md#output) |
| `USING` | Reference an adapter or module. | [example](statements.md#using) |
| `TRANSFORM` | Define a transformation step. | [example](statements.md#transform) |
| `BUFFER` | Declare a buffer block. | [example](statements.md#transform) |
| `FOR` / `EACH` | Start a loop over items. | [example](statements.md#for) |
| `IF` / `THEN` / `ELSE` | Conditional branching. | [example](statements.md#if) |
| `ENUM` | Define an enumeration. | [example](statements.md#enum) |
| `FOREACH` | Loop shortcut. | [example](statements.md#foreach) |
| `INPUT` | Reference pipeline input. | [example](statements.md#input) |
| `ABORT` | Abort execution. | [example](statements.md#abort) |
| `THROW` | Throw an error. | [example](statements.md#throw) |
| `TRY` / `CATCH` | Error handling block. | [example](statements.md#try) |
| `RETRY` / `TIMES` / `BACKOFF` | Retry logic modifiers. | [example](statements.md#try) |
| `SHARED` / `SINGLE` / `MANY` | Resource qualifiers. | [example](statements.md#shared) |
| `FUNC` | Declare a function. | [example](statements.md#func) |
| `TYPE` | Declare a type. | [example](statements.md#type) |
| `RETURN` | Return from function. | [example](statements.md#return) |
| `MODIFY` | Modify an existing value. | [example](statements.md#modify) |
| `WHERE` | Filter clause. | [example](statements.md#where) |
| `SET` / `APPEND` / `TO` | Assignment operations. | [example](statements.md#set) |
| `INIT` | Initial value for a variable. | [example](statements.md#init) |

### Expression Keywords

| Keyword | Description | Usage |
|---|---|---|
| `AS` | Alias or cast. | [example](expressions.md#as) |
| `LET` / `IN` | Local binding expression. | [example](expressions.md#let) |
| `AWAIT` | Await asynchronous result. | [example](expressions.md#await) |
| `SUSPEND` | Suspend execution. | [example](expressions.md#suspend) |
| `CALL` | Invoke a host function. | [example](expressions.md#call) |
| `TRUE` / `FALSE` | Boolean literals. | [example](expressions.md#literals) |
| `NULL` | Null literal. | [example](expressions.md#literals) |
| `UNION` | Union type expression. | [example](expressions.md#union) |

## Special Token

`EOF` marks the end of the input.

# Summary

Branchline uses a small set of tokens. Keywords are written in upper case and
include `OUTPUT`, `TRANSFORM`, `BUFFER`, `SHARED`, `FUNC`, `TYPE`, `LET`,
`IF`, `FOR`, `TRY`, `CALL`, `AWAIT`, and `SUSPEND`, among others
as seen throughout the grammar【F:language/src/test/kotlin/v2/ebnf.txt†L22-L90】.

Names are expressed using identifiers:
`funcDecl ::= FUNC IDENTIFIER "(" paramList? ")" funcBody`
shows identifiers applied to declarations【F:language/src/test/kotlin/v2/ebnf.txt†L45-L63】.

Literals support numbers, strings, booleans, and null via the rule
`literal ::= NUMBER | STRING | TRUE | FALSE | NULL`【F:language/src/test/kotlin/v2/ebnf.txt†L112-L116】.

Punctuation such as parentheses, braces, brackets, commas, and semicolons
structure programs and appear in the production rules.
