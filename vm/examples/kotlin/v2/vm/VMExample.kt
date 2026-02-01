package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.*
import io.github.ehlyzov.branchline.ir.*
import io.github.ehlyzov.branchline.debug.*

/**
 * Example demonstrating the VM execution of Branchline DSL code
 */
class VMExample {
    
    fun runBasicCalculationExample() {
        println("=== Basic VM Calculation Example ===")
        
        // Simulate the Branchline DSL code:
        // LET total = 0;
        // SET total = 100 + 50;
        // OUTPUT { result: total };
        
        val token = Token(TokenType.NUMBER, "0", 0, 0)
        val ir = listOf(
            IRLet("total", NumberLiteral(I32(0), token)),
            IRSetVar("total", BinaryExpr(
                NumberLiteral(I32(100), token),
                Token(TokenType.PLUS, "+", 0, 0),
                NumberLiteral(I32(50), token)
            )),
        )
        
        // Create VM components
        val compiler = Compiler()
        val vm = VM()
        
        // Compile to bytecode
        println("Compiling IR to bytecode...")
        val bytecode = compiler.compile(ir)
        println("Generated ${bytecode.size()} instructions")

        // Execute with VM
        println("Executing with VM...")
        val env = mutableMapOf<String, Any?>()
        vm.execute(bytecode, env)
        val result = env["total"]
        println("Result (env.total): $result")
        println("Environment: $env")
    }
    
    fun runPerformanceComparisonExample() {
        println("\n=== Performance Comparison Example ===")
        
        // Create a more complex IR with loops and calculations
        val token = Token(TokenType.NUMBER, "0", 0, 0)
        val complexIR = listOf(
            IRLet("sum", NumberLiteral(I32(0), token)),
            IRLet("numbers", ArrayExpr(
                (1..10).map { NumberLiteral(I32(it), token) },
                token
            )),
            IRForEach(
                "num", 
                IdentifierExpr("numbers", token),
                null, // no where clause
                listOf(
                    IRSetVar("sum", BinaryExpr(
                        IdentifierExpr("sum", token),
                        Token(TokenType.PLUS, "+", 0, 0),
                        IdentifierExpr("num", token)
                    ))
                )
            ),
            IROutput(listOf(
                LiteralProperty(ObjKey.Name("sum"), IdentifierExpr("sum", token))
            ))
        )
        
        // Analyze performance potential
        val estimate = VMUtil.estimatePerformanceBenefit(complexIR)
        println("Performance Estimate:")
        println("  Expected speedup: ${estimate.expectedSpeedup}x")
        println("  Confidence: ${(estimate.confidence * 100).toInt()}%")
        println("  Recommendation: ${estimate.recommendation}")
        
        // Validate VM compatibility
        val validation = VMUtil.validateIRForVM(complexIR)
        println("\nVM Compatibility:")
        println("  Valid: ${validation.isValid}")
        if (!validation.isValid) {
            println("  Unsupported features: ${validation.unsupportedFeatures}")
        }
    }
    
    fun runIntegrationExample() {
        println("\n=== Integration Example ===")
        
        // This demonstrates how the VM integrates with the existing system
        val token = Token(TokenType.NUMBER, "42", 0, 0)
        val ir = listOf(
            IRLet("answer", NumberLiteral(I32(42), token)),
            IROutput(listOf(LiteralProperty(ObjKey.Name("answer"), IdentifierExpr("answer", token))))
        )
        
        // Create VM-enabled executor
        val vmExec = VMFactory.createExecutor(
            ir = ir,
            useVM = true
        )
        
        val env = mutableMapOf<String, Any?>()
        val result = vmExec.run(env)
        
        println("Execution completed successfully")
        println("Environment: $env")
        println("Result: $result")
        
        // Show VM statistics
        val stats = VMFactory.getCompilationStats()
        println("\nVM Statistics:")
        println("  Total compilations: ${stats.totalCompilations}")
        println("  Successful: ${stats.successfulCompilations}")
        println("  Failed: ${stats.failedCompilations}")
    }

    fun runStreamVMOutputExample() {
        println("\n=== Stream VM OUTPUT Example ===")
        val program = """
            TRANSFORM __T { // Produce multiple OUTPUTs; VM will normalize to list
                FOR EACH n IN input.nums {
                    OUTPUT { val: n }
                }
            }
        """.trimIndent()

        // Parse program
        val tokens = Lexer(program).lex()
        val prog = Parser(tokens, program).parse()
        val funcs = prog.decls.filterIsInstance<FuncDecl>().associateBy { it.name }
        val hostFns = io.github.ehlyzov.branchline.std.StdLib.fns
        io.github.ehlyzov.branchline.sema.SemanticAnalyzer(hostFns.keys).analyze(prog)
        val transform = prog.decls.filterIsInstance<TransformDecl>().single()

        // Build runner using VM engine
        val runnerVM = io.github.ehlyzov.branchline.ir.compileStream(
            transform,
            funcs = funcs,
            hostFns = hostFns,
            hostFnMeta = io.github.ehlyzov.branchline.std.StdLib.meta,
            engine = ExecutionEngine.VM,
        )
        val input = mapOf("nums" to listOf(1, 2, 3))
        val outVM = runnerVM(input)
        println("VM OUTPUT: $outVM")

        // Build runner using interpreter for parity
        val runnerInterp = io.github.ehlyzov.branchline.ir.compileStream(
            transform,
            funcs = funcs,
            hostFns = hostFns,
            hostFnMeta = io.github.ehlyzov.branchline.std.StdLib.meta,
            engine = ExecutionEngine.INTERPRETER,
        )
        val outInterp = runnerInterp(input)
        println("Interpreter OUTPUT: $outInterp")
    }
    
