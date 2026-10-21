package io.github.ehlyzov.branchline.vm

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import io.github.ehlyzov.branchline.I32
import io.github.ehlyzov.branchline.I64
import io.github.ehlyzov.branchline.IBig
import io.github.ehlyzov.branchline.ObjKey
import io.github.ehlyzov.branchline.runtime.isPlatformIntegerNumber
import io.github.ehlyzov.branchline.runtime.bignum.BLBigDec
import io.github.ehlyzov.branchline.runtime.bignum.BLBigInt
import io.github.ehlyzov.branchline.runtime.bignum.blBigIntParse

/**
 * Utility for serializing VM bytecode and constant pools to a portable JSON payload.
 */
object BytecodeIO {
    private val json = Json { prettyPrint = true }

    @Serializable
    data class SerializedBytecode(
        val instructions: List<SerializedInstruction>
    )

    @Serializable
    data class SerializedInstruction(
        val op: String,
        val s: String? = null,
        val i: Int? = null,
        val n: Int? = null,
        val key: String? = null,
        val value: SerializedValue? = null
    )

    @Serializable
    data class SerializedValue(
        val t: String,
        val s: String? = null,
        val b: Boolean? = null,
        val arr: List<SerializedValue>? = null,
        val obj: Map<String, SerializedValue>? = null,
    )

    fun encodeToString(bytecode: Bytecode): String =
        json.encodeToString(SerializedBytecode.serializer(), serializeBytecode(bytecode))

    fun decodeFromString(raw: String): Bytecode =
        deserializeBytecode(json.decodeFromString(SerializedBytecode.serializer(), raw))

    fun serializeBytecode(bytecode: Bytecode): SerializedBytecode =
        SerializedBytecode(bytecode.instructions.map { serializeInstruction(it) })

    fun deserializeBytecode(serialized: SerializedBytecode): Bytecode =
        Bytecode.fromInstructions(serialized.instructions.map { deserializeInstruction(it) })

    fun serializeValue(value: Any?): SerializedValue = when (value) {
        null -> SerializedValue("null")
        is Boolean -> SerializedValue("bool", b = value)
        is String -> SerializedValue("str", s = value)
        is Int, is Long, is Double, is Float -> {
            // JS primitive numbers share runtime type checks. Fractional or
            // unsafe integral Double text must not be decoded through toLong().
            val integer = isPlatformIntegerNumber(value) &&
                (value is Long || (value as Number).toDouble() in
                    -9_007_199_254_740_991.0..9_007_199_254_740_991.0)
            val negativeZero = (value as Number).toDouble().let { it == 0.0 && 1.0 / it < 0.0 }
            SerializedValue(if (integer && !negativeZero) "i64" else "f64",
                s = if (negativeZero) "-0.0" else value.toString())
        }
        is BLBigInt -> SerializedValue("bi", s = value.toString())
        // BigDecimal.toString() (not toPlainString()) preserves negative scale.
        is BLBigDec -> SerializedValue("bd", s = bytecodeDecimalText(value))
        is List<*> -> SerializedValue("arr", arr = value.map { serializeValue(it) })
        is Array<*> -> SerializedValue("arr", arr = value.map { serializeValue(it) })
        is Map<*, *> -> LinkedHashMap<String, SerializedValue>(value.size).let { mapped ->
            for ((k, v) in value) mapped[k.toString()] = serializeValue(v)
            SerializedValue("obj", obj = mapped)
        }
        else -> SerializedValue("str", s = value.toString())
    }

    fun deserializeValue(value: SerializedValue): Any? {
        fun invalid(reason: String): Nothing = throw IllegalArgumentException(
            "Invalid bytecode value '${value.t}': $reason. Upgrade Branchline or recompile the script with the target CLI using blc.",
        )
        fun text(): String {
            if (value.b != null || value.arr != null || value.obj != null) invalid("unexpected payload fields")
            return value.s ?: invalid("missing string payload")
        }
        return when (value.t) {
            "null" -> {
                if (value.s != null || value.b != null || value.arr != null || value.obj != null) invalid("unexpected payload fields")
                null
            }
            "bool" -> {
                if (value.s != null || value.arr != null || value.obj != null) invalid("unexpected payload fields")
                value.b ?: invalid("missing boolean payload")
            }
            "str" -> text()
            "i64" -> text().toLongOrNull() ?: invalid("expected a signed 64-bit integer")
            "f64" -> text().toDoubleOrNull() ?: invalid("expected a floating-point number")
            "bi", "bd" -> {
                val payload = text()
                if (!(if (value.t == "bi") integerText else decimalText).matches(payload)) invalid("malformed numeric text")
                try {
                    if (value.t == "bi") blBigIntParse(payload) else parseBytecodeDecimal(payload)
                } catch (failure: IllegalArgumentException) {
                    invalid(failure.message ?: "numeric payload is outside the platform representation")
                }
            }
            "arr" -> {
                if (value.s != null || value.b != null || value.obj != null) invalid("unexpected payload fields")
                (value.arr ?: invalid("missing array payload")).map { deserializeValue(it) }
            }
            "obj" -> {
                if (value.s != null || value.b != null || value.arr != null) invalid("unexpected payload fields")
                (value.obj ?: invalid("missing object payload")).mapValues { (_, v) -> deserializeValue(v) }
            }
            else -> throw IllegalArgumentException(
                "Unsupported bytecode value type '${value.t}'. Upgrade Branchline or recompile the script with the target CLI using blc.",
            )
        }
    }

