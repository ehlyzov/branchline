@file:OptIn(ExperimentalVMApi::class)

package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.I32
import io.github.ehlyzov.branchline.I64
import io.github.ehlyzov.branchline.ObjKey
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/** One bounded local/typed comprehension. The previous canonical-stack backend is untouched. */
@ExperimentalVMApi
object JvmLocalLoopCompiler {
    @JvmStatic
    fun prepare(source: Bytecode): LocalLoopPlan {
        val code = source.copy(
            opcodes = source.opcodes.copyOf(), intOperands = source.intOperands.copyOf(),
            stringOperands = source.stringOperands.copyOf(), objKeyOperands = source.objKeyOperands.copyOf(),
            constOperands = source.constOperands.copyOf(), intOperandOffsets = source.intOperandOffsets.copyOf(),
            stringOperandOffsets = source.stringOperandOffsets.copyOf(), objKeyOperandOffsets = source.objKeyOperandOffsets.copyOf(),
            constOperandOffsets = source.constOperandOffsets.copyOf(), constants = source.constants.toList(),
            debugInfo = source.debugInfo.toMap(), traceMetadata = TraceMetadata(
                source.traceMetadata.enterEvents.mapValues { it.value.toList() },
                source.traceMetadata.exitEvents.mapValues { it.value.toList() },
                source.traceMetadata.pathWrites.mapValues { (_, meta) -> meta.copy(segments = meta.segments.toList(), displayPath = meta.displayPath?.toList()) },
            ),
        )
        fun fallback(reason: String, eligible: Int = 0, body: Int = 0) = LocalLoopPlan(code, -1, -1, -1, null,
            emptyArray(), emptyArray(), 0, null, eligible, body, 0, 0, 0, 0, reason)
        val ops = try {
            (0 until code.size()).map { pc ->
                val op = Opcode.entries[code.getOpcode(pc)]
                code.getInstruction(pc)
                if (op == Opcode.PUSH && code.getConstOperand(pc) is LambdaTemplate) return fallback("closure")
                op
            }
        } catch (_: Exception) { return fallback("malformed bytecode") }
        val inits = ops.indices.filter { ops[it] == Opcode.INIT_FOREACH }
        val nexts = ops.indices.filter { ops[it] == Opcode.NEXT_FOREACH }
        if (inits.size != 1 || nexts.size != 1) return fallback("requires one comprehension")
        val init = inits.single(); val start = init + 1; val next = nexts.single(); val exit = next + 1
        if (next <= start || exit >= code.size() || code.getIntOperand(init) != exit ||
            code.getIntOperand(next) != start || code.getIntOperand(next, 1) != exit ||
            ops[next - 1] != Opcode.ARRAY_BUILDER_ADD || ops[exit] != Opcode.ARRAY_BUILDER_FINISH ||
            ops.count { it == Opcode.ARRAY_BUILDER_INIT } != 1 || ops.count { it == Opcode.ARRAY_BUILDER_ADD } != 1
        ) return fallback("not a compiler-shaped comprehension")
        val body = next - start + 1
        if (body > 128) return fallback("body exceeds 128 instructions", 1, body)
        for (pc in ops.indices) {
            val allowed = when {
                pc == init || pc == next -> true
                pc in start until next -> ops[pc] in BODY
                else -> ops[pc] in OUTER
            }
            if (!allowed) return fallback("unsupported opcode ${ops[pc]} at $pc")
        }
        val filters = (start until next).filter { ops[it] == Opcode.JUMP_IF_FALSE }
        if (filters.size > 1 || filters.any { code.getIntOperand(it) != next }) return fallback("unsupported loop control flow")
        if (!prefixShape(code, ops, init)) return fallback("unsupported outer stack shape")
        val constants = arrayOfNulls<Any?>(code.size())
        val keys = arrayOfNulls<Any?>(code.size())
        for (pc in start until next) {
            when (ops[pc]) {
                Opcode.PUSH -> constants[pc] = code.getConstOperand(pc)
                Opcode.ACCESS_STATIC -> keys[pc] = when (val key = code.getObjKeyOperand(pc)) {
                    is ObjKey.Name -> key.v
                    is I32 -> key.v
                    is I64 -> key.v
                    else -> return fallback("unsupported static key at $pc")
                }
                else -> Unit
            }
        }
        return try {
            val generator = Generator(code, ops, constants, start, next, exit)
            val bytes = generator.generate()
            val kernel = KernelLoader(LocalLoopKernel::class.java.classLoader).define(bytes).getDeclaredConstructor().newInstance() as LocalLoopKernel
            LocalLoopPlan(code, start, next, exit, kernel, constants, keys, generator.maxHeight, bytes, 1, body,
                generator.valueCount, generator.primitiveSites, generator.spillCount, generator.guards, null)
        } catch (failure: IllegalArgumentException) {
            fallback(failure.message ?: "unsupported local lowering", 1, body)
        } catch (failure: Exception) {
            fallback("local kernel generation failed: ${failure::class.simpleName}", 1, body)
        } catch (failure: LinkageError) {
            fallback("local kernel linkage failed: ${failure::class.simpleName}", 1, body)
        }
    }

