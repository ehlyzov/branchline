package v2.vm

import v2.ExecutionEngine
import v2.testutils.compileAndRun
import kotlin.test.Test
import kotlin.test.assertEquals

class WritePathsVMTest {
    @Test
    fun set_object_nested() {
        val out = compileAndRun(
            """
            LET o = { a: { b: 0 } };
            SET o.a.b = 7;
            OUTPUT o;
            """.trimIndent(),
            engine = ExecutionEngine.VM
        )
        assertEquals(mapOf("a" to mapOf("b" to 7)), out)
    }

    @Test
    fun debug_dump_set_dynamic() {
        val program = """
            TRANSFORM __T { LET k = "x";
                LET o = {};
                SET o.[k] = 10;
                OUTPUT o;
            }
        """.trimIndent()

        val tokens = v2.Lexer(program).lex()
        val prog = v2.Parser(tokens, program).parse()
        val funcs = prog.decls.filterIsInstance<v2.FuncDecl>().associateBy { it.name }
        val transform = prog.decls.filterIsInstance<v2.TransformDecl>().single()
        val ir = v2.ir.ToIR(funcs, v2.std.StdLib.fns).compile((transform.body as v2.CodeBlock).statements)
        val compiler = Compiler(funcs, v2.std.StdLib.fns)
        val bytecode = compiler.compile(ir)
        val vm = VM(v2.std.StdLib.fns, funcs)
        vm.begin(bytecode, mutableMapOf())
        val pretty = VM.prettySnapshot(vm.snapshotJson())
        println(pretty)
    }

    @Test
    fun set_dynamic_key() {
        val out = compileAndRun(
            """
            LET k = "x";
            LET o = {};
            SET o.[k] = 10;
            OUTPUT o;
            """.trimIndent(),
            engine = ExecutionEngine.VM
        )
        println("set_dynamic_key out=" + out)
        assertEquals(mapOf("x" to 10), out)
    }

    @Test
    fun set_array_index() {
        val out = compileAndRun(
            """
            LET xs = [1,2,3];
            SET xs[1] = 42;
            OUTPUT { xs: xs };
            """.trimIndent(),
            engine = ExecutionEngine.VM
        )
        assertEquals(mapOf("xs" to listOf(1, 42, 3)), out)
    }

    @Test
    fun append_nested_with_init() {
        val out = compileAndRun(
            """
            LET o = { a: { b: null } };
            APPEND TO o.a.b 1 INIT [0];
            OUTPUT o;
            """.trimIndent(),
            engine = ExecutionEngine.VM
        )
        assertEquals(mapOf("a" to mapOf("b" to listOf(0, 1))), out)
    }

    @Test
    fun append_nested_existing_list() {
        val out = compileAndRun(
            """
            LET o = { a: { b: [10] } };
            APPEND TO o.a.b 11 INIT [0,0];
            OUTPUT o;
            """.trimIndent(),
            engine = ExecutionEngine.VM
        )
        println("append_nested_existing_list out=" + out)
        assertEquals(mapOf("a" to mapOf("b" to listOf(10, 11))), out)
    }

    @Test
    fun modify_nested_static_and_computed() {
        val out = compileAndRun(
            """
            LET k = "z";
            LET o = { a: { b: { x: 1 } } };
            LET t = o.a.b;
            MODIFY t { y: 2, [k]: 3 }
            SET o.a.b = t;
            OUTPUT o;
            """.trimIndent(),
            engine = ExecutionEngine.VM
        )
        assertEquals(mapOf("a" to mapOf("b" to mapOf("x" to 1, "y" to 2, "z" to 3))), out)
    }
}
