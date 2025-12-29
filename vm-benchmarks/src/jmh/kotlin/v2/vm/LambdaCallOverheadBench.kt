package v2.vm

import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
open class LambdaCallOverheadBench {
    @Param("0", "1", "2", "3", "4", "5")
    var arity: Int = 0

    @Param("0", "5", "10")
    var capturedSize: Int = 0

    private lateinit var vm: VM
    private lateinit var env: MutableMap<String, Any?>
    private lateinit var callFunctionCode: Bytecode
    private lateinit var callLambdaCode: Bytecode

    @Setup(Level.Trial)
    fun setup() {
        vm = VM()

        val lambdaBytecode = Bytecode.fromInstructions(listOf(
            Instruction.PUSH(0),
            Instruction.RETURN_VALUE
        ))

        val captured = (0 until capturedSize).associate { "c$it" to it }
        val params = List(arity) { "p$it" }
        val lambda = LambdaValue(params, lambdaBytecode, captured)

        env = mutableMapOf("f" to lambda)

        val cfInstrs = mutableListOf<Instruction>()
        for (i in arity - 1 downTo 0) {
            cfInstrs += Instruction.PUSH(0)
        }
        cfInstrs += Instruction.CALL("f", arity)
        cfInstrs += Instruction.RETURN_VALUE
        callFunctionCode = Bytecode.fromInstructions(cfInstrs)

        val clInstrs = mutableListOf<Instruction>()
        clInstrs += Instruction.LOAD_VAR("f")
        for (i in arity - 1 downTo 0) {
            clInstrs += Instruction.PUSH(0)
        }
        clInstrs += Instruction.CALL_LAMBDA(arity)
        clInstrs += Instruction.RETURN_VALUE
        callLambdaCode = Bytecode.fromInstructions(clInstrs)
    }

    @Benchmark
    fun callFunction(bh: Blackhole) {
        bh.consume(vm.execute(callFunctionCode, env))
    }

    @Benchmark
    fun callLambda(bh: Blackhole) {
        bh.consume(vm.execute(callLambdaCode, env))
    }
}
