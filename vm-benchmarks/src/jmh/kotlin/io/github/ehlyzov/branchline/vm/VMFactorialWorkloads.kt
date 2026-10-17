@file:OptIn(ExperimentalVMApi::class)

package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.FuncDecl
import io.github.ehlyzov.branchline.Lexer
import io.github.ehlyzov.branchline.Parser
import io.github.ehlyzov.branchline.TransformDecl
import io.github.ehlyzov.branchline.ir.Exec
import io.github.ehlyzov.branchline.ir.ToIR
import io.github.ehlyzov.branchline.std.StdLib
import java.security.MessageDigest

/** Fixed independent fixtures shared by execution, preparation and memory probes. */
public class VMFactorialFixture(public val workload: String, public val dataset: String) {
    private val size: DatasetSize = when (dataset) {
        "small" -> DatasetSize.SMALL
        "large" -> DatasetSize.LARGE
        else -> error("Unknown dataset: $dataset")
    }
    public val input: Map<String, Any?> = makeInput()
    public val source: String = when (workload) {
        "paths" -> VMTransformPrograms.pathExpressions
        "comprehension" -> VMTransformPrograms.arrayComprehension
        "reduction" -> VMTransformPrograms.typicalTransform
        "holdoutProjection" -> """
            TRANSFORM Holdout {
                OUTPUT {rows: [{id: o.id, score: o.total * 3 + o.id, sku: o.items[0].sku}
                    FOR EACH o IN input.orders WHERE o.total > 55]}
            }
        """.trimIndent()
        "holdoutDeepPaths" -> """
            TRANSFORM Holdout {
                LET first = input.envelope.payload.document.orders[0].items[0].sku;
                LET last = input.envelope.payload.document.orders[${size.orders - 1}].items[${size.itemsPerOrder - 1}].qty;
                OUTPUT {first: first, last: last, name: input.envelope.payload.document.customer.name}
            }
        """.trimIndent()
        "fallbackClosure" -> """
            TRANSFORM Control {
                LET twice = (x) -> x * 2;
                OUTPUT {value: twice(input.orders[0].total)}
            }
        """.trimIndent()
        else -> error("Unknown workload: $workload")
    }
    public val expected: Any = when (workload) {
        "paths" -> linkedMapOf("customer" to "Customer-$dataset", "firstSku" to "SKU-0-0", "firstQty" to 1, "secondSku" to "SKU-0-1")
        "comprehension" -> linkedMapOf(
            "expensive" to (0 until size.orders).filter { 50 + it % 250 > 150 },
            "skus" to (0 until size.itemsPerOrder).map { "SKU-0-$it" },
        )
        "reduction" -> linkedMapOf("orderCount" to size.orders, "total" to (0 until size.orders).sumOf { 50 + it % 250 })
        "holdoutProjection" -> mapOf("rows" to (0 until size.orders).filter { 50 + it % 250 > 55 }.map {
            linkedMapOf("id" to it, "score" to (50 + it % 250) * 3 + it, "sku" to "SKU-$it-0")
        })
        "holdoutDeepPaths" -> linkedMapOf("first" to "SKU-0-0", "last" to 1 + (size.itemsPerOrder - 1) % 5, "name" to "Customer-$dataset")
        "fallbackClosure" -> mapOf("value" to 100)
        else -> error("Unknown workload: $workload")
    }

    private fun makeInput(): Map<String, Any?> {
        val native = BenchDatasets.buildInput(size)
        return if (workload == "holdoutDeepPaths") linkedMapOf("envelope" to linkedMapOf("payload" to linkedMapOf("document" to native))) else native
    }

    public fun freshEnv(): MutableMap<String, Any?> = BenchDatasets.buildEnv(input)
    public fun assertOutput(actual: Any?) {
        check(expected == actual) { "$workload/$dataset: expected=$expected actual=$actual" }
    }
    public fun assertPristine() { check(input == makeInput()) { "$workload/$dataset input mutated" } }

    public fun prepare(engine: String, collectDiagnostics: Boolean = false): VMFactorialExecutable {
        val funcs: Map<String, FuncDecl> = emptyMap()
        val parsed = Parser(Lexer(source).lex(), source).parse()
        val transform = parsed.decls.filterIsInstance<TransformDecl>().single()
        val ir = ToIR(funcs, StdLib.fns).compile(transform.body.statements)
        if (engine == "interpreter") return VMFactorialInterpreter(Exec(ir, hostFns = StdLib.fns, hostFnMeta = StdLib.meta))
        val vm = VM(StdLib.fns, funcs)
        val bytecode = Compiler(funcs, StdLib.fns).compile(ir)
        // Keep the control on the original VM API, including with a preserved old VM jar.
        if (engine == "baseline") return VMFactorialBaseline(vm, bytecode)
        val options = when (engine) {
            "slots" -> VMExperimentalOptions(slots = true, collectDiagnostics = collectDiagnostics)
            "fusion" -> VMExperimentalOptions(fusion = true, collectDiagnostics = collectDiagnostics)
            "both" -> VMExperimentalOptions(slots = true, fusion = true, collectDiagnostics = collectDiagnostics)
            else -> error("Unknown engine: $engine")
        }
        return VMFactorialMachine(vm, vm.prepareExperimental(bytecode, options))
    }