    private val integerText = Regex("[+-]?[0-9]+")
    private val decimalText = Regex("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")

    private fun unwrapKeyConst(key: ObjKey): Any = when (key) {
        is ObjKey.Name -> key.v
        is I32 -> key.v
        is I64 -> key.v
        is IBig -> key.v
    }

    private fun serializeInstruction(instruction: Instruction): SerializedInstruction = when (instruction) {
        is Instruction.PUSH -> SerializedInstruction("PUSH", value = serializeValue(instruction.value))
        Instruction.DUP -> SerializedInstruction("DUP")
        Instruction.POP -> SerializedInstruction("POP")
        Instruction.SWAP -> SerializedInstruction("SWAP")
        is Instruction.LOAD_VAR -> SerializedInstruction("LOAD_VAR", s = instruction.name)
        is Instruction.STORE_VAR -> SerializedInstruction("STORE_VAR", s = instruction.name)
        is Instruction.LOAD_SCOPE -> SerializedInstruction("LOAD_SCOPE", s = instruction.name)
        is Instruction.LOAD_LOCAL -> SerializedInstruction("LOAD_LOCAL", i = instruction.index)
        is Instruction.STORE_LOCAL -> SerializedInstruction("STORE_LOCAL", i = instruction.index)
        Instruction.ADD -> SerializedInstruction("ADD")
        Instruction.SUB -> SerializedInstruction("SUB")
        Instruction.MUL -> SerializedInstruction("MUL")
        Instruction.DIV -> SerializedInstruction("DIV")
        Instruction.IDIV -> SerializedInstruction("IDIV")
        Instruction.MOD -> SerializedInstruction("MOD")
        Instruction.NEG -> SerializedInstruction("NEG")
        Instruction.EQ -> SerializedInstruction("EQ")
        Instruction.NEQ -> SerializedInstruction("NEQ")
        Instruction.LT -> SerializedInstruction("LT")
        Instruction.LE -> SerializedInstruction("LE")
        Instruction.GT -> SerializedInstruction("GT")
        Instruction.GE -> SerializedInstruction("GE")
        Instruction.AND -> SerializedInstruction("AND")
        Instruction.OR -> SerializedInstruction("OR")
        Instruction.NOT -> SerializedInstruction("NOT")
        Instruction.COALESCE -> SerializedInstruction("COALESCE")
        is Instruction.MAKE_OBJECT -> SerializedInstruction("MAKE_OBJECT", n = instruction.fieldCount)
        is Instruction.MAKE_ARRAY -> SerializedInstruction("MAKE_ARRAY", n = instruction.elementCount)
        is Instruction.ACCESS_STATIC -> SerializedInstruction("ACCESS_STATIC", key = unwrapKeyConst(instruction.key).toString())
        Instruction.ACCESS_DYNAMIC -> SerializedInstruction("ACCESS_DYNAMIC")
        is Instruction.SET_STATIC -> SerializedInstruction("SET_STATIC", key = unwrapKeyConst(instruction.key).toString())
        Instruction.SET_DYNAMIC -> SerializedInstruction("SET_DYNAMIC")
        is Instruction.PRIVATE_SET_STATIC -> SerializedInstruction(
            "PRIVATE_SET_STATIC", key = unwrapKeyConst(instruction.key).toString(),
            s = when (instruction.key) {
                is ObjKey.Name -> "name"
                is I32 -> "i32"
                is I64 -> "i64"
                is IBig -> "ibig"
            },
        )
        Instruction.PRIVATE_SET_DYNAMIC -> SerializedInstruction("PRIVATE_SET_DYNAMIC")
        Instruction.PRIVATE_ARRAY_APPEND -> SerializedInstruction("PRIVATE_ARRAY_APPEND")
        Instruction.APPEND -> SerializedInstruction("APPEND")
        Instruction.ARRAY_BUILDER_INIT -> SerializedInstruction("ARRAY_BUILDER_INIT")
        Instruction.ARRAY_BUILDER_ADD -> SerializedInstruction("ARRAY_BUILDER_ADD")
        Instruction.ARRAY_BUILDER_FINISH -> SerializedInstruction("ARRAY_BUILDER_FINISH")
        Instruction.PLUS_ASSIGN -> SerializedInstruction("PLUS_ASSIGN")
        Instruction.CONCAT -> SerializedInstruction("CONCAT")
        is Instruction.JUMP -> SerializedInstruction("JUMP", i = instruction.address)
        is Instruction.JUMP_IF_TRUE -> SerializedInstruction("JUMP_IF_TRUE", i = instruction.address)
        is Instruction.JUMP_IF_FALSE -> SerializedInstruction("JUMP_IF_FALSE", i = instruction.address)
        is Instruction.JUMP_IF_NULL -> SerializedInstruction("JUMP_IF_NULL", i = instruction.address)
        is Instruction.CALL -> SerializedInstruction("CALL", s = instruction.name, n = instruction.argCount)
        is Instruction.CALL_HOST -> SerializedInstruction("CALL_HOST", s = instruction.name, i = instruction.index, n = instruction.argCount)
        is Instruction.CALL_FN -> SerializedInstruction("CALL_FN", s = instruction.name, n = instruction.argCount)
        is Instruction.CALL_LAMBDA -> SerializedInstruction("CALL_LAMBDA", n = instruction.argCount)
        Instruction.RETURN -> SerializedInstruction("RETURN")
        Instruction.RETURN_VALUE -> SerializedInstruction("RETURN_VALUE")
        is Instruction.OUTPUT -> SerializedInstruction("OUTPUT", n = instruction.fieldCount)
        Instruction.OUTPUT_1 -> SerializedInstruction("OUTPUT_1")
        Instruction.OUTPUT_2 -> SerializedInstruction("OUTPUT_2")
        is Instruction.MODIFY -> SerializedInstruction("MODIFY", n = instruction.updateCount)
        is Instruction.INIT_FOREACH -> SerializedInstruction("INIT_FOREACH", s = instruction.varName, i = instruction.jumpToEnd)
        is Instruction.NEXT_FOREACH -> SerializedInstruction("NEXT_FOREACH", i = instruction.jumpToStart, n = instruction.jumpToEnd)
        is Instruction.TRY_START -> SerializedInstruction("TRY_START", i = instruction.catchAddress)
        Instruction.TRY_END -> SerializedInstruction("TRY_END")
        is Instruction.CATCH -> SerializedInstruction("CATCH", s = instruction.exceptionVar, n = instruction.retryCount)
        Instruction.ABORT -> SerializedInstruction("ABORT")
        is Instruction.TRACE -> SerializedInstruction("TRACE", s = instruction.event)
        is Instruction.BREAKPOINT -> SerializedInstruction("BREAKPOINT", s = instruction.info)
        is Instruction.LINE -> SerializedInstruction("LINE", i = instruction.lineNumber)
        Instruction.SUSPEND -> SerializedInstruction("SUSPEND")
        else -> throw IllegalArgumentException("Unsupported instruction for serialization: $instruction")
    }

