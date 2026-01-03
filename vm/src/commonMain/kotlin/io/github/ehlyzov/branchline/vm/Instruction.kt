package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.ObjKey

/**
 * Operand types for table-driven instructions
 */
enum class OperandType {
    NONE,
    INT,
    STRING,
    OBJ_KEY,
    CONST
}

/**
 * Lookup tables for opcode metadata
 */
object InstructionTable {
    // The order must match Opcode ordinal order exactly.
    val operandTypes: Array<Array<OperandType>> = arrayOf(
        arrayOf(OperandType.CONST), // PUSH
        emptyArray(), // DUP
        emptyArray(), // POP
        emptyArray(), // SWAP
        arrayOf(OperandType.STRING), // LOAD_VAR
        arrayOf(OperandType.STRING), // STORE_VAR
        arrayOf(OperandType.STRING), // LOAD_SCOPE
        arrayOf(OperandType.INT), // LOAD_LOCAL
        arrayOf(OperandType.INT), // STORE_LOCAL
        emptyArray(), // ADD
        emptyArray(), // SUB
        emptyArray(), // MUL
        emptyArray(), // DIV
        emptyArray(), // MOD
        emptyArray(), // NEG
        emptyArray(), // EQ
        emptyArray(), // NEQ
        emptyArray(), // LT
        emptyArray(), // LE
        emptyArray(), // GT
        emptyArray(), // GE
        emptyArray(), // AND
        emptyArray(), // OR
        emptyArray(), // NOT
        emptyArray(), // COALESCE
        arrayOf(OperandType.INT), // MAKE_OBJECT
        arrayOf(OperandType.INT), // MAKE_ARRAY
        arrayOf(OperandType.OBJ_KEY), // ACCESS_STATIC
        emptyArray(), // ACCESS_DYNAMIC
        arrayOf(OperandType.OBJ_KEY), // SET_STATIC
        emptyArray(), // SET_DYNAMIC
        emptyArray(), // APPEND
        emptyArray(), // CONCAT
        arrayOf(OperandType.INT), // JUMP
        arrayOf(OperandType.INT), // JUMP_IF_TRUE
        arrayOf(OperandType.INT), // JUMP_IF_FALSE
        arrayOf(OperandType.INT), // JUMP_IF_NULL
        arrayOf(OperandType.STRING, OperandType.INT), // CALL
        arrayOf(OperandType.INT, OperandType.STRING, OperandType.INT), // CALL_HOST (index, name, argc)
        arrayOf(OperandType.STRING, OperandType.INT), // CALL_FN
        arrayOf(OperandType.INT), // CALL_LAMBDA
        emptyArray(), // RETURN
        emptyArray(), // RETURN_VALUE
        arrayOf(OperandType.INT), // OUTPUT
        arrayOf(OperandType.INT), // MODIFY
        arrayOf(OperandType.STRING, OperandType.INT), // INIT_FOREACH
        arrayOf(OperandType.INT, OperandType.INT), // NEXT_FOREACH
        arrayOf(OperandType.INT), // TRY_START
        emptyArray(), // TRY_END
        arrayOf(OperandType.STRING, OperandType.INT), // CATCH
        emptyArray(), // ABORT
        arrayOf(OperandType.STRING), // TRACE
        arrayOf(OperandType.STRING), // BREAKPOINT
        arrayOf(OperandType.INT), // LINE
        emptyArray(), // NOP
        emptyArray() // SUSPEND
    )
}

/**
 * Stack-based Virtual Machine instruction set for Branchline DSL
 *
 * The VM operates on a stack-based architecture where:
 * - Values are pushed onto and popped from an operand stack
 * - Local variables are stored in a separate environment/frame
 * - Instructions manipulate the stack and environment
 */
sealed class Instruction {

    // === Stack Operations ===

    /** Push a literal value onto the stack */
    data class PUSH(val value: Any?) : Instruction()

    /** Duplicate the top stack element */
    object DUP : Instruction()

    /** Pop and discard the top stack element */
    object POP : Instruction()

    /** Swap the top two stack elements */
    object SWAP : Instruction()

    // === Variable Operations ===

    /** Load a variable by name onto the stack */
    data class LOAD_VAR(val name: String) : Instruction()

    /** Store the top stack value into a variable */
    data class STORE_VAR(val name: String) : Instruction()

    /** Load a value from the current scope/environment */
    data class LOAD_SCOPE(val name: String) : Instruction()

