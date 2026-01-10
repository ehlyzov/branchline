package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.Parser
import io.github.ehlyzov.branchline.ExecutionEngine
import io.github.ehlyzov.branchline.Lexer
import io.github.ehlyzov.branchline.TransformDecl
import io.github.ehlyzov.branchline.runtime.io.readText
import io.github.ehlyzov.branchline.ir.compileStream

/**
 * CLI runner that executes a Branchline script on the VM in a loop.
 *
 * Usage:
 *   ./gradlew run --args "<script> [iterations]"
 *
 * If iterations are omitted, the script runs until interrupted.
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: ./gradlew run --args \"<script> [iterations]\"")
        return
    }
    val scriptPath = args[0]
    val iterations = args.getOrNull(1)?.toIntOrNull()

    val code = readText(scriptPath)
    val program = Parser(Lexer(code).lex(), code).parse()
    val transform = program.decls.filterIsInstance<TransformDecl>().first()
    val runner = compileStream(transform, engine = ExecutionEngine.VM)

    if (iterations != null) {
        repeat(iterations) { runner(emptyMap()) }
    } else {
        while (true) { runner(emptyMap()) }
    }
}
