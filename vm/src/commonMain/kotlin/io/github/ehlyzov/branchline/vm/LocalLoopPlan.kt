@file:OptIn(ExperimentalVMApi::class)

package io.github.ehlyzov.branchline.vm

/** JVM-local experiment. Constants retain identity; structure is owned, execution is sequential. */
@ExperimentalVMApi
class LocalLoopPlan internal constructor(
    internal val bytecode: Bytecode,
    internal val bodyStart: Int,
    internal val nextPc: Int,
    internal val exitPc: Int,
    internal val kernel: LocalLoopKernel?,
    internal val constantsByPc: Array<Any?>,
    internal val keysByPc: Array<Any?>,
    internal val maxVirtualHeight: Int,
    internal val generatedCode: ByteArray?,
    val eligibleLoopCount: Int,
    val bodyInstructionCount: Int,
    val localValueCount: Int,
    val primitiveSiteCount: Int,
    val spillSiteCount: Int,
    val guardSiteCount: Int,
    val fallbackReason: String?,
) {
    val loweredLoopCount: Int get() = if (kernel == null) 0 else 1
    val generatedClassCount: Int get() = loweredLoopCount
    val generatedBytecodeBytes: Int get() = generatedCode?.size ?: 0
}

@ExperimentalVMApi
interface LocalLoopKernel { fun run(context: LocalLoopContext) }

/** Public only for generated child-loader classes; no virtual operand stack is stored here. */
@ExperimentalVMApi
interface LocalLoopContext {
    fun constant(sourcePc: Int): Any?
    fun key(sourcePc: Int): Any
    fun hasVariable(name: String): Boolean
    fun variable(name: String): Any?
    fun scope(name: String): Any?
    fun local(index: Int): Any?
    fun beginSpill(sourcePc: Int, primitiveUsed: Boolean)
    fun spill(value: Any?)
    fun append(value: Any?, sourcePc: Int)
    /** 1 continues the generated loop; 0 resumes canonical execution; 2 completes the loop. */
    fun next(sourcePc: Int, start: Int, end: Int, primitiveUsed: Boolean): Int
    fun finish(primitiveUsed: Boolean)
}