    @JvmStatic
    fun generatedClassBytes(plan: LocalLoopPlan): ByteArray? = plan.generatedCode?.copyOf()

    private class KernelLoader(parent: ClassLoader) : ClassLoader(parent) {
        fun define(bytes: ByteArray): Class<*> = defineClass(BINARY, bytes, 0, bytes.size)
    }

    private enum class Kind { REF, INTEGER, BOOL }
    private data class Value(val kind: Kind, val register: Int, val tag: Int = -1, val constant: Any? = null, val literal: Boolean = false)
    private data class Spill(val pc: Int, val label: Label, val values: List<Value>)

    private class Generator(
        val code: Bytecode, val ops: List<Opcode>, val constants: Array<Any?>,
        val start: Int, val next: Int, val exit: Int,
    ) {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        lateinit var method: MethodVisitor
        val values = ArrayList<Value>()
        val spills = LinkedHashMap<Int, Spill>()
        var register = 12 // 0=this, 1=context, 2=primitive-used, 3..11=primitive scratch
        var valueCount = 0
        var primitiveSites = 0
        var guards = 0
        var maxHeight = 0
        val spillCount: Int get() = spills.size
        fun fresh(kind: Kind, constant: Any? = null, literal: Boolean = false): Value {
            val at = register
            register += if (kind == Kind.INTEGER) 3 else 1
            valueCount++
            return Value(kind, at, if (kind == Kind.INTEGER) at + 2 else -1, constant, literal)
        }
        fun ctx(name: String, descriptor: String = "()V", args: () -> Unit = {}) {
            method.visitVarInsn(Opcodes.ALOAD, 1); args()
            method.visitMethodInsn(Opcodes.INVOKEINTERFACE, CONTEXT, name, descriptor, true)
        }
        fun load(value: Value) = method.visitVarInsn(when (value.kind) { Kind.REF -> Opcodes.ALOAD; Kind.INTEGER -> Opcodes.LLOAD; Kind.BOOL -> Opcodes.ILOAD }, value.register)
        fun boxed(value: Value) {
            when (value.kind) {
                Kind.REF -> load(value)
                Kind.BOOL -> { load(value); method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false) }
                Kind.INTEGER -> {
                    val asLong = Label(); val done = Label()
                    method.visitVarInsn(Opcodes.ILOAD, value.tag); method.visitJumpInsn(Opcodes.IFEQ, asLong)
                    load(value); method.visitInsn(Opcodes.L2I)
                    method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false)
                    method.visitJumpInsn(Opcodes.GOTO, done); method.visitLabel(asLong)
                    load(value); method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false)
                    method.visitLabel(done)
                }
            }
        }
        fun spill(pc: Int): Label = spills.getOrPut(pc) { Spill(pc, Label(), values.toList()) }.label
        fun reject(opcode: Int, pc: Int) { guards++; method.visitJumpInsn(opcode, spill(pc)) }
        fun pop(): Value { require(values.isNotEmpty()) { "unsupported body stack shape" }; return values.removeAt(values.lastIndex) }
        fun cap(at: Int, pc: Int) {
            method.visitVarInsn(Opcodes.LLOAD, at); method.visitLdcInsn(SAFE); method.visitInsn(Opcodes.LCMP); reject(Opcodes.IFGT, pc)
            method.visitVarInsn(Opcodes.LLOAD, at); method.visitLdcInsn(-SAFE); method.visitInsn(Opcodes.LCMP); reject(Opcodes.IFLT, pc)
        }
        fun integer(value: Value, at: Int, tag: Int, pc: Int) {
            when (value.kind) {
                Kind.INTEGER -> { load(value); method.visitVarInsn(Opcodes.LSTORE, at); method.visitVarInsn(Opcodes.ILOAD, value.tag); method.visitVarInsn(Opcodes.ISTORE, tag) }
                Kind.BOOL -> { method.visitJumpInsn(Opcodes.GOTO, spill(pc)); method.visitInsn(Opcodes.LCONST_0); method.visitVarInsn(Opcodes.LSTORE, at); method.visitInsn(Opcodes.ICONST_0); method.visitVarInsn(Opcodes.ISTORE, tag) }
                Kind.REF -> {
                    val intValue = Label(); val done = Label()
                    load(value); method.visitTypeInsn(Opcodes.INSTANCEOF, "java/lang/Integer"); method.visitJumpInsn(Opcodes.IFNE, intValue)
                    load(value); method.visitTypeInsn(Opcodes.INSTANCEOF, "java/lang/Long"); reject(Opcodes.IFEQ, pc)
                    load(value); method.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Long")
                    method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false)
                    method.visitVarInsn(Opcodes.LSTORE, at); method.visitInsn(Opcodes.ICONST_0); method.visitVarInsn(Opcodes.ISTORE, tag)
                    method.visitJumpInsn(Opcodes.GOTO, done); method.visitLabel(intValue)
                    load(value); method.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Integer")
                    method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false); method.visitInsn(Opcodes.I2L)
                    method.visitVarInsn(Opcodes.LSTORE, at); method.visitInsn(Opcodes.ICONST_1); method.visitVarInsn(Opcodes.ISTORE, tag)
                    method.visitLabel(done)
                }
            }
            cap(at, pc)
        }
        fun resultTag(value: Value, unary: Boolean) {
            val asLong = Label(); val done = Label()
            method.visitVarInsn(Opcodes.ILOAD, 9)
            if (!unary) { method.visitVarInsn(Opcodes.ILOAD, 10); method.visitInsn(Opcodes.IAND) }
            method.visitJumpInsn(Opcodes.IFEQ, asLong)
            load(value); method.visitLdcInsn(Int.MIN_VALUE.toLong()); method.visitInsn(Opcodes.LCMP); method.visitJumpInsn(Opcodes.IFLT, asLong)
            load(value); method.visitLdcInsn(Int.MAX_VALUE.toLong()); method.visitInsn(Opcodes.LCMP); method.visitJumpInsn(Opcodes.IFGT, asLong)
            method.visitInsn(Opcodes.ICONST_1); method.visitJumpInsn(Opcodes.GOTO, done)
            method.visitLabel(asLong); method.visitInsn(Opcodes.ICONST_0); method.visitLabel(done)
            method.visitVarInsn(Opcodes.ISTORE, value.tag)
        }
        fun typedUsed() { method.visitInsn(Opcodes.ICONST_1); method.visitVarInsn(Opcodes.ISTORE, 2) }

        fun generate(): ByteArray {
            writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL, CLASS, null, "java/lang/Object", arrayOf(KERNEL))
            writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null).apply {
                visitCode(); visitVarInsn(Opcodes.ALOAD, 0); visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
                visitInsn(Opcodes.RETURN); visitMaxs(0, 0); visitEnd()
            }
            method = writer.visitMethod(Opcodes.ACC_PUBLIC, "run", "(L$CONTEXT;)V", null, null)
            method.visitCode(); method.visitInsn(Opcodes.ICONST_0); method.visitVarInsn(Opcodes.ISTORE, 2)
            val head = Label(); val nextLabel = Label(); method.visitLabel(head)
            for (pc in start until next) {
                maxHeight = maxOf(maxHeight, values.size)
                when (val op = ops[pc]) {
                    Opcode.PUSH -> {
                        val value = fresh(Kind.REF, constants[pc], true)
                        ctx("constant", "(I)Ljava/lang/Object;") { method.visitLdcInsn(pc) }
                        method.visitVarInsn(Opcodes.ASTORE, value.register); values.add(value)
                    }
                    Opcode.LOAD_VAR, Opcode.LOAD_SCOPE, Opcode.LOAD_LOCAL -> {
                        val value = fresh(Kind.REF)
                        if (op == Opcode.LOAD_VAR) {
                            ctx("hasVariable", "(Ljava/lang/String;)Z") { method.visitLdcInsn(code.getStringOperand(pc)) }; reject(Opcodes.IFEQ, pc)
                        }
                        val name = when (op) { Opcode.LOAD_VAR -> "variable"; Opcode.LOAD_SCOPE -> "scope"; else -> "local" }
                        val desc = if (op == Opcode.LOAD_LOCAL) "(I)Ljava/lang/Object;" else "(Ljava/lang/String;)Ljava/lang/Object;"
                        ctx(name, desc) { method.visitLdcInsn(if (op == Opcode.LOAD_LOCAL) code.getIntOperand(pc) else code.getStringOperand(pc)) }
                        method.visitVarInsn(Opcodes.ASTORE, value.register); values.add(value)
                    }
                    Opcode.ACCESS_STATIC -> {
                        require(values.isNotEmpty()) { "unsupported access stack" }
                        val container = values.last()
                        if (container.kind != Kind.REF) method.visitJumpInsn(Opcodes.GOTO, spill(pc))
                        boxed(container); ctx("key", "(I)Ljava/lang/Object;") { method.visitLdcInsn(pc) }
                        method.visitMethodInsn(Opcodes.INVOKESTATIC, SUPPORT, "safeStatic", "(Ljava/lang/Object;Ljava/lang/Object;)Z", false); reject(Opcodes.IFEQ, pc)
                        boxed(container); ctx("key", "(I)Ljava/lang/Object;") { method.visitLdcInsn(pc) }
                        method.visitMethodInsn(Opcodes.INVOKESTATIC, SUPPORT, "readStatic", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false)
                        val result = fresh(Kind.REF); method.visitVarInsn(Opcodes.ASTORE, result.register); pop(); values.add(result)
                    }
                    Opcode.ADD, Opcode.SUB, Opcode.MUL, Opcode.NEG,
                    Opcode.EQ, Opcode.NEQ, Opcode.LT, Opcode.LE, Opcode.GT, Opcode.GE -> arithmetic(pc, op)
                    Opcode.JUMP_IF_FALSE -> {
                        require(values.size == 1) { "predicate leaves body temporaries" }
                        val condition = values.last()
                        when (condition.kind) {
                            Kind.BOOL -> load(condition)
                            Kind.REF -> {
                                load(condition); method.visitTypeInsn(Opcodes.INSTANCEOF, "java/lang/Boolean"); reject(Opcodes.IFEQ, pc)
                                load(condition); method.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Boolean")
                                method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false)
                            }
                            Kind.INTEGER -> { method.visitJumpInsn(Opcodes.GOTO, spill(pc)); method.visitInsn(Opcodes.ICONST_0) }
                        }
                        pop(); method.visitJumpInsn(Opcodes.IFEQ, nextLabel)
                    }
                    Opcode.MAKE_OBJECT -> {
                        val count = code.getIntOperand(pc)
                        require(count >= 0 && count <= values.size / 2) { "invalid object stack" }
                        val fields = ArrayList<Pair<Value, Value>>()
                        repeat(count) {
                            val key = pop(); val value = pop()
                            require(key.literal && (key.constant is String || key.constant is Int || key.constant is Long)) { "object keys must be literal String/Int/Long" }
                            fields.add(key to value)
                        }
                        val result = fresh(Kind.REF)
                        method.visitTypeInsn(Opcodes.NEW, "java/util/LinkedHashMap"); method.visitInsn(Opcodes.DUP); method.visitLdcInsn(count)
                        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/LinkedHashMap", "<init>", "(I)V", false)
                        method.visitVarInsn(Opcodes.ASTORE, result.register)
                        for ((key, value) in fields) {
                            load(result); boxed(key); boxed(value)
                            method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/LinkedHashMap", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false)
                            method.visitInsn(Opcodes.POP)
                        }
                        values.add(result)
                    }
                    Opcode.ARRAY_BUILDER_ADD -> {
                        require(values.size == 1 && pc == next - 1) { "unsupported append stack" }
                        val value = pop()
                        ctx("append", "(Ljava/lang/Object;I)V") { boxed(value); method.visitLdcInsn(pc) }
                    }
                    else -> error("Unsupported local opcode $op")
                }
                maxHeight = maxOf(maxHeight, values.size)
            }
            require(values.isEmpty()) { "body leaves temporary values" }
            method.visitLabel(nextLabel)
            ctx("next", "(IIIZ)I") {
                method.visitLdcInsn(next); method.visitLdcInsn(start); method.visitLdcInsn(exit); method.visitVarInsn(Opcodes.ILOAD, 2)
            }
            method.visitInsn(Opcodes.ICONST_1); method.visitJumpInsn(Opcodes.IF_ICMPEQ, head)
            ctx("finish", "(Z)V") { method.visitVarInsn(Opcodes.ILOAD, 2) }; method.visitInsn(Opcodes.RETURN)
            for (spill in spills.values) {
                method.visitLabel(spill.label)
                ctx("beginSpill", "(IZ)V") { method.visitLdcInsn(spill.pc); method.visitVarInsn(Opcodes.ILOAD, 2) }
                for (value in spill.values) ctx("spill", "(Ljava/lang/Object;)V") { boxed(value) }
                method.visitInsn(Opcodes.RETURN)
            }
            val end = Label(); method.visitLabel(end)
            method.visitMaxs(0, 0); method.visitEnd(); writer.visitEnd()
            val bytes = writer.toByteArray()
            require(end.offset <= 6000) { "generated method exceeds bounded JVM code size" }
            return bytes
        }

        private fun arithmetic(pc: Int, op: Opcode) {
            val unary = op == Opcode.NEG
            require(values.size >= if (unary) 1 else 2) { "invalid arithmetic stack" }
            val right = values.last(); val left = if (unary) right else values[values.lastIndex - 1]
            integer(left, 3, 9, pc)
            if (!unary) integer(right, 5, 10, pc)
            val comparison = op in COMPARISONS
            val result = fresh(if (comparison) Kind.BOOL else Kind.INTEGER)
            method.visitVarInsn(Opcodes.LLOAD, 3)
            if (!unary) method.visitVarInsn(Opcodes.LLOAD, 5)
            if (comparison) {
                method.visitInsn(Opcodes.LCMP)
                val yes = Label(); val done = Label()
                method.visitJumpInsn(when (op) { Opcode.EQ -> Opcodes.IFEQ; Opcode.NEQ -> Opcodes.IFNE; Opcode.LT -> Opcodes.IFLT; Opcode.LE -> Opcodes.IFLE; Opcode.GT -> Opcodes.IFGT; else -> Opcodes.IFGE }, yes)
                method.visitInsn(Opcodes.ICONST_0); method.visitJumpInsn(Opcodes.GOTO, done)
                method.visitLabel(yes); method.visitInsn(Opcodes.ICONST_1); method.visitLabel(done)
                method.visitVarInsn(Opcodes.ISTORE, result.register)
            } else {
                method.visitInsn(when (op) { Opcode.ADD -> Opcodes.LADD; Opcode.SUB -> Opcodes.LSUB; Opcode.MUL -> Opcodes.LMUL; else -> Opcodes.LNEG })
                method.visitVarInsn(Opcodes.LSTORE, result.register)
                if (op == Opcode.MUL) {
                    val safe = Label()
                    method.visitVarInsn(Opcodes.LLOAD, 3); method.visitInsn(Opcodes.LCONST_0); method.visitInsn(Opcodes.LCMP); method.visitJumpInsn(Opcodes.IFEQ, safe)
                    load(result); method.visitVarInsn(Opcodes.LLOAD, 3); method.visitInsn(Opcodes.LDIV); method.visitVarInsn(Opcodes.LLOAD, 5); method.visitInsn(Opcodes.LCMP); reject(Opcodes.IFNE, pc)
                    method.visitLabel(safe)
                }
                cap(result.register, pc); resultTag(result, unary)
            }
            typedUsed(); pop(); if (!unary) pop(); values.add(result); primitiveSites++
        }
    }

    private fun prefixShape(code: Bytecode, ops: List<Opcode>, init: Int): Boolean {
        val stack = ArrayList<Boolean>()
        fun pop(count: Int): Boolean { if (count < 0 || count > stack.size) return false; repeat(count) { stack.removeAt(stack.lastIndex) }; return true }
        for (pc in 0 until init) {
            when (ops[pc]) {
                Opcode.PUSH, Opcode.LOAD_VAR, Opcode.LOAD_SCOPE, Opcode.LOAD_LOCAL -> stack.add(false)
                Opcode.ARRAY_BUILDER_INIT -> stack.add(true)
                Opcode.DUP -> { if (stack.isEmpty()) return false; stack.add(stack.last()) }
                Opcode.POP, Opcode.STORE_VAR, Opcode.STORE_LOCAL -> if (!pop(1)) return false
                Opcode.SWAP -> { if (stack.size < 2) return false; val value = stack.last(); stack[stack.lastIndex] = stack[stack.lastIndex - 1]; stack[stack.lastIndex - 1] = value }
                Opcode.ACCESS_STATIC, Opcode.NEG -> { if (!pop(1)) return false; stack.add(false) }
                Opcode.ADD, Opcode.SUB, Opcode.MUL, Opcode.EQ, Opcode.NEQ, Opcode.LT, Opcode.LE, Opcode.GT, Opcode.GE -> { if (!pop(2)) return false; stack.add(false) }
                Opcode.MAKE_OBJECT, Opcode.OUTPUT -> { val n = code.getIntOperand(pc); if (n < 0 || n > Int.MAX_VALUE / 2 || !pop(n * 2)) return false; stack.add(false) }
                Opcode.NOP -> Unit
                else -> return false
            }
        }
        return stack.size >= 2 && stack[stack.lastIndex - 1]
    }
    private const val SAFE = 9007199254740991L
    private const val CLASS = "io/github/ehlyzov/branchline/vm/generated/CompiledLocalComprehension"
    private const val BINARY = "io.github.ehlyzov.branchline.vm.generated.CompiledLocalComprehension"
    private const val KERNEL = "io/github/ehlyzov/branchline/vm/LocalLoopKernel"
    private const val CONTEXT = "io/github/ehlyzov/branchline/vm/LocalLoopContext"
    private const val SUPPORT = "io/github/ehlyzov/branchline/vm/LocalLoopSupport"
    private val COMPARISONS = setOf(Opcode.EQ, Opcode.NEQ, Opcode.LT, Opcode.LE, Opcode.GT, Opcode.GE)
    private val BODY = setOf(Opcode.PUSH, Opcode.LOAD_VAR, Opcode.LOAD_SCOPE, Opcode.LOAD_LOCAL, Opcode.ACCESS_STATIC,
        Opcode.ADD, Opcode.SUB, Opcode.MUL, Opcode.NEG, Opcode.EQ, Opcode.NEQ, Opcode.LT, Opcode.LE, Opcode.GT, Opcode.GE,
        Opcode.JUMP_IF_FALSE, Opcode.MAKE_OBJECT, Opcode.ARRAY_BUILDER_ADD)
    private val OUTER = (BODY - setOf(Opcode.JUMP_IF_FALSE, Opcode.ARRAY_BUILDER_ADD)) +
        setOf(Opcode.STORE_VAR, Opcode.STORE_LOCAL, Opcode.DUP, Opcode.POP, Opcode.SWAP, Opcode.ARRAY_BUILDER_INIT, Opcode.ARRAY_BUILDER_FINISH, Opcode.OUTPUT, Opcode.NOP)
}