    /** Load a local variable by index */
    data class LOAD_LOCAL(val index: Int) : Instruction()

    /** Store the top stack value into a local variable by index */
    data class STORE_LOCAL(val index: Int) : Instruction()

    // === Arithmetic Operations ===

    /** Add the top two stack elements */
    object ADD : Instruction()

    /** Subtract the top two stack elements */
    object SUB : Instruction()

    /** Multiply the top two stack elements */
    object MUL : Instruction()

    /** Divide the top two stack elements */
    object DIV : Instruction()

    /** Modulo the top two stack elements */
    object MOD : Instruction()

    /** Negate the top stack element */
    object NEG : Instruction()

    // === Comparison Operations ===

    /** Check equality of top two stack elements */
    object EQ : Instruction()

    /** Check inequality of top two stack elements */
    object NEQ : Instruction()

    /** Less than comparison */
    object LT : Instruction()

    /** Less than or equal comparison */
    object LE : Instruction()

    /** Greater than comparison */
    object GT : Instruction()

    /** Greater than or equal comparison */
    object GE : Instruction()

    // === Logical Operations ===

    /** Logical AND of top two stack elements */
    object AND : Instruction()

    /** Logical OR of top two stack elements */
    object OR : Instruction()

    /** Logical NOT of top stack element */
    object NOT : Instruction()

    /** Null coalescing operator */
    object COALESCE : Instruction()

    // === Object/Array Operations ===

    /** Create a new object from key-value pairs on stack */
    data class MAKE_OBJECT(val fieldCount: Int) : Instruction()

    /** Create a new array from elements on stack */
    data class MAKE_ARRAY(val elementCount: Int) : Instruction()

    /** Access object property or array element (static key) */
    data class ACCESS_STATIC(val key: ObjKey) : Instruction()

    /** Access object property or array element (dynamic key from stack) */
    object ACCESS_DYNAMIC : Instruction()

    /** Set object property or array element (static key) */
    data class SET_STATIC(val key: ObjKey) : Instruction()

    /** Set object property or array element (dynamic key from stack) */
    object SET_DYNAMIC : Instruction()

    /** Append value to array/list */
    object APPEND : Instruction()

    /** Concatenate two arrays */
    object CONCAT : Instruction()

    // === Control Flow ===

    /** Unconditional jump to instruction address */
    data class JUMP(val address: Int) : Instruction()

    /** Jump if top stack element is truthy */
    data class JUMP_IF_TRUE(val address: Int) : Instruction()

    /** Jump if top stack element is falsy */
    data class JUMP_IF_FALSE(val address: Int) : Instruction()

    /** Jump if top stack element is null */
    data class JUMP_IF_NULL(val address: Int) : Instruction()

    // === Function Calls ===

    /** Call a function with arguments from stack */
    data class CALL(val name: String, val argCount: Int) : Instruction()

    /** Call a host function resolved by index (with name for initial resolution) */
    data class CALL_HOST(val index: Int, val name: String, val argCount: Int) : Instruction()

    /** Call a user-defined function (FuncDecl) by name with arguments */
    data class CALL_FN(val name: String, val argCount: Int) : Instruction()

    /** Call a lambda/closure with arguments from stack */
    data class CALL_LAMBDA(val argCount: Int) : Instruction()

    /** Return from current function */
    object RETURN : Instruction()

    /** Return with a value from stack */
    object RETURN_VALUE : Instruction()

    // === Special Branchline Operations ===

    /** Create output record from stack values */
    data class OUTPUT(val fieldCount: Int) : Instruction()

    /** Specialized OUTPUT for 1 field (key, value) */
    object OUTPUT_1 : Instruction()

    /** Specialized OUTPUT for 2 fields (k1,v1,k2,v2) */
    object OUTPUT_2 : Instruction()

    /** Modify object with property updates */
    data class MODIFY(val updateCount: Int) : Instruction()

    /** Initialize iteration over collection */
    data class INIT_FOREACH(val varName: String, val jumpToEnd: Int) : Instruction()

    /** Continue iteration (jump back to loop start or fall through) */
    data class NEXT_FOREACH(val jumpToStart: Int, val jumpToEnd: Int) : Instruction()

    /** Start try block */
    data class TRY_START(val catchAddress: Int) : Instruction()

    /** End try block */
    object TRY_END : Instruction()

    /** Handle caught exception */
    data class CATCH(val exceptionVar: String, val retryCount: Int) : Instruction()

    /** Abort execution with value */
    object ABORT : Instruction()

    // === Debug/Trace Operations ===

