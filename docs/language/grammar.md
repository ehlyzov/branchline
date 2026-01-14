---
title: Grammar
---

# Branchline Grammar

Use this page for the exact, authoritative syntax. If you only need to learn the language, start with the [Tour of Branchline](../learn/index.md).

The Branchline DSL syntax is defined using Extended Backus–Naur Form (EBNF). The canonical grammar is maintained in the repository at
[interpreter/src/jvmTest/resources/io/github/ehlyzov/branchline/ebnf.txt](https://github.com/ehlyzov/branchline-public/blob/main/interpreter/src/jvmTest/resources/io/github/ehlyzov/branchline/ebnf.txt)
and is reproduced below for convenience.

```ebnf
──────────────────────────────────────────────────────────────────────────────
program          ::= versionDecl? importDecl* topDecl* EOF ;

versionDecl      ::= "#!branchline" VERSION                                  ;

importDecl       ::= **IMPORT** STRING ( **AS** name )? **;**                ;

topDecl          ::= transformDecl | funcDecl | typeDecl | sharedDecl | **;** ;

#---------------------------------------------------------------------------
# Lexical helpers
#---------------------------------------------------------------------------

# IDENTIFIER also includes backtick-escaped names (for example, `if`).
name             ::= IDENTIFIER | softKeyword                               ;
softKeyword      ::= **ABORT** | **APPEND** | **AS** | **BACKOFF**
                   | **buffer** | **CALL** | **enum** | **FOREACH**
                   | **FUNC** | **INIT** | **LET** | **MANY**
                   | **MODIFY** | **OUTPUT** | **RETRY** | **RETURN**
                   | **SET** | **SHARED** | **SINGLE** | **SOURCE**
                   | **stream** | **THROW** | **TIMES** | **TO**
                   | **TRANSFORM** | **TYPE** | **union** | **USING**       ;

#---------------------------------------------------------------------------
# Transforms
#---------------------------------------------------------------------------

transformDecl    ::= annotation* **TRANSFORM** name? transformSig?
                     transformMode? block                                     ;

annotation       ::= **@** name                                              ;
transformMode    ::= "{" ( **stream** | **buffer** ) "}"                    ;
transformSig     ::= "(" transformParamList? ")" "->" typeExpr              ;
transformParamList ::= transformParam ( "," transformParam )*               ;
transformParam   ::= name ":" typeExpr                                       ;

#---------------------------------------------------------------------------
# Shared memory
#---------------------------------------------------------------------------

sharedDecl       ::= **SHARED** name ( **SINGLE** | **MANY** )? **;**        ;

#---------------------------------------------------------------------------
# Functions & Types
#---------------------------------------------------------------------------

funcDecl         ::= **FUNC** name "(" paramList? ")" funcBody               ;
paramList        ::= name ( "," name )*                                      ;
funcBody         ::= "=" expression | block                                  ;

typeDecl         ::= **TYPE** name "=" typeExpr **;**                        ;
typeExpr         ::= typeTerm ( "|" typeTerm )*                              ;
typeTerm         ::= enumType | recordType | listType
                   | placeholderType | simpleType                            ;
enumType         ::= **enum** "{" enumVal ("," enumVal)* "}"                 ;
# "enum" is treated as a type literal only when followed by "{"
enumVal          ::= name                                                    ;
recordType       ::= "{" recordField ("," recordField)* "}"                  ;
recordField      ::= name ["?"] ":" typeExpr                                 ;
listType         ::= "[" typeExpr "]"                                        ;
placeholderType  ::= "_" ["?"]                                               ;
simpleType       ::= **string** | **number** | **boolean** | **null**
                   | name                                                    ;

#---------------------------------------------------------------------------
# Blocks  (braces **or** off-side  INDENT / DEDENT tokens)
#---------------------------------------------------------------------------

block            ::= "{" statement* "}"  |  INDENT statement+ DEDENT         ;

statement        ::= letStmt | ifStmt | forStmt | tryStmt | callStmt
                   | setStmt | appendStmt | modifyStmt | returnStmt
                   | sharedWrite | suspendStmt | abortStmt | throwStmt
                   | nestedOutput | expressionStmt | **;**                   ;

letStmt          ::= **LET** name "=" expression **;**                       ;
ifStmt           ::= **IF** expression block ( **ELSE** block )?             ;
forStmt          ::= ( **FOR EACH** | **FOR** ) name **IN** expression
                     ( **WHERE** expression )? block                        ;
tryStmt          ::= **TRY** expression **CATCH** "(" name ")" retrySpec? arrow expression ;
callStmt         ::= optAwait **CALL** name "(" argList? ")" arrow name **;** ;
optAwait         ::= **AWAIT**?                                              ;
arrow            ::= "->" | "=>"                                             ;
retrySpec        ::= **RETRY** NUMBER **TIMES** ( **BACKOFF** STRING )?       ;
setStmt          ::= **SET** setTarget "=" expression **;**                 ;
appendStmt       ::= **APPEND** **TO** setTarget expression
                     ( **INIT** expression )? **;**                          ;
modifyStmt       ::= **MODIFY** modifyTarget modifyBlock **;**               ;
returnStmt       ::= **RETURN** expression? **;**                            ;
sharedWrite      ::= name "[" expression? "]" "=" expression **;**           ;
suspendStmt      ::= **SUSPEND** expression **;**                            ;
abortStmt       ::= 'ABORT' expression? ';'                                  ;
throwStmt       ::= 'THROW' expression? ';'                                  ;
nestedOutput     ::= **OUTPUT** expression                                   ;
expressionStmt   ::= expression **;**                                        ;

setTarget        ::= name pathSeg*                                           ;
modifyTarget     ::= name ("." name)*                                        ;
modifyBlock      ::= "{" modifyField (("," | ";") modifyField)* [(","|";")] "}" ;
modifyField      ::= fieldKey ":" expression
                   | "[" expression "]" ":" expression                       ;

#---------------------------------------------------------------------------
# Expressions  (standard precedence ladder +  ??  coalesce)
#---------------------------------------------------------------------------

expression       ::= assignment                                              ;
assignment       ::= coalesce ( "=" assignment )?                            ;
coalesce         ::= logicOr ( "??" logicOr )*                               ;
logicOr          ::= logicAnd ( "||" logicAnd )*                             ;
logicAnd         ::= equality ( "&&" equality )*                             ;
equality         ::= comparison ( ( "==" | "!=" ) comparison )*              ;
comparison       ::= term ( ( "<" | ">" | "<=" | ">=" ) term )*              ;
term             ::= factor ( ( "+" | "-" ) factor )*                        ;
factor           ::= unary  ( ( "*" | "/" | "%" ) unary )*                   ;
unary            ::= ( "!" | "-" | **AWAIT** | **SUSPEND** ) unary
                   | primary                                                ;

primary          ::= literal | pathExpr | name | funCall | arrayLit
                   | objectLit | caseExpr | tryExpr | "(" expression ")" | lambdaExpr ;
tryExpr          ::= **TRY** expression **CATCH** "(" name ")" retrySpec? arrow expression ;

caseExpr         ::= **CASE** "{" caseWhen+ caseElse "}"                    ;
caseWhen         ::= **WHEN** expression **THEN** expression                ;
caseElse         ::= **ELSE** expression                                    ;

literal          ::= NUMBER | STRING | **TRUE** | **FALSE** | **NULL**       ;

funCall          ::= name "(" argList? ")"                                   ;
argList          ::= expression ( "," expression )*                          ;

lambdaExpr       ::= "(" paramList? ")" "->" expression                      ;

arrayLit         ::= "[" ( expression ("," expression)* )? "]"
                   | "[" expression **FOR EACH** name **IN** expression
                     ( **WHERE** expression )? "]"                           ;

objectLit        ::= "{" fieldPair ("," fieldPair)* "}"                      ;
fieldPair        ::= fieldKey ":" expression                                 ;
fieldKey         ::= name ["?"] | STRING                                     ;

#---------------------------------------------------------------------------
# Paths  ($, identifiers, wildcards, slices, filters)
#---------------------------------------------------------------------------

pathExpr         ::= "$"        pathSeg*                                     |
                     **INPUT**  pathSeg*                                     |
                     name pathSeg*                                           ;

pathSeg          ::= "." "*"?                    # *.name  or  .*
                   | "." name
                   | "[" slice  "]"
                   | "[" predicate "]"                                      ;

slice            ::= [ NUMBER ] ":" [ NUMBER ]                               ;
predicate        ::= expression                                              ;

#---------------------------------------------------------------------------
# Templates  (payloads are expressions)
#---------------------------------------------------------------------------
──────────────────────────────────────────────────────────────────────────────
```

The grammar file above is exercised by tests to stay synchronized with the implementation.

## Related
- [Lexical Structure](lexical.md)
- [Statements](statements.md)
- [Expressions](expressions.md)
