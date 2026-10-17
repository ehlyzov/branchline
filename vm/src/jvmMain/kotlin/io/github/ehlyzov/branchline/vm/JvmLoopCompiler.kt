@file:OptIn(ExperimentalVMApi::class)

package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.ObjKey
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes

/** A bounded, JVM-only comprehension compiler. No class cache and no interpreted body handlers. */
@ExperimentalVMApi
object JvmLoopCompiler {
    @JvmStatic
    fun prepare(source: Bytecode): LoopBodyPlan {
        val code = source.copy(
            opcodes = source.opcodes.copyOf(), intOperands = source.intOperands.copyOf(),
            stringOperands = source.stringOperands.copyOf(), objKeyOperands = source.objKeyOperands.copyOf(),
            constOperands = source.constOperands.copyOf(), intOperandOffsets = source.intOperandOffsets.copyOf(),
            stringOperandOffsets = source.stringOperandOffsets.copyOf(), objKeyOperandOffsets = source.objKeyOperandOffsets.copyOf(),
            constOperandOffsets = source.constOperandOffsets.copyOf(), constants = source.constants.toList(),
            debugInfo = source.debugInfo.toMap(), traceMetadata = TraceMetadata(
                source.traceMetadata.enterEvents.mapValues { it.value.toList() },
                source.traceMetadata.exitEvents.mapValues { it.value.toList() },
                source.traceMetadata.pathWrites.mapValues { (_, meta) ->
                    meta.copy(segments = meta.segments.toList(), displayPath = meta.displayPath?.toList())
                },
            ),
        )
        fun fallback(reason: String, eligible: Int = 0, bodySize: Int = 0) = LoopBodyPlan(
            code, -1, -1, -1, -1, null, emptyArray(), emptyArray(), null, eligible, bodySize, reason,
        )
        val ops: List<Opcode>
        try {
            ops = (0 until code.size()).map { pc ->
                val op = Opcode.entries[code.getOpcode(pc)]
                code.getInstruction(pc) // Validate all operand accesses before generating a partial plan.
                if (op == Opcode.PUSH && code.getConstOperand(pc) is LambdaTemplate) return fallback("closure")
                op
            }
        } catch (_: Exception) { return fallback("malformed bytecode") }
        val inits = ops.indices.filter { ops[it] == Opcode.INIT_FOREACH }
        val nexts = ops.indices.filter { ops[it] == Opcode.NEXT_FOREACH }
        if (inits.size != 1 || nexts.size != 1) return fallback("requires one comprehension loop")
        val init = inits.single()
        val start = init + 1
        val next = nexts.single()
        if (next <= start || next + 1 >= code.size()) return fallback("invalid loop layout")
        val exit = next + 1
        if (code.getIntOperand(init) != exit || code.getIntOperand(next) != start || code.getIntOperand(next, 1) != exit ||
            ops[exit] != Opcode.ARRAY_BUILDER_FINISH || ops[next - 1] != Opcode.ARRAY_BUILDER_ADD ||
            ops.count { it == Opcode.ARRAY_BUILDER_INIT } != 1 || ops.count { it == Opcode.ARRAY_BUILDER_ADD } != 1
        ) return fallback("not a compiler-shaped comprehension")
        val bodySize = next - start + 1
        if (bodySize > MAX_BODY_INSTRUCTIONS) return fallback("body exceeds $MAX_BODY_INSTRUCTIONS instructions", 1, bodySize)
        for (pc in ops.indices) {
            val op = ops[pc]
            val allowed = when {
                pc == init -> op == Opcode.INIT_FOREACH
                pc == next -> op == Opcode.NEXT_FOREACH
                pc in start until next -> op in BODY_OPS
                else -> op in OUTER_OPS
            }
            if (!allowed) return fallback("unsupported opcode $op at $pc")
        }
        val filters = (start until next).filter { ops[it] == Opcode.JUMP_IF_FALSE }
        if (filters.size > 1 || filters.any { code.getIntOperand(it) != next }) return fallback("unsupported loop control flow")
        if (!validStackShape(code, ops, init, next, filters.singleOrNull())) return fallback("unsupported comprehension stack shape")

        val constants = arrayOfNulls<Any?>(code.size())
        val keys = arrayOfNulls<ObjKey>(code.size())
        for (pc in start until next) when (ops[pc]) {
            Opcode.PUSH -> constants[pc] = code.getConstOperand(pc)
            Opcode.ACCESS_STATIC -> keys[pc] = code.getObjKeyOperand(pc)
            else -> Unit
        }
        return try {
            val bytes = generate(code, ops, start, next, exit)
            val loader = KernelLoader(LoopBodyKernel::class.java.classLoader)
            val kernel = loader.define(bytes).getDeclaredConstructor().newInstance() as LoopBodyKernel
            LoopBodyPlan(code, init, start, next, exit, kernel, constants, keys, bytes, 1, bodySize, null)
        } catch (failure: Exception) {
            fallback("JVM kernel generation failed: ${failure::class.simpleName}", 1, bodySize)
        } catch (failure: LinkageError) {
            fallback("JVM kernel linkage failed: ${failure::class.simpleName}", 1, bodySize)
        }
    }