    fun runTracingExample() {
        println("\n=== VM Tracing Example ===")
        
        // Create a tracer to see VM execution steps
        val tracer = CollectingTracer(TraceOptions(step = true, includeEval = true))
        
        // Simple calculation with tracing
        val instructions = listOf(
            Instruction.PUSH(10),
            Instruction.PUSH(20),
            Instruction.ADD,
            Instruction.STORE_VAR("result")
        )
        
        val bytecode = Bytecode.fromInstructions(instructions)
        val vm = VM(tracer = tracer)
        val env = mutableMapOf<String, Any?>()
        
        val result = vm.execute(bytecode, env)
        
        println("Execution result: $result")
        println("Final environment: $env")
        println("Trace events captured: ${tracer.events.size}")
        
        // Show some trace events
        tracer.events.take(5).forEach { event ->
            println("  ${event.at}: ${event.event}")
        }
    }

    fun runMetricsDemo() {
        println("\n=== VM Metrics Demo ===")
        VMFactory.Metrics.reset()

        // 1) VMEval on expression: increments compile metrics
        val vmEval = VMEval()
        val env1 = mutableMapOf<String, Any?>()
        val expr = BinaryExpr(NumberLiteral(I32(1), Token(TokenType.NUMBER, "1", 0, 0)), Token(TokenType.PLUS, "+", 0, 0), NumberLiteral(I32(2), Token(TokenType.NUMBER, "2", 0, 1)))
        val v1 = vmEval.eval(expr, env1)
        println("VMEval(1+2) = $v1")
        println("Stats after VMEval: ${VMFactory.getCompilationStats()}")

        // 2) VMExec on a buffer transform: increments compile & execute metrics
        val program = """
            TRANSFORM __T { LET a = 1; LET b = 2; OUTPUT { s: a + b };
            }
        """.trimIndent()
        val tokens = Lexer(program).lex()
        val prog = Parser(tokens, program).parse()
        val funcs = prog.decls.filterIsInstance<FuncDecl>().associateBy { it.name }
        val hostFns = io.github.ehlyzov.branchline.std.StdLib.fns
        io.github.ehlyzov.branchline.sema.SemanticAnalyzer(hostFns.keys).analyze(prog)
        val transform = prog.decls.filterIsInstance<TransformDecl>().single()
        val ir = io.github.ehlyzov.branchline.ir.ToIR(funcs, hostFns).compile(transform.body.statements)

        val vmExec = VMExec(
            ir = ir,
            hostFns = hostFns,
            hostFnMeta = io.github.ehlyzov.branchline.std.StdLib.meta,
            funcs = funcs,
        )
        val v2res = vmExec.run(mutableMapOf("input" to emptyMap<String, Any?>()))
        println("VMExec output: $v2res")
        println("Final stats: ${VMFactory.getCompilationStats()}")
    }

    fun runInstructionHistogramDemo() {
        println("\n=== VM Instruction Histogram Demo ===")
        val program = """
            TRANSFORM __T { LET acc = 0;
                FOR EACH n IN input.nums {
                    IF n % 2 == 0 THEN { SET acc = acc + n; } ELSE { }
                }
                OUTPUT { sum: acc };
            }
        """.trimIndent()

        val tokens = Lexer(program).lex()
        val prog = Parser(tokens, program).parse()
        val funcs = prog.decls.filterIsInstance<FuncDecl>().associateBy { it.name }
        val hostFns = io.github.ehlyzov.branchline.std.StdLib.fns
        io.github.ehlyzov.branchline.sema.SemanticAnalyzer(hostFns.keys).analyze(prog)
        val transform = prog.decls.filterIsInstance<TransformDecl>().single()
        val ir = io.github.ehlyzov.branchline.ir.ToIR(funcs, hostFns).compile(transform.body.statements)

        // Fallback interpreter eval for VMExec
        val tracer = io.github.ehlyzov.branchline.debug.CollectingTracer(io.github.ehlyzov.branchline.debug.TraceOptions(step = false))
        val exec = VMExec(
            ir = ir,
            tracer = tracer,
            hostFns = hostFns,
            hostFnMeta = io.github.ehlyzov.branchline.std.StdLib.meta,
            funcs = funcs,
        )
        val out = exec.run(mutableMapOf("input" to mapOf("nums" to (1..10).toList())))
        println("Transform output: $out")

        val hist = tracer.instructionCounts.toList().sortedByDescending { it.second }
        println("Instruction histogram (top 10):")
        for ((op, cnt) in hist.take(10)) println("  $op: $cnt")
        val total = tracer.instructionCounts.values.sum()
        println("Total executed instructions: $total")
    }
    
    fun demonstrateVMCapabilities() {
        println("Stack-based Virtual Machine for Branchline DSL")
        println("=".repeat(50))
        
        runBasicCalculationExample()
        runPerformanceComparisonExample()
        runIntegrationExample()
        runTracingExample()
        runStreamVMOutputExample()
        runMetricsDemo()
        runInstructionHistogramDemo()
        
        println("\n=== VM Implementation Summary ===")
        println("✓ Instruction set defined (${Instruction::class.sealedSubclasses.size} instruction types)")
        println("✓ Stack-based execution engine")
        println("✓ IR-to-bytecode compiler")
        println("✓ Integration with existing eval system")
        println("✓ Fallback mechanism for unsupported features")
        println("✓ Performance estimation and compatibility validation")
        println("✓ Debug tracing support")
        println("✓ Exception handling and safety checks")
        println("✓ Array comprehension compilation")

        println("\nFuture work:")
        println("- Add memory management and garbage collection")
        println("- Performance optimization passes")
        println("- Comprehensive test coverage")
        println("- Benchmark against interpreter")
    }
}

fun main() {
    val example = VMExample()
    example.demonstrateVMCapabilities()
}
