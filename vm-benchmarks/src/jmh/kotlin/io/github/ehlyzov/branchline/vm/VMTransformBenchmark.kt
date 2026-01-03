package io.github.ehlyzov.branchline.vm

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole
import io.github.ehlyzov.branchline.FuncDecl
import io.github.ehlyzov.branchline.Lexer
import io.github.ehlyzov.branchline.Parser
import io.github.ehlyzov.branchline.TransformDecl
import io.github.ehlyzov.branchline.ir.ToIR
import io.github.ehlyzov.branchline.std.StdLib
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public open class VMTransformBenchmark {
    @Param("small", "medium", "large")
    public lateinit var dataset: String

    private lateinit var input: Map<String, Any?>
    private lateinit var env: MutableMap<String, Any?>
    private lateinit var vm: VM
    private lateinit var pathBytecode: Bytecode
    private lateinit var arrayBytecode: Bytecode
    private lateinit var transformBytecode: Bytecode

    @Setup(Level.Trial)
    public fun setup() {
        val size = datasetSizeFromParam(dataset)
        input = BenchDatasets.buildInput(size)
        val hostFns = StdLib.fns
        val funcs: Map<String, FuncDecl> = emptyMap()
        vm = VM(hostFns, funcs)
        pathBytecode = compileBytecode(VMTransformPrograms.pathExpressions, hostFns, funcs)
        arrayBytecode = compileBytecode(VMTransformPrograms.arrayComprehension, hostFns, funcs)
        transformBytecode = compileBytecode(VMTransformPrograms.typicalTransform, hostFns, funcs)
    }

    @Setup(Level.Invocation)
    public fun resetEnv() {
        env = BenchDatasets.buildEnv(input)
    }

    @Benchmark
    public fun pathExpressions(bh: Blackhole) {
        bh.consume(vm.execute(pathBytecode, env))
    }

    @Benchmark
    public fun arrayComprehensions(bh: Blackhole) {
        bh.consume(vm.execute(arrayBytecode, env))
    }

    @Benchmark
    public fun typicalTransform(bh: Blackhole) {
        bh.consume(vm.execute(transformBytecode, env))
    }
}

private fun datasetSizeFromParam(param: String): DatasetSize {
    return when (param.lowercase()) {
        "small" -> DatasetSize.SMALL
        "medium" -> DatasetSize.MEDIUM
        "large" -> DatasetSize.LARGE
        else -> error("Unknown dataset size: $param")
    }
}

private fun compileBytecode(
    program: String,
    hostFns: Map<String, (List<Any?>) -> Any?>,
    funcs: Map<String, FuncDecl>,
): Bytecode {
    val tokens = Lexer(program).lex()
    val parsed = Parser(tokens, program).parse()
    val transforms = parsed.decls.filterIsInstance<TransformDecl>()
    val transform = transforms.single()
    val ir = ToIR(funcs, hostFns).compile(transform.body.statements)
    return Compiler(funcs, hostFns).compile(ir)
}
