package io.github.ehlyzov.branchline.vm

import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import io.github.ehlyzov.branchline.FuncDecl
import io.github.ehlyzov.branchline.ir.IRNode

/**
 * Base class for JMH benchmarks targeting the Branchline VM.
 *
 * Subclasses provide the IR program to benchmark along with any
 * function or host-function definitions. The bytecode is built once
 * per JMH trial and the [run] method can be called from `@Benchmark`
 * methods to execute the program with a mutable environment.
 */
@State(Scope.Benchmark)
abstract class AbstractVMBenchmark {
    private lateinit var vm: VM
    private lateinit var bytecode: Bytecode

    /** Compile the IR into bytecode once per trial. */
    @Setup(org.openjdk.jmh.annotations.Level.Trial)
    fun setup() {
        val funcs = funcs()
        val hostFns = hostFns()
        bytecode = Compiler(funcs, hostFns).compile(program())
        vm = VM(hostFns, funcs)
    }


    /** IR nodes representing the program under test. */
    protected abstract fun program(): List<IRNode>

    /** Additional function declarations used by the program. */
    protected open fun funcs(): Map<String, FuncDecl> = emptyMap()

    /** Host functions available during execution. */
    protected open fun hostFns(): Map<String, (List<Any?>) -> Any?> = emptyMap()

    /** Execute the compiled bytecode with the provided environment. */
    fun run(env: MutableMap<String, Any?> = mutableMapOf()): Any? =
        vm.execute(bytecode, env)
}