    /** Emit trace event for debugging */
    data class TRACE(val event: String) : Instruction()

    /** Mark instruction for debugging breakpoint */
    data class BREAKPOINT(val info: String) : Instruction()

    /** No operation - used for padding/alignment */
    object NOP : Instruction()

    /** Suspend execution and return a continuation/snapshot */
    object SUSPEND : Instruction()

    // === Meta Information ===

    /** Line number information for debugging */
    data class LINE(val lineNumber: Int) : Instruction()
}

/**
 * Represents a compiled program as a sequence of instructions
 */
data class Bytecode(
    val opcodes: IntArray,
    val intOperands: IntArray = IntArray(0),
    val stringOperands: Array<String> = emptyArray(),
    val objKeyOperands: Array<ObjKey> = emptyArray(),
    val constOperands: IntArray = IntArray(0),
    val intOperandOffsets: IntArray = IntArray(0),
    val stringOperandOffsets: IntArray = IntArray(0),
    val objKeyOperandOffsets: IntArray = IntArray(0),
    val constOperandOffsets: IntArray = IntArray(0),
    val constants: List<Any?> = emptyList(),
    val debugInfo: Map<Int, String> = emptyMap(),
    val traceMetadata: TraceMetadata = TraceMetadata(),

) {

    fun size(): Int = opcodes.size

    fun getOpcode(pc: Int): Int = opcodes[pc]

    fun getIntOperand(pc: Int, position: Int = 0): Int {
        val offset = intOperandOffsets[pc]
        return intOperands[offset + position]
    }

    fun getStringOperand(pc: Int, position: Int = 0): String {
        val offset = stringOperandOffsets[pc]
        return stringOperands[offset + position]
    }

    fun getObjKeyOperand(pc: Int, position: Int = 0): ObjKey {
        val offset = objKeyOperandOffsets[pc]
        return objKeyOperands[offset + position]
    }

    fun getConstOperand(pc: Int, position: Int = 0): Any? {
        val offset = constOperandOffsets[pc]
        return constants[constOperands[offset + position]]
    }

    fun hasDebugInfo(pc: Int): Boolean = debugInfo.containsKey(pc)

    fun getDebugInfo(pc: Int): String? = debugInfo[pc]

    // Compatibility: reconstruct an Instruction object for the given pc.
    fun getInstruction(pc: Int): Instruction {
        val op = Opcode.values()[getOpcode(pc)]
        return when (op) {
            Opcode.PUSH -> Instruction.PUSH(getConstOperand(pc))
            Opcode.DUP -> Instruction.DUP
            Opcode.POP -> Instruction.POP
            Opcode.SWAP -> Instruction.SWAP
            Opcode.LOAD_VAR -> Instruction.LOAD_VAR(getStringOperand(pc))
            Opcode.STORE_VAR -> Instruction.STORE_VAR(getStringOperand(pc))
            Opcode.LOAD_SCOPE -> Instruction.LOAD_SCOPE(getStringOperand(pc))
            Opcode.LOAD_LOCAL -> Instruction.LOAD_LOCAL(getIntOperand(pc))
            Opcode.STORE_LOCAL -> Instruction.STORE_LOCAL(getIntOperand(pc))
            Opcode.ADD -> Instruction.ADD
            Opcode.SUB -> Instruction.SUB
            Opcode.MUL -> Instruction.MUL
            Opcode.DIV -> Instruction.DIV
            Opcode.MOD -> Instruction.MOD
            Opcode.NEG -> Instruction.NEG
            Opcode.EQ -> Instruction.EQ
            Opcode.NEQ -> Instruction.NEQ
            Opcode.LT -> Instruction.LT
            Opcode.LE -> Instruction.LE
            Opcode.GT -> Instruction.GT
            Opcode.GE -> Instruction.GE
            Opcode.AND -> Instruction.AND
            Opcode.OR -> Instruction.OR
            Opcode.NOT -> Instruction.NOT
            Opcode.COALESCE -> Instruction.COALESCE
            Opcode.MAKE_OBJECT -> Instruction.MAKE_OBJECT(getIntOperand(pc))
            Opcode.MAKE_ARRAY -> Instruction.MAKE_ARRAY(getIntOperand(pc))
            Opcode.ACCESS_STATIC -> Instruction.ACCESS_STATIC(getObjKeyOperand(pc))
            Opcode.ACCESS_DYNAMIC -> Instruction.ACCESS_DYNAMIC
            Opcode.SET_STATIC -> Instruction.SET_STATIC(getObjKeyOperand(pc))
            Opcode.SET_DYNAMIC -> Instruction.SET_DYNAMIC
            Opcode.APPEND -> Instruction.APPEND
            Opcode.CONCAT -> Instruction.CONCAT
            Opcode.JUMP -> Instruction.JUMP(getIntOperand(pc))
            Opcode.JUMP_IF_TRUE -> Instruction.JUMP_IF_TRUE(getIntOperand(pc))
            Opcode.JUMP_IF_FALSE -> Instruction.JUMP_IF_FALSE(getIntOperand(pc))
            Opcode.JUMP_IF_NULL -> Instruction.JUMP_IF_NULL(getIntOperand(pc))
            Opcode.CALL -> Instruction.CALL(getStringOperand(pc, 0), getIntOperand(pc, 0))
            Opcode.CALL_HOST -> Instruction.CALL_HOST(getIntOperand(pc, 0), getStringOperand(pc, 0), getIntOperand(pc, 1))
            Opcode.CALL_FN -> Instruction.CALL_FN(getStringOperand(pc, 0), getIntOperand(pc, 0))
            Opcode.CALL_LAMBDA -> Instruction.CALL_LAMBDA(getIntOperand(pc))
            Opcode.RETURN -> Instruction.RETURN
            Opcode.RETURN_VALUE -> Instruction.RETURN_VALUE
            Opcode.OUTPUT -> {
                val n = getIntOperand(pc)
                if (n == 1) Instruction.OUTPUT_1 else if (n == 2) Instruction.OUTPUT_2 else Instruction.OUTPUT(n)
            }
            Opcode.MODIFY -> Instruction.MODIFY(getIntOperand(pc))
            Opcode.INIT_FOREACH -> Instruction.INIT_FOREACH(getStringOperand(pc, 0), getIntOperand(pc, 0))
            Opcode.NEXT_FOREACH -> Instruction.NEXT_FOREACH(getIntOperand(pc), getIntOperand(pc, 1))
            Opcode.TRY_START -> Instruction.TRY_START(getIntOperand(pc))
            Opcode.TRY_END -> Instruction.TRY_END
            Opcode.CATCH -> Instruction.CATCH(getStringOperand(pc, 0), getIntOperand(pc, 0))
            Opcode.ABORT -> Instruction.ABORT
            Opcode.TRACE -> Instruction.TRACE(getStringOperand(pc))
            Opcode.BREAKPOINT -> Instruction.BREAKPOINT(getStringOperand(pc))
            Opcode.LINE -> Instruction.LINE(getIntOperand(pc))
            Opcode.NOP -> Instruction.NOP
            Opcode.SUSPEND -> Instruction.SUSPEND
        }
    }

    // Compatibility: materialize instruction list for tests and tooling
    val instructions: List<Instruction>
        get() {
            val out = ArrayList<Instruction>(size())
            var i = 0
            while (i < size()) {
                out.add(getInstruction(i))
                i++
            }
            return out
        }

    companion object {
        fun fromInstructions(
            instructions: List<Instruction>,
            constants: List<Any?> = emptyList(),
            debugInfo: Map<Int, String> = emptyMap(),
            traceMetadata: TraceMetadata = TraceMetadata(),
        ): Bytecode {
            val (ops, arrays) = buildFromInstructions(instructions, constants, debugInfo)
            return Bytecode(
                opcodes = ops,
                intOperands = arrays.intOperands,
                stringOperands = arrays.stringOperands,
                objKeyOperands = arrays.objKeyOperands,
                constOperands = arrays.constOperands,
                intOperandOffsets = arrays.intOperandOffsets,
                stringOperandOffsets = arrays.stringOperandOffsets,
                objKeyOperandOffsets = arrays.objKeyOperandOffsets,
                constOperandOffsets = arrays.constOperandOffsets,
                constants = arrays.constants,
                debugInfo = arrays.debugInfo,
                traceMetadata = traceMetadata,
            )
        }
        private data class BuildArrays(
            val intOperands: IntArray,
            val stringOperands: Array<String>,
            val objKeyOperands: Array<ObjKey>,
            val constOperands: IntArray,
            val intOperandOffsets: IntArray,
            val stringOperandOffsets: IntArray,
            val objKeyOperandOffsets: IntArray,
            val constOperandOffsets: IntArray,
            val constants: List<Any?>,
            val debugInfo: Map<Int, String>,
        )

        private fun buildFromInstructions(
            instructions: List<Instruction>,
            seedConstants: List<Any?>,
            debugInfo: Map<Int, String>,
        ): Pair<IntArray, BuildArrays> {
            val opcodes = IntArray(instructions.size)

            val intList = ArrayList<Int>()
            val strList = ArrayList<String>()
            val keyList = ArrayList<ObjKey>()
            val constIndexList = ArrayList<Int>()

            val intOffsets = IntArray(instructions.size)
            val strOffsets = IntArray(instructions.size)
            val keyOffsets = IntArray(instructions.size)
            val constOffsets = IntArray(instructions.size)

            val consts = ArrayList<Any?>(seedConstants.size + 16)
            consts.addAll(seedConstants)

            var pc = 0
            while (pc < instructions.size) {
                val ins = instructions[pc]
                opcodes[pc] = when (ins) {
                    is Instruction.PUSH -> Opcode.PUSH.ordinal
                    Instruction.DUP -> Opcode.DUP.ordinal
                    Instruction.POP -> Opcode.POP.ordinal
                    Instruction.SWAP -> Opcode.SWAP.ordinal
                    is Instruction.LOAD_VAR -> Opcode.LOAD_VAR.ordinal
                    is Instruction.STORE_VAR -> Opcode.STORE_VAR.ordinal
                    is Instruction.LOAD_SCOPE -> Opcode.LOAD_SCOPE.ordinal
                    is Instruction.LOAD_LOCAL -> Opcode.LOAD_LOCAL.ordinal
                    is Instruction.STORE_LOCAL -> Opcode.STORE_LOCAL.ordinal
                    Instruction.ADD -> Opcode.ADD.ordinal
                    Instruction.SUB -> Opcode.SUB.ordinal
                    Instruction.MUL -> Opcode.MUL.ordinal
                    Instruction.DIV -> Opcode.DIV.ordinal
                    Instruction.MOD -> Opcode.MOD.ordinal
                    Instruction.NEG -> Opcode.NEG.ordinal
                    Instruction.EQ -> Opcode.EQ.ordinal
                    Instruction.NEQ -> Opcode.NEQ.ordinal
                    Instruction.LT -> Opcode.LT.ordinal
                    Instruction.LE -> Opcode.LE.ordinal
                    Instruction.GT -> Opcode.GT.ordinal
                    Instruction.GE -> Opcode.GE.ordinal
                    Instruction.AND -> Opcode.AND.ordinal
                    Instruction.OR -> Opcode.OR.ordinal
                    Instruction.NOT -> Opcode.NOT.ordinal
                    Instruction.COALESCE -> Opcode.COALESCE.ordinal
                    is Instruction.MAKE_OBJECT -> Opcode.MAKE_OBJECT.ordinal
                    is Instruction.MAKE_ARRAY -> Opcode.MAKE_ARRAY.ordinal
                    is Instruction.ACCESS_STATIC -> Opcode.ACCESS_STATIC.ordinal
                    Instruction.ACCESS_DYNAMIC -> Opcode.ACCESS_DYNAMIC.ordinal
                    is Instruction.SET_STATIC -> Opcode.SET_STATIC.ordinal
                    Instruction.SET_DYNAMIC -> Opcode.SET_DYNAMIC.ordinal
                    Instruction.APPEND -> Opcode.APPEND.ordinal
                    Instruction.CONCAT -> Opcode.CONCAT.ordinal
                    is Instruction.JUMP -> Opcode.JUMP.ordinal
                    is Instruction.JUMP_IF_TRUE -> Opcode.JUMP_IF_TRUE.ordinal
                    is Instruction.JUMP_IF_FALSE -> Opcode.JUMP_IF_FALSE.ordinal
                    is Instruction.JUMP_IF_NULL -> Opcode.JUMP_IF_NULL.ordinal
                    is Instruction.CALL -> Opcode.CALL.ordinal
                    is Instruction.CALL_HOST -> Opcode.CALL_HOST.ordinal
                    is Instruction.CALL_FN -> Opcode.CALL_FN.ordinal
                    is Instruction.CALL_LAMBDA -> Opcode.CALL_LAMBDA.ordinal
                    Instruction.RETURN -> Opcode.RETURN.ordinal
                    Instruction.RETURN_VALUE -> Opcode.RETURN_VALUE.ordinal
                    is Instruction.OUTPUT -> Opcode.OUTPUT.ordinal
                    Instruction.OUTPUT_1 -> Opcode.OUTPUT.ordinal
                    Instruction.OUTPUT_2 -> Opcode.OUTPUT.ordinal
                    is Instruction.MODIFY -> Opcode.MODIFY.ordinal
                    is Instruction.INIT_FOREACH -> Opcode.INIT_FOREACH.ordinal
                    is Instruction.NEXT_FOREACH -> Opcode.NEXT_FOREACH.ordinal
                    is Instruction.TRY_START -> Opcode.TRY_START.ordinal
                    Instruction.TRY_END -> Opcode.TRY_END.ordinal
                    is Instruction.CATCH -> Opcode.CATCH.ordinal
                    Instruction.ABORT -> Opcode.ABORT.ordinal
                    is Instruction.TRACE -> Opcode.TRACE.ordinal
                    is Instruction.BREAKPOINT -> Opcode.BREAKPOINT.ordinal
                    is Instruction.LINE -> Opcode.LINE.ordinal
                    Instruction.NOP -> Opcode.NOP.ordinal
                    Instruction.SUSPEND -> Opcode.SUSPEND.ordinal
                }

                intOffsets[pc] = intList.size
                strOffsets[pc] = strList.size
                keyOffsets[pc] = keyList.size
                constOffsets[pc] = constIndexList.size

                when (ins) {
                    is Instruction.PUSH -> {
                        constIndexList.add(consts.size)
                        consts.add(ins.value)
                    }
                    is Instruction.LOAD_VAR -> strList.add(ins.name)
                    is Instruction.STORE_VAR -> strList.add(ins.name)
                    is Instruction.LOAD_SCOPE -> strList.add(ins.name)
                    is Instruction.LOAD_LOCAL -> intList.add(ins.index)
                    is Instruction.STORE_LOCAL -> intList.add(ins.index)
                    is Instruction.MAKE_OBJECT -> intList.add(ins.fieldCount)
                    is Instruction.MAKE_ARRAY -> intList.add(ins.elementCount)
                    is Instruction.ACCESS_STATIC -> keyList.add(ins.key)
                    is Instruction.SET_STATIC -> keyList.add(ins.key)
                    is Instruction.JUMP -> intList.add(ins.address)
                    is Instruction.JUMP_IF_TRUE -> intList.add(ins.address)
                    is Instruction.JUMP_IF_FALSE -> intList.add(ins.address)
                    is Instruction.JUMP_IF_NULL -> intList.add(ins.address)
                    is Instruction.CALL -> {
                        strList.add(ins.name)
                        intList.add(ins.argCount)
                    }
                    is Instruction.CALL_HOST -> {
                        intList.add(ins.index)
                        strList.add(ins.name)
                        intList.add(ins.argCount)
                    }
                    is Instruction.CALL_FN -> {
                        strList.add(ins.name)
                        intList.add(ins.argCount)
                    }
                    is Instruction.CALL_LAMBDA -> intList.add(ins.argCount)
                    is Instruction.OUTPUT -> intList.add(ins.fieldCount)
                    Instruction.OUTPUT_1 -> intList.add(1)
                    Instruction.OUTPUT_2 -> intList.add(2)
                    is Instruction.MODIFY -> intList.add(ins.updateCount)
                    is Instruction.INIT_FOREACH -> {
                        strList.add(ins.varName)
                        intList.add(ins.jumpToEnd)
                    }
                    is Instruction.NEXT_FOREACH -> {
                        intList.add(ins.jumpToStart)
                        intList.add(ins.jumpToEnd)
                    }
                    is Instruction.TRY_START -> intList.add(ins.catchAddress)
                    is Instruction.CATCH -> {
                        strList.add(ins.exceptionVar)
                        intList.add(ins.retryCount)
                    }
                    is Instruction.TRACE -> strList.add(ins.event)
                    is Instruction.BREAKPOINT -> strList.add(ins.info)
                    is Instruction.LINE -> intList.add(ins.lineNumber)
                    else -> {}
                }

                pc++
            }

            val built = BuildArrays(
                intOperands = intList.toIntArray(),
                stringOperands = strList.toTypedArray(),
                objKeyOperands = keyList.toTypedArray(),
                constOperands = constIndexList.toIntArray(),
                intOperandOffsets = intOffsets,
                stringOperandOffsets = strOffsets,
                objKeyOperandOffsets = keyOffsets,
                constOperandOffsets = constOffsets,
                constants = consts.toList(),
                debugInfo = debugInfo.toMap(),
            )
            return Pair(opcodes, built)
        }
    }
}
