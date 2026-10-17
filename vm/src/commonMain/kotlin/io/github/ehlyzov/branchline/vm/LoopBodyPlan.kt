@file:OptIn(ExperimentalVMApi::class)

package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.ObjKey

/** An experimental, structurally owned program and at most one JVM-generated loop. */
@ExperimentalVMApi
class LoopBodyPlan internal constructor(
    internal val bytecode: Bytecode,
    internal val initPc: Int,
    internal val bodyStart: Int,
    internal val nextPc: Int,
    internal val exitPc: Int,
    internal val kernel: LoopBodyKernel?,
    internal val constantsByPc: Array<Any?>,
    internal val keysByPc: Array<ObjKey?>,
    internal val generatedCode: ByteArray?,
    val eligibleLoopCount: Int,
    val bodyInstructionCount: Int,
    val fallbackReason: String?,
) {
    val loweredLoopCount: Int get() = if (kernel == null) 0 else 1
    val generatedClassCount: Int get() = loweredLoopCount
    val generatedBytecodeBytes: Int get() = generatedCode?.size ?: 0
}

/** Public only for generated classes loaded by a child class loader. No per-opcode dispatch. */
@ExperimentalVMApi
interface LoopBodyKernel {
    fun run(context: LoopBodyContext)
}

/** Fixed operation entry points; all language values remain on the canonical VM stack. */
@ExperimentalVMApi
interface LoopBodyContext {
    fun mark(sourcePc: Int)
    fun after(nextPc: Int): Boolean
    fun pushConstant(sourcePc: Int)
    fun dup()
    fun pop()
    fun swap()
    fun loadVariable(name: String)
    fun loadScope(name: String)
    fun loadLocal(index: Int)
    fun accessStatic(sourcePc: Int)
    fun accessDynamic()
    fun add()
    fun sub()
    fun mul()
    fun div()
    fun idiv()
    fun mod()
    fun neg()
    fun eq()
    fun neq()
    fun lt()
    fun le()
    fun gt()
    fun ge()
    fun and()
    fun or()
    fun not()
    fun coalesce()
    fun makeObject(count: Int)
    fun makeArray(count: Int)
    fun arrayBuilderAdd()
    fun popTruth(): Boolean
    fun nextForeach(start: Int, end: Int): Boolean
}
