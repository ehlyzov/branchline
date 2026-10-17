@file:OptIn(ExperimentalVMApi::class)

package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.Lexer
import io.github.ehlyzov.branchline.Parser
import io.github.ehlyzov.branchline.TransformDecl
import io.github.ehlyzov.branchline.ir.ToIR
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LoopBodyRuntimeTest {
    private fun compile(body: String): Bytecode {
        val source = "TRANSFORM Unrelated { $body }"
        val transform = Parser(Lexer(source).lex()).parse().decls.filterIsInstance<TransformDecl>().single()
        return Compiler().compile(ToIR(emptyMap(), emptyMap()).compile(transform.body.statements))
    }

    @Test
    fun generated_loop_has_native_backedge_and_fixed_helpers_without_opcode_switch() {
        val plan = JvmLoopCompiler.prepare(compile("OUTPUT {values: [row.amount * 2 + 7 FOR EACH row IN input.rows WHERE row.amount > -3]}"))
        assertEquals(null, plan.fallbackReason)
        val bytes = assertNotNull(JvmLoopCompiler.generatedClassBytes(plan))
        var switches = 0
        var backedges = 0
        val methods = mutableListOf<String>()
        val visited = HashSet<Label>()
        ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
                if (name != "run") return null
                return object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitLabel(label: Label) { visited.add(label) }
                    override fun visitJumpInsn(opcode: Int, label: Label) { if (label in visited) backedges++ }
                    override fun visitTableSwitchInsn(min: Int, max: Int, dflt: Label, vararg labels: Label) { switches++ }
                    override fun visitLookupSwitchInsn(dflt: Label, keys: IntArray, labels: Array<out Label>) { switches++ }
                    override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
                        assertEquals("io/github/ehlyzov/branchline/vm/LoopBodyContext", owner)
                        methods.add(name)
                    }
                }
            }
        }, 0)
        assertEquals(0, switches)
        assertTrue(backedges > 0)
        assertEquals(1, methods.count { it == "nextForeach" })
        assertEquals(1, methods.count { it == "popTruth" })
        assertTrue("mul" in methods && "add" in methods && "accessStatic" in methods)
        val vm = VM()
        assertEquals(mapOf("values" to listOf(3, 15)), vm.executeLoopBodies(plan,
            mutableMapOf("input" to mapOf("rows" to listOf(mapOf("amount" to -2), mapOf("amount" to 4))))))
        assertTrue(vm.loopBodyExecuted)
        assertEquals(1, vm.loopBodyEntries)
    }

    @Test
    fun custom_observer_cannot_reenter_other_apis_or_reset_outer_status() {
        val code = compile("OUTPUT {values: [item.value FOR EACH item IN input.rows]}")
        val plan = JvmLoopCompiler.prepare(code)
        val nested = Bytecode.fromInstructions(listOf(Instruction.PUSH(99)))
        lateinit var vm: VM
        val nestedExperimental = VM().prepareExperimental(nested)
        val nestedBlocks = VM().prepareBlocks(nested)
        var calls = 0
        val row = object : Map<String, Any?> by mapOf("value" to 7) {
            override fun get(key: String): Any? {
                calls++
                val report = vm.experimentalReport
                assertFailsWith<IllegalStateException> { vm.executeExperimental(nestedExperimental) }
                assertSame(report, vm.experimentalReport)
                assertFailsWith<IllegalStateException> { vm.executeBlocks(nestedBlocks) }
                assertFailsWith<IllegalStateException> { vm.executeLoopBodies(plan) }
                assertTrue(vm.loopBodyExecuted)
                assertEquals(1, vm.loopBodyEntries)
                return 7
            }
        }
        vm = VM()
        repeat(2) {
            assertEquals(mapOf("values" to listOf(7)), vm.executeLoopBodies(plan,
                mutableMapOf("input" to mapOf("rows" to listOf(row)))))
            assertEquals(null, vm.loopBodyFallbackReason)
        }
        assertEquals(2, calls)
    }
}
