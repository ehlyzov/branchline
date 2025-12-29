---
title: Grammar
---

# Branchline Grammar

The Branchline DSL syntax is defined using Extended Backus–Naur Form (EBNF).
The canonical grammar is maintained in the repository at
[interpreter/src/jvmTest/resources/v2/ebnf.txt](https://github.com/ehlyzov/branchline-public/blob/main/interpreter/src/jvmTest/resources/v2/ebnf.txt)
and is reproduced below for convenience.

```ebnf
──────────────────────────────────────────────────────────────────────────────
program          ::= versionDecl? importDecl* topDecl* EOF ;

versionDecl      ::= "#!branchline" VERSION                                  ;

importDecl       ::= **IMPORT** STRING ( **AS** IDENTIFIER )? **;**          ;

topDecl          ::= transformDecl | funcDecl | typeDecl | sharedDecl | **;** ;

#---------------------------------------------------------------------------
# Transforms
#---------------------------------------------------------------------------

transformDecl    ::= annotation* **TRANSFORM** IDENTIFIER? transformSig?
                     transformMode? block                                     ;

annotation       ::= **@** IDENTIFIER                                        ;
transformMode    ::= "{" ( **stream** | **buffer** ) "}"                    ;
transformSig     ::= "(" transformParamList? ")" "->" typeExpr              ;
transformParamList ::= transformParam ( "," transformParam )*               ;
transformParam   ::= IDENTIFIER ":" typeExpr                                 ;

#---------------------------------------------------------------------------
# Shared memory
#---------------------------------------------------------------------------

sharedDecl       ::= **SHARED** IDENTIFIER ( **SINGLE** | **MANY** )? **;**  ;

#---------------------------------------------------------------------------
# Functions & Types
#---------------------------------------------------------------------------

funcDecl         ::= **FUNC** IDENTIFIER "(" paramList? ")" funcBody         ;
paramList        ::= IDENTIFIER ( "," IDENTIFIER )*                          ;
funcBody         ::= "=" expression | block                                  ;

typeDecl         ::= **TYPE** IDENTIFIER "=" typeExpr **;**                  ;
typeExpr         ::= typeTerm ( "|" typeTerm )*                              ;
typeTerm         ::= enumType | recordType | listType
                   | placeholderType | simpleType                            ;
enumType         ::= **enum** "{" enumVal ("," enumVal)* "}"                 ;
enumVal          ::= IDENTIFIER                                              ;
recordType       ::= "{" recordField ("," recordField)* "}"                  ;
recordField      ::= IDENTIFIER ["?"] ":" typeExpr                           ;
listType         ::= "[" typeExpr "]"                                        ;
placeholderType  ::= "_" ["?"]                                               ;
simpleType       ::= **string** | **number** | **boolean** | **null**
                   | IDENTIFIER                                              ;

#---------------------------------------------------------------------------
# Blocks  (braces **or** off-side  INDENT / DEDENT tokens)
#---------------------------------------------------------------------------

block            ::= "{" statement* "}"  |  INDENT statement+ DEDENT         ;

statement        ::= letStmt | ifStmt | forStmt | tryStmt | callStmt
                   | setStmt | appendStmt | modifyStmt | returnStmt
                   | sharedWrite | suspendStmt | abortStmt | throwStmt
                   | nestedOutput | expressionStmt | **;**                   ;

letStmt          ::= **LET** IDENTIFIER "=" expression **;**                 ;
ifStmt           ::= **IF** expression block ( **ELSE** block )?             ;
forStmt          ::= ( **FOR EACH** | **FOR** ) IDENTIFIER **IN** expression
                     ( **WHERE** expression )? block                        ;
tryStmt          ::= **TRY** expression **CATCH** "(" IDENTIFIER ")" "=>" expression ;
callStmt         ::= optAwait **CALL** IDENTIFIER "(" argList? ")" arrow IDENTIFIER **;** ;
optAwait         ::= **AWAIT**?                                              ;
arrow            ::= "->" | "=>"                                             ;
setStmt          ::= **SET** setTarget "=" expression **;**                 ;
appendStmt       ::= **APPEND** **TO** setTarget expression
                     ( **INIT** expression )? **;**                          ;
modifyStmt       ::= **MODIFY** modifyTarget modifyBlock                     ;
returnStmt       ::= **RETURN** expression? **;**                            ;
sharedWrite      ::= IDENTIFIER "[" expression? "]" "=" expression **;**     ;
suspendStmt      ::= **SUSPEND** expression **;**                            ;
abortStmt       ::= 'ABORT' expression? ';'                                  ;
throwStmt       ::= 'THROW' expression? ';'                                  ;
nestedOutput     ::= **OUTPUT** expression                                   ;
expressionStmt   ::= expression **;**                                        ;

setTarget        ::= IDENTIFIER pathSeg*                                     ;
modifyTarget     ::= IDENTIFIER ("." IDENTIFIER)*                            ;
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

primary          ::= literal | pathExpr | IDENTIFIER | funCall | arrayLit
                   | objectLit | caseExpr | "(" expression ")" | lambdaExpr ;

caseExpr         ::= **CASE** "{" caseWhen+ caseElse "}"                    ;
caseWhen         ::= **WHEN** expression **THEN** expression                ;
caseElse         ::= **ELSE** expression                                    ;

literal          ::= NUMBER | STRING | **TRUE** | **FALSE** | **NULL**       ;

funCall          ::= IDENTIFIER "(" argList? ")"                             ;
argList          ::= expression ( "," expression )*                          ;

lambdaExpr       ::= "(" paramList? ")" "->" expression                      ;

arrayLit         ::= "[" ( expression ("," expression)* )? "]"
                   | "[" expression **FOR EACH** IDENTIFIER **IN** expression
                     ( **WHERE** expression )? "]"                           ;

objectLit        ::= "{" fieldPair ("," fieldPair)* "}"                      ;
fieldPair        ::= fieldKey ":" expression                                 ;
fieldKey         ::= IDENTIFIER ["?"] | STRING                               ;

#---------------------------------------------------------------------------
# Paths  ($, identifiers, wildcards, slices, filters)
#---------------------------------------------------------------------------

pathExpr         ::= "$"        pathSeg*                                     |
                     **INPUT**  pathSeg*                                     |
                     IDENTIFIER pathSeg*                                     ;

pathSeg          ::= "." "*"?                    # *.name  or  .*
                   | "." IDENTIFIER
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
