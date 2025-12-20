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

topDecl          ::= outputDecl | transformDecl | funcDecl
                   | typeDecl  | sharedDecl | **;**              ;

outputDecl  ::= **OUTPUT** adapterSpec? templateBlock           ;
#            e.g.  OUTPUT USING xmlFile("out.xml") { … }
#                  OUTPUT { … }      # defaults to jsonStream(stdout)

adapterSpec ::= **USING** adapterCall                          ;
adapterCall ::= IDENTIFIER "(" argList? ")"                    ;
#             └──────┬──────┘
#              adapter name      (jsonFile, xmlFile, rdfEndpoint, …)

# Example (not grammar):
#   OUTPUT         USING xmlFile("out.xml") { … }
#   OUTPUT { … }                      # → defaults to jsonStream(stdout)

#---------------------------------------------------------------------------
# Transforms
#---------------------------------------------------------------------------

transformDecl    ::= annotation* **TRANSFORM** IDENTIFIER? transformMode?
                     block                                                   ;

annotation       ::= **@** IDENTIFIER                                        ;
transformMode    ::= "{" **buffer** "}"                                    ;

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
typeExpr         ::= **enum** "{" enumVal ("," enumVal)* "}"                 |
                     simpleType ( "|" simpleType )*                          ;
enumVal          ::= IDENTIFIER                                              ;
simpleType       ::= **string** | **number** | **boolean** | **null**
                   | **array** "<" simpleType ">" | IDENTIFIER               ;

#---------------------------------------------------------------------------
# Blocks  (braces **or** off-side  INDENT / DEDENT tokens)
#---------------------------------------------------------------------------

block            ::= "{" statement* "}"  |  INDENT statement+ DEDENT         ;

statement        ::= letStmt | ifStmt | forStmt | tryStmt | callStmt
                   | sharedWrite | suspendStmt | abortStmt | throwStmt
                   | nestedOutput | expressionStmt | **;**                   ;

letStmt          ::= **LET** IDENTIFIER "=" expression **;**                 ;
ifStmt           ::= **IF** expression block ( **ELSE** block )?             ;
forStmt          ::= ( **FOR EACH** | **FOR** ) IDENTIFIER **IN** expression block ;
tryStmt          ::= **TRY** expression **CATCH** "(" IDENTIFIER ")" "=>" expression ;
callStmt         ::= optAwait **CALL** IDENTIFIER "(" argList? ")" arrow IDENTIFIER **;** ;
optAwait         ::= **AWAIT**?                                              ;
arrow            ::= "->" | "=>"                                             ;
sharedWrite      ::= IDENTIFIER "[" expression? "]" "=" expression **;**     ;
suspendStmt      ::= **SUSPEND** expression **;**                            ;
abortStmt       ::= 'ABORT' expression? ';'                                  ;
throwStmt       ::= 'THROW' expression? ';'                                  ;
nestedOutput     ::= **OUTPUT** templateBlock                                ;
expressionStmt   ::= expression **;**                                        ;

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
                   | "[" **FOR** "(" IDENTIFIER **IN** expression ")"
                     ( **IF** expression )? "=>" expression "]"              ;

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
# Templates  (JSON shown; XML/RDF T.B.D.)
#---------------------------------------------------------------------------

templateBlock    ::= objectLit | xmlTemplate | rdfTemplate                   ;
xmlTemplate      ::= /* TBD */                                               ;
rdfTemplate      ::= /* TBD */                                               ;
──────────────────────────────────────────────────────────────────────────────
```

The grammar file above is exercised by tests to stay synchronized with the implementation【F:language/src/test/kotlin/v2/ebnf.txt†L1-L153】.
