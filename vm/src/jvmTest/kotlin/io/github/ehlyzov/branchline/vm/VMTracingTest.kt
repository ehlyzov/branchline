package io.github.ehlyzov.branchline.vm

import kotlin.test.Test
import kotlin.test.assertEquals
import io.github.ehlyzov.branchline.AccessExpr
import io.github.ehlyzov.branchline.AccessSeg
import io.github.ehlyzov.branchline.ArrayExpr
import io.github.ehlyzov.branchline.Expr
import io.github.ehlyzov.branchline.IdentifierExpr
import io.github.ehlyzov.branchline.LiteralProperty
import io.github.ehlyzov.branchline.NumberLiteral
import io.github.ehlyzov.branchline.ObjKey
import io.github.ehlyzov.branchline.ObjectExpr
import io.github.ehlyzov.branchline.Property
import io.github.ehlyzov.branchline.StringExpr
import io.github.ehlyzov.branchline.Token
import io.github.ehlyzov.branchline.TokenType
import io.github.ehlyzov.branchline.debug.CollectingTracer
import io.github.ehlyzov.branchline.debug.TraceEvent
import io.github.ehlyzov.branchline.debug.TraceOptions
import io.github.ehlyzov.branchline.ir.IRAppendTo
import io.github.ehlyzov.branchline.ir.IRAppendVar
import io.github.ehlyzov.branchline.ir.IRExprOutput
import io.github.ehlyzov.branchline.ir.IRLet
import io.github.ehlyzov.branchline.ir.IRNode
import io.github.ehlyzov.branchline.ir.IROutput
import io.github.ehlyzov.branchline.ir.IRSet
import io.github.ehlyzov.branchline.ir.IRSetVar
import io.github.ehlyzov.branchline.I32
import io.github.ehlyzov.branchline.std.StdLib

class VMTracingTest {
    @Test
    fun pathWrite_set_static_emits_provenance_event() {
        val letNode = IRLet("o", obj(literal("a", obj(literal("b", num(1))))))
        val setNode = IRSet(access("o", "a", "b"), num(2))
        val outputNode = IRExprOutput(ident("o"))

        val run = runWithTracer(listOf(letNode, setNode, outputNode))

        val pathWrite = run.events.filterIsInstance<TraceEvent.PathWrite>().single()
        assertEquals("SET", pathWrite.op)
        assertEquals("o", pathWrite.root)
        assertEquals(listOf("a", "b"), pathWrite.path)
        assertEquals(1, pathWrite.old)
        assertEquals(2, pathWrite.new)
        assertEquals(mapOf("a" to mapOf("b" to 2)), run.result)
    }

    @Test
    fun pathWrite_append_dynamic_resolves_runtime_key() {
        val letObject = IRLet("o", obj())
        val letKey = IRLet("k", str("foo"))
        val append = IRAppendTo(accessDynamic("o", ident("k")), num(5), emptyArrayExpr())
        val output = IRExprOutput(ident("o"))

        val run = runWithTracer(listOf(letObject, letKey, append, output))

        val pathWrite = run.events.filterIsInstance<TraceEvent.PathWrite>().single()
        assertEquals("APPEND", pathWrite.op)
        assertEquals("o", pathWrite.root)
        assertEquals(listOf("foo"), pathWrite.path)
        assertEquals(null, pathWrite.old)
        assertEquals(listOf(5), pathWrite.new)
    }

    @Test
    fun pathWrite_append_var_captures_full_variable_change() {
        val letAcc = IRLet("acc", emptyArrayExpr())
        val append = IRAppendVar("acc", num(10), null)
        val output = IRExprOutput(ident("acc"))

        val run = runWithTracer(listOf(letAcc, append, output))

        val pathWrite = run.events.filterIsInstance<TraceEvent.PathWrite>().single()
        assertEquals("APPEND", pathWrite.op)
        assertEquals("acc", pathWrite.root)
        assertEquals(listOf("acc"), pathWrite.path)
        assertEquals(emptyList<Any?>(), pathWrite.old)
        assertEquals(listOf(10), pathWrite.new)
        assertEquals(listOf(10), run.result)
    }

    @Test
    fun output_event_emitted_after_output_instruction() {
        val letNode = IRLet("row", obj(literal("id", num(7))))
        val outputNode = IROutput(listOf(literal("row", ident("row"))))

        val run = runWithTracer(listOf(letNode, outputNode))

        val outputs = run.events.filterIsInstance<TraceEvent.Output>()
        assertEquals(1, outputs.size)
        val expected: Map<Any, Any?> = mapOf("row" to mapOf<Any, Any?>("id" to 7))
        assertEquals(expected, outputs.single().obj)
    }

    @Test
    fun enter_exit_events_follow_ir_nodes() {
        val letNode = IRLet("o", obj())
        val setVar = IRSetVar("o", obj(literal("x", num(1))))
        val outputNode = IRExprOutput(ident("o"))
        val tracer = CollectingTracer(TraceOptions(step = true, includeCalls = false))

        val run = runWithTracer(listOf(letNode, setVar, outputNode), tracer)
        val events = run.events
        val enterNodes = events.filterIsInstance<TraceEvent.Enter>().map { it.node }
        val exitNodes = events.filterIsInstance<TraceEvent.Exit>().map { it.node }

        assertEquals(listOf<IRNode>(letNode, setVar, outputNode), enterNodes)
        assertEquals(listOf<IRNode>(letNode, setVar, outputNode), exitNodes)
    }

    private data class RunResult(val result: Any?, val events: List<TraceEvent>, val tracer: CollectingTracer)

    private fun runWithTracer(nodes: List<IRNode>, tracer: CollectingTracer = CollectingTracer()): RunResult {
        val compiler = Compiler(funcs = emptyMap(), hostFns = StdLib.fns)
        val bytecode = compiler.compile(nodes)
        val vm = VM(StdLib.fns, emptyMap(), tracer)
        val result = vm.execute(bytecode, mutableMapOf())
        val events = tracer.events.map { it.event }
        return RunResult(result, events, tracer)
    }

    private fun ident(name: String): IdentifierExpr = IdentifierExpr(name, token(TokenType.IDENTIFIER, name))

    private fun num(value: Int): NumberLiteral = NumberLiteral(I32(value), token(TokenType.NUMBER, value.toString()))

    private fun str(value: String): StringExpr = StringExpr(value, token(TokenType.STRING, value))

    private fun obj(vararg props: Property): ObjectExpr = ObjectExpr(props.toList(), token(TokenType.LEFT_BRACE, "{"))

    private fun literal(name: String, expr: Expr): LiteralProperty = LiteralProperty(ObjKey.Name(name), expr)

    private fun emptyArrayExpr(): ArrayExpr = ArrayExpr(emptyList(), token(TokenType.LEFT_BRACKET, "["))

    private fun access(base: String, vararg path: String): AccessExpr {
        val baseExpr = ident(base)
        val segs = path.map { AccessSeg.Static(ObjKey.Name(it)) }
        return AccessExpr(baseExpr, segs, baseExpr.token)
    }

    private fun accessDynamic(base: String, keyExpr: Expr): AccessExpr {
        val baseExpr = ident(base)
        val segs = listOf(AccessSeg.Dynamic(keyExpr))
        return AccessExpr(baseExpr, segs, baseExpr.token)
    }

    private fun token(type: TokenType, lexeme: String): Token = Token(type, lexeme, 0, 0)
}