    /** Untimed inspection only; the returned bytes do not expose mutable plan state. */
    @JvmStatic
    fun generatedClassBytes(plan: LoopBodyPlan): ByteArray? = plan.generatedCode?.copyOf()

    private class KernelLoader(parent: ClassLoader) : ClassLoader(parent) {
        fun define(bytes: ByteArray): Class<*> = defineClass(BINARY_NAME, bytes, 0, bytes.size)
    }

    private fun generate(code: Bytecode, ops: List<Opcode>, start: Int, next: Int, exit: Int): ByteArray {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL, CLASS_NAME, null, "java/lang/Object", arrayOf(KERNEL))
        writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null).apply {
            visitCode(); visitVarInsn(Opcodes.ALOAD, 0)
            visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            visitInsn(Opcodes.RETURN); visitMaxs(0, 0); visitEnd()
        }
        val method = writer.visitMethod(Opcodes.ACC_PUBLIC, "run", "(L$CONTEXT;)V", null, null)
        method.visitCode()
        val labels = (start..next).associateWith { Label() }
        fun call(name: String, descriptor: String = "()V", int: Int? = null, text: String? = null) {
            method.visitVarInsn(Opcodes.ALOAD, 1)
            if (int != null) method.visitLdcInsn(int)
            if (text != null) method.visitLdcInsn(text)
            method.visitMethodInsn(Opcodes.INVOKEINTERFACE, CONTEXT, name, descriptor, true)
        }
        fun after(target: Int) {
            call("after", "(I)Z", int = target)
            val proceed = Label()
            method.visitJumpInsn(Opcodes.IFEQ, proceed)
            method.visitInsn(Opcodes.RETURN)
            method.visitLabel(proceed)
        }
        for (pc in start..next) {
            method.visitLabel(labels.getValue(pc))
            call("mark", "(I)V", int = pc)
            val op = ops[pc]
            when (op) {
                Opcode.PUSH -> call("pushConstant", "(I)V", int = pc)
                Opcode.LOAD_VAR -> call("loadVariable", "(Ljava/lang/String;)V", text = code.getStringOperand(pc))
                Opcode.LOAD_SCOPE -> call("loadScope", "(Ljava/lang/String;)V", text = code.getStringOperand(pc))
                Opcode.LOAD_LOCAL -> call("loadLocal", "(I)V", int = code.getIntOperand(pc))
                Opcode.ACCESS_STATIC -> call("accessStatic", "(I)V", int = pc)
                Opcode.MAKE_OBJECT -> call("makeObject", "(I)V", int = code.getIntOperand(pc))
                Opcode.MAKE_ARRAY -> call("makeArray", "(I)V", int = code.getIntOperand(pc))
                Opcode.JUMP_IF_FALSE -> {
                    call("popTruth", "()Z")
                    method.visitVarInsn(Opcodes.ISTORE, 2)
                    method.visitVarInsn(Opcodes.ILOAD, 2)
                    val accepted = Label()
                    method.visitJumpInsn(Opcodes.IFNE, accepted)
                    after(next)
                    method.visitJumpInsn(Opcodes.GOTO, labels.getValue(next))
                    method.visitLabel(accepted)
                    after(pc + 1)
                }
                Opcode.NEXT_FOREACH -> {
                    method.visitVarInsn(Opcodes.ALOAD, 1)
                    method.visitLdcInsn(start); method.visitLdcInsn(exit)
                    method.visitMethodInsn(Opcodes.INVOKEINTERFACE, CONTEXT, "nextForeach", "(II)Z", true)
                    method.visitVarInsn(Opcodes.ISTORE, 2)
                    method.visitVarInsn(Opcodes.ILOAD, 2)
                    val finished = Label()
                    method.visitJumpInsn(Opcodes.IFEQ, finished)
                    after(start)
                    method.visitJumpInsn(Opcodes.GOTO, labels.getValue(start))
                    method.visitLabel(finished)
                    after(exit)
                    method.visitInsn(Opcodes.RETURN)
                }
                else -> call(METHODS.getValue(op))
            }
            if (op != Opcode.JUMP_IF_FALSE && op != Opcode.NEXT_FOREACH && op in OBSERVERS) after(pc + 1)
        }
        method.visitInsn(Opcodes.RETURN)
        method.visitMaxs(0, 0); method.visitEnd(); writer.visitEnd()
        return writer.toByteArray()
    }

    private fun validStackShape(code: Bytecode, ops: List<Opcode>, init: Int, next: Int, filter: Int?): Boolean {
        // true marks the compiler-owned array builder; body operations cannot consume the outer prefix.
        val stack = ArrayList<Boolean>()
        fun pop(count: Int, floor: Int): Boolean {
            if (count < 0 || stack.size.toLong() - count < floor) return false
            repeat(count) { stack.removeAt(stack.lastIndex) }
            return true
        }
        fun step(pc: Int, floor: Int): Boolean {
            when (ops[pc]) {
                Opcode.PUSH, Opcode.LOAD_VAR, Opcode.LOAD_SCOPE, Opcode.LOAD_LOCAL -> stack.add(false)
                Opcode.DUP -> { if (stack.size <= floor) return false; stack.add(stack.last()) }
                Opcode.POP, Opcode.STORE_VAR, Opcode.STORE_LOCAL -> if (!pop(1, floor)) return false
                Opcode.SWAP -> {
                    if (stack.size < floor + 2) return false
                    val value = stack[stack.lastIndex]; stack[stack.lastIndex] = stack[stack.lastIndex - 1]; stack[stack.lastIndex - 1] = value
                }
                Opcode.ACCESS_STATIC, Opcode.NEG, Opcode.NOT -> { if (!pop(1, floor)) return false; stack.add(false) }
                Opcode.ACCESS_DYNAMIC, Opcode.ADD, Opcode.SUB, Opcode.MUL, Opcode.DIV, Opcode.IDIV, Opcode.MOD,
                Opcode.EQ, Opcode.NEQ, Opcode.LT, Opcode.LE, Opcode.GT, Opcode.GE, Opcode.AND, Opcode.OR, Opcode.COALESCE -> {
                    if (!pop(2, floor)) return false; stack.add(false)
                }
                Opcode.MAKE_ARRAY -> { if (!pop(code.getIntOperand(pc), floor)) return false; stack.add(false) }
                Opcode.MAKE_OBJECT -> {
                    val count = code.getIntOperand(pc)
                    if (count < 0 || count > Int.MAX_VALUE / 2 || !pop(count * 2, floor)) return false
                    stack.add(false)
                }
                Opcode.OUTPUT -> {
                    val count = code.getIntOperand(pc)
                    if (count != 0) {
                        if (count < 0 || count > Int.MAX_VALUE / 2 || !pop(count * 2, floor)) return false
                        stack.add(false)
                    }
                }
                Opcode.ARRAY_BUILDER_INIT -> stack.add(true)
                Opcode.ARRAY_BUILDER_FINISH -> { if (stack.isEmpty() || !stack.last()) return false; stack[stack.lastIndex] = false }
                Opcode.ARRAY_BUILDER_ADD -> {
                    if (!pop(1, floor) || stack.isEmpty() || !stack.last()) return false
                }
                Opcode.JUMP_IF_FALSE -> if (!pop(1, floor)) return false
                Opcode.NOP -> Unit
                else -> return false
            }
            return true
        }
        for (pc in 0 until init) if (!step(pc, 0)) return false
        if (stack.size < 2 || !stack[stack.lastIndex - 1] || !pop(1, 0)) return false
        val base = stack.toList()
        for (pc in init + 1 until next) {
            if (!step(pc, base.size)) return false
            if (pc == filter && stack != base) return false
        }
        if (stack != base) return false
        for (pc in next + 1 until code.size()) if (!step(pc, 0)) return false
        return true
    }

    private const val MAX_BODY_INSTRUCTIONS = 128
    private const val CLASS_NAME = "io/github/ehlyzov/branchline/vm/generated/CompiledComprehension"
    private const val BINARY_NAME = "io.github.ehlyzov.branchline.vm.generated.CompiledComprehension"
    private const val KERNEL = "io/github/ehlyzov/branchline/vm/LoopBodyKernel"
    private const val CONTEXT = "io/github/ehlyzov/branchline/vm/LoopBodyContext"
    private val METHODS = mapOf(
        Opcode.DUP to "dup", Opcode.POP to "pop", Opcode.SWAP to "swap", Opcode.ACCESS_DYNAMIC to "accessDynamic",
        Opcode.ADD to "add", Opcode.SUB to "sub", Opcode.MUL to "mul", Opcode.DIV to "div", Opcode.IDIV to "idiv", Opcode.MOD to "mod", Opcode.NEG to "neg",
        Opcode.EQ to "eq", Opcode.NEQ to "neq", Opcode.LT to "lt", Opcode.LE to "le", Opcode.GT to "gt", Opcode.GE to "ge",
        Opcode.AND to "and", Opcode.OR to "or", Opcode.NOT to "not", Opcode.COALESCE to "coalesce", Opcode.ARRAY_BUILDER_ADD to "arrayBuilderAdd",
    )
    private val BODY_OPS = METHODS.keys + setOf(Opcode.PUSH, Opcode.LOAD_VAR, Opcode.LOAD_SCOPE, Opcode.LOAD_LOCAL,
        Opcode.ACCESS_STATIC, Opcode.MAKE_OBJECT, Opcode.MAKE_ARRAY, Opcode.JUMP_IF_FALSE)
    private val OUTER_OPS = (BODY_OPS - setOf(Opcode.JUMP_IF_FALSE, Opcode.ARRAY_BUILDER_ADD)) +
        setOf(Opcode.STORE_VAR, Opcode.STORE_LOCAL, Opcode.ARRAY_BUILDER_INIT, Opcode.ARRAY_BUILDER_FINISH, Opcode.OUTPUT, Opcode.NOP)
    private val OBSERVERS = BODY_OPS - setOf(Opcode.PUSH, Opcode.DUP, Opcode.POP, Opcode.SWAP, Opcode.LOAD_SCOPE,
        Opcode.LOAD_LOCAL, Opcode.COALESCE, Opcode.MAKE_ARRAY)
}
