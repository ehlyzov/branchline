package io.github.ehlyzov.branchline.testutils

import io.github.ehlyzov.branchline.ExecutionEngine
import io.github.ehlyzov.branchline.FuncDecl
import io.github.ehlyzov.branchline.Lexer
import io.github.ehlyzov.branchline.Parser
import io.github.ehlyzov.branchline.TransformDecl
import io.github.ehlyzov.branchline.ir.compileStream
import io.github.ehlyzov.branchline.sema.SemanticAnalyzer
import io.github.ehlyzov.branchline.std.StdFn
import io.github.ehlyzov.branchline.std.StdLib

private fun compileProgramToRunner(
    code: String,
    extraFns: Map<String, StdFn>,
    engine: ExecutionEngine,
    runSema: Boolean = true,
): (Map<String, Any?>) -> Any? {
    val tokens = Lexer(code).lex()
    val prog = Parser(tokens, code).parse()

    val funcs = prog.decls
        .filterIsInstance<FuncDecl>()
        .associateBy { it.name }

    val hostFns = StdLib.fns + extraFns
    if (runSema) {
        SemanticAnalyzer(hostFns.keys).analyze(prog)
    }

    val transform = prog.decls.filterIsInstance<TransformDecl>().single()
    return compileStream(transform, funcs = funcs, hostFns = hostFns, engine = engine)
}

fun compileAndRun(
    body: String,
    row: Map<String, Any?> = emptyMap(),
    extraFns: Map<String, StdFn> = emptyMap(),
    engine: ExecutionEngine = ExecutionEngine.INTERPRETER,
): Any? {
    val code = """
        TRANSFORM __T {
            $body
        }
    """.trimIndent()
    val runner = compileProgramToRunner(code, extraFns, engine, runSema = true)
    return runner(row)
}

fun buildRunner(
    body: String,
    extraFns: Map<String, StdFn> = emptyMap(),
    engine: ExecutionEngine = ExecutionEngine.INTERPRETER,
): (Map<String, Any?>) -> Any? {
    val code = """
        TRANSFORM __T {
            $body
        }
    """.trimIndent()
    return compileProgramToRunner(code, extraFns, engine, runSema = true)
}

fun buildRunnerUnchecked(
    body: String,
    extraFns: Map<String, StdFn> = emptyMap(),
    engine: ExecutionEngine = ExecutionEngine.INTERPRETER,
): (Map<String, Any?>) -> Any? {
    val code = """
        TRANSFORM __T {
            $body
        }
    """.trimIndent()
    return compileProgramToRunner(code, extraFns, engine, runSema = false)
}

fun compileAndRunUnchecked(
    body: String,
    row: Map<String, Any?> = emptyMap(),
    extraFns: Map<String, StdFn> = emptyMap(),
    engine: ExecutionEngine = ExecutionEngine.INTERPRETER,
): Any? {
    val runner = buildRunnerUnchecked(body, extraFns, engine)
    return runner(row)
}

fun buildRunnerFromProgram(
    program: String,
    extraFns: Map<String, StdFn> = emptyMap(),
    engine: ExecutionEngine = ExecutionEngine.INTERPRETER,
): (Map<String, Any?>) -> Any? =
    compileProgramToRunner(program, extraFns, engine, runSema = false)

fun compileProgramAndRun(
    program: String,
    row: Map<String, Any?> = emptyMap(),
    extraFns: Map<String, StdFn> = emptyMap(),
    engine: ExecutionEngine = ExecutionEngine.INTERPRETER,
): Any? {
    val runner = compileProgramToRunner(program, extraFns, engine)
    return runner(row)
}