    public fun validate(executable: VMFactorialExecutable): Map<String, Any?> {
        repeat(3) { assertOutput(executable.execute(freshEnv())); assertPristine() }
        val metadata = executable.metadata()
        if (workload in primaryWorkloads) {
            if (metadata["requestedSlots"] == true) check(metadata["effectiveSlots"] == true && metadata["fallbackReason"] == null) { "Primary slots workload did not execute its treatment: $metadata" }
            if (metadata["requestedFusion"] == true) check(metadata["effectiveFusion"] == true && metadata["fallbackReason"] == null) { "Primary fusion workload did not execute its treatment: $metadata" }
        }
        if (metadata["collectDiagnostics"] == true) {
            if (metadata["effectiveSlots"] == true) check((metadata["slotReads"] as Long) + (metadata["slotWrites"] as Long) > 0L) { "Effective slots without measured mechanism use: $metadata" }
            if (metadata["effectiveFusion"] == true) check((metadata["fusedDispatches"] as Long) > 0L) { "Effective fusion without measured mechanism use: $metadata" }
        }
        if (workload == "fallbackClosure" && (metadata["requestedSlots"] == true || metadata["requestedFusion"] == true)) {
            check(metadata["fallbackReason"] is String && metadata["effectiveSlots"] == false && metadata["effectiveFusion"] == false) {
                "Closure control did not report explicit ordinary-VM fallback: $metadata"
            }
        }
        return linkedMapOf(
            "workload" to workload, "dataset" to dataset,
            "sourceSha256" to MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) },
            "expectedOutputHash" to expected.hashCode(), "validatedExecutions" to 3,
        ) + metadata
    }

    public companion object {
        public val engines: List<String> = listOf("interpreter", "baseline", "slots", "fusion", "both")
        public val primaryWorkloads: List<String> = listOf("paths", "comprehension", "reduction", "holdoutProjection", "holdoutDeepPaths")
    }
}

public interface VMFactorialExecutable {
    public fun execute(env: MutableMap<String, Any?>): Any?
    public fun metadata(): Map<String, Any?>
}

private class VMFactorialInterpreter(private val exec: Exec) : VMFactorialExecutable {
    override fun execute(env: MutableMap<String, Any?>): Any? = exec.run(env)
    override fun metadata(): Map<String, Any?> = mapOf("engine" to "interpreter", "effective" to "interpreter")
}

private class VMFactorialBaseline(private val vm: VM, private val bytecode: Bytecode) : VMFactorialExecutable {
    override fun execute(env: MutableMap<String, Any?>): Any? = vm.execute(bytecode, env)
    override fun metadata(): Map<String, Any?> = mapOf(
        "engine" to "baseline", "executionApi" to "VM.execute",
        "requestedSlots" to false, "requestedFusion" to false, "collectDiagnostics" to false,
        "effectiveSlots" to false, "effectiveFusion" to false, "fallbackReason" to null,
        "slotReads" to 0L, "slotWrites" to 0L, "loopSlotWrites" to 0L,
        "mirroredStoresElided" to 0L, "fusedDispatches" to 0L,
        "instructionsElided" to 0L, "hostBarriers" to 0L,
    )
}

private class VMFactorialMachine(private val vm: VM, private val plan: VMExperimentalPlan) : VMFactorialExecutable {
    override fun execute(env: MutableMap<String, Any?>): Any? = vm.executeExperimental(plan, env)
    override fun metadata(): Map<String, Any?> {
        val report = vm.experimentalReport
        return linkedMapOf(
            "requestedSlots" to report.requested.slots, "requestedFusion" to report.requested.fusion,
            "collectDiagnostics" to report.requested.collectDiagnostics,
            "effectiveSlots" to report.effective.slots, "effectiveFusion" to report.effective.fusion,
            "fallbackReason" to report.fallbackReason,
            "slotReads" to report.slotReads, "slotWrites" to report.slotWrites,
            "loopSlotWrites" to report.loopSlotWrites, "mirroredStoresElided" to report.mirroredStoresElided,
            "fusedDispatches" to report.fusedDispatches, "instructionsElided" to report.instructionsElided,
            "hostBarriers" to report.hostBarriers,
        )
    }
}