    private fun deserializeInstruction(serialized: SerializedInstruction): Instruction = when (serialized.op) {
        "PUSH" -> Instruction.PUSH(deserializeValue(serialized.value!!))
        "DUP" -> Instruction.DUP
        "POP" -> Instruction.POP
        "SWAP" -> Instruction.SWAP
        "LOAD_VAR" -> Instruction.LOAD_VAR(serialized.s!!)
        "STORE_VAR" -> Instruction.STORE_VAR(serialized.s!!)
        "LOAD_SCOPE" -> Instruction.LOAD_SCOPE(serialized.s!!)
        "LOAD_LOCAL" -> Instruction.LOAD_LOCAL(serialized.i!!)
        "STORE_LOCAL" -> Instruction.STORE_LOCAL(serialized.i!!)
        "ADD" -> Instruction.ADD
        "SUB" -> Instruction.SUB
        "MUL" -> Instruction.MUL
        "DIV" -> Instruction.DIV
        "IDIV" -> Instruction.IDIV
        "MOD" -> Instruction.MOD
        "NEG" -> Instruction.NEG
        "EQ" -> Instruction.EQ
        "NEQ" -> Instruction.NEQ
        "LT" -> Instruction.LT
        "LE" -> Instruction.LE
        "GT" -> Instruction.GT
        "GE" -> Instruction.GE
        "AND" -> Instruction.AND
        "OR" -> Instruction.OR
        "NOT" -> Instruction.NOT
        "COALESCE" -> Instruction.COALESCE
        "MAKE_OBJECT" -> Instruction.MAKE_OBJECT(serialized.n!!)
        "MAKE_ARRAY" -> Instruction.MAKE_ARRAY(serialized.n!!)
        "ACCESS_STATIC" -> Instruction.ACCESS_STATIC(ObjKey.Name(serialized.key!!))
        "ACCESS_DYNAMIC" -> Instruction.ACCESS_DYNAMIC
        "SET_STATIC" -> Instruction.SET_STATIC(ObjKey.Name(serialized.key!!))
        "SET_DYNAMIC" -> Instruction.SET_DYNAMIC
        "PRIVATE_SET_STATIC" -> Instruction.PRIVATE_SET_STATIC(when (serialized.s) {
            "i32" -> I32(serialized.key!!.toInt())
            "i64" -> I64(serialized.key!!.toLong())
            "ibig" -> IBig(io.github.ehlyzov.branchline.runtime.bignum.blBigIntParse(serialized.key!!))
            else -> ObjKey.Name(serialized.key!!)
        })
        "PRIVATE_SET_DYNAMIC" -> Instruction.PRIVATE_SET_DYNAMIC
        "PRIVATE_ARRAY_APPEND" -> Instruction.PRIVATE_ARRAY_APPEND
        "APPEND" -> Instruction.APPEND
        "ARRAY_BUILDER_INIT" -> Instruction.ARRAY_BUILDER_INIT
        "ARRAY_BUILDER_ADD" -> Instruction.ARRAY_BUILDER_ADD
        "ARRAY_BUILDER_FINISH" -> Instruction.ARRAY_BUILDER_FINISH
        "PLUS_ASSIGN" -> Instruction.PLUS_ASSIGN
        "CONCAT" -> Instruction.CONCAT
        "JUMP" -> Instruction.JUMP(serialized.i!!)
        "JUMP_IF_TRUE" -> Instruction.JUMP_IF_TRUE(serialized.i!!)
        "JUMP_IF_FALSE" -> Instruction.JUMP_IF_FALSE(serialized.i!!)
        "JUMP_IF_NULL" -> Instruction.JUMP_IF_NULL(serialized.i!!)
        "CALL" -> Instruction.CALL(serialized.s!!, serialized.n!!)
        "CALL_HOST" -> Instruction.CALL_HOST(serialized.i!!, serialized.s!!, serialized.n!!)
        "CALL_FN" -> Instruction.CALL_FN(serialized.s!!, serialized.n!!)
        "CALL_LAMBDA" -> Instruction.CALL_LAMBDA(serialized.n!!)
        "RETURN" -> Instruction.RETURN
        "RETURN_VALUE" -> Instruction.RETURN_VALUE
        "OUTPUT" -> Instruction.OUTPUT(serialized.n!!)
        "OUTPUT_1" -> Instruction.OUTPUT_1
        "OUTPUT_2" -> Instruction.OUTPUT_2
        "MODIFY" -> Instruction.MODIFY(serialized.n!!)
        "INIT_FOREACH" -> Instruction.INIT_FOREACH(serialized.s!!, serialized.i!!)
        "NEXT_FOREACH" -> Instruction.NEXT_FOREACH(serialized.i!!, serialized.n!!)
        "TRY_START" -> Instruction.TRY_START(serialized.i!!)
        "TRY_END" -> Instruction.TRY_END
        "CATCH" -> Instruction.CATCH(serialized.s!!, serialized.n!!)
        "ABORT" -> Instruction.ABORT
        "TRACE" -> Instruction.TRACE(serialized.s!!)
        "BREAKPOINT" -> Instruction.BREAKPOINT(serialized.s!!)
        "LINE" -> Instruction.LINE(serialized.i!!)
        "SUSPEND" -> Instruction.SUSPEND
        else -> throw IllegalArgumentException(
            "Unsupported bytecode instruction '${serialized.op}'. Upgrade Branchline or recompile the script with the target CLI using blc.",
        )
    }
}

// Keep platform admission separate from arithmetic: JVM has arbitrary precision,
// while JS uses a signed Long for BigInt and a Double for BigDec.
internal expect fun parseBytecodeDecimal(text: String): BLBigDec
internal expect fun bytecodeDecimalText(value: BLBigDec): String
