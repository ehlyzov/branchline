package io.github.ehlyzov.branchline.cli

import io.github.ehlyzov.branchline.Lexer
import io.github.ehlyzov.branchline.Parser
import io.github.ehlyzov.branchline.TransformDecl
import io.github.ehlyzov.branchline.contract.ContractValidationMode
import io.github.ehlyzov.branchline.debug.Debug
import io.github.ehlyzov.branchline.json.JsonKeyMode
import io.github.ehlyzov.branchline.json.JsonParseOptions
import io.github.ehlyzov.branchline.json.parseJsonObjectInput
import io.github.ehlyzov.branchline.json.parseJsonObjectInputSelected
import io.github.ehlyzov.branchline.json.planJsonInputSelection
import io.github.ehlyzov.branchline.vm.VMExec
import io.github.ehlyzov.branchline.vm.VMExecutionPath
import io.github.ehlyzov.branchline.vm.VMFallbackPolicy

public enum class JsonMaterializationMode { SELECTIVE, EAGER }

public data class ExperimentalJsonPipelineOptions(
    val forceEager: Boolean = false,
    val jsonOptions: JsonParseOptions = JsonParseOptions(),
    val contractsMode: ContractValidationMode = ContractValidationMode.OFF,
)

public class JsonMaterializationReport internal constructor(
    public val effectiveMode: JsonMaterializationMode,
    public val reason: String,
    selectedRootKeys: Set<String>,
) {
    private val keys = selectedRootKeys.toSet()
    public val selectedRootKeys: Set<String> get() = keys.toSet()
}

/**
 * Opt-in prototype for reusable, sequential JSON-text transformations. Preparation is outside
 * execution; every call validates all JSON and returns fresh compact output. Plans and values
 * retain no input text. The VM retains its last environment, as in ordinary execution.
 * This API does not accept tracing, fanout or shared-store configuration. Ambient Debug tracing
 * requests eager input at each materialization call, including when enabled after preparation.
 */
public class ExperimentalJsonPipeline private constructor(
    private val runtime: BranchlineProgram,
    private val transform: TransformDecl,
    private val vmExec: VMExec,
    private val options: ExperimentalJsonPipelineOptions,
    private val preparedReport: JsonMaterializationReport,
) {
    private val keys = preparedReport.selectedRootKeys
    private val tracingReport = JsonMaterializationReport(
        JsonMaterializationMode.EAGER, "AMBIENT_TRACING", emptySet(),
    )

    /**
     * Immutable snapshot of the effective plan for the current ambient context, not execution
     * history. Reading again after Debug.tracer changes reflects the new context. Other eager
     * reasons (including forceEager) retain their preparation-time reason.
     */
    public val report: JsonMaterializationReport
        get() = if (preparedReport.effectiveMode == JsonMaterializationMode.SELECTIVE && Debug.tracer != null) {
            tracingReport
        } else preparedReport

    public fun materialize(jsonText: String): Map<String, Any?> =
        if (report.effectiveMode == JsonMaterializationMode.SELECTIVE) {
            parseJsonObjectInputSelected(jsonText, keys, options.jsonOptions)
        } else parseJsonObjectInput(jsonText, options.jsonOptions)

    public fun execute(jsonText: String): String {
        val result = runtime.executeVmWithContracts(transform, materialize(jsonText), vmExec, options.contractsMode)
        check(vmExec.lastExecution?.path == VMExecutionPath.VM_BYTECODE) { "Expected strict VM bytecode execution" }
        return formatJson(result.output, pretty = false, numberMode = options.jsonOptions.numberMode)
    }

    public companion object {
        public fun prepare(
            source: String,
            transformName: String? = null,
            options: ExperimentalJsonPipelineOptions = ExperimentalJsonPipelineOptions(),
        ): ExperimentalJsonPipeline {
            val runtime = BranchlineProgram(source)
            val transform = runtime.selectTransform(transformName)
            // Reuse the ordinary semantic/bytecode authority; this bounded planner has no runtime effects.
            val parsed = Parser(Lexer(source).lex(), source).parse()
            val plan = planJsonInputSelection(parsed, transform)
            val reason = when {
                options.forceEager -> "FORCE_EAGER"
                options.contractsMode != ContractValidationMode.OFF -> "CONTRACTS_ENABLED"
                options.jsonOptions.keyMode != JsonKeyMode.STRING -> "NUMERIC_KEYS"
                else -> plan.reason
            }
            val selective = reason == "STATIC_TOP_LEVEL" && plan.supported
            val report = JsonMaterializationReport(
                if (selective) JsonMaterializationMode.SELECTIVE else JsonMaterializationMode.EAGER,
                reason,
                if (selective) plan.selectedRootKeys else emptySet(),
            )
            val bytecode = runtime.compileBytecode(transform)
            val vmExec = runtime.prepareVmExec(transform, bytecode, VMFallbackPolicy.NONE)
            return ExperimentalJsonPipeline(runtime, transform, vmExec, options, report)
        }
    }
}
