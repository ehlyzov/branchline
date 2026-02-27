package io.github.ehlyzov.branchline.contract

import io.github.ehlyzov.branchline.Token
import kotlinx.serialization.Serializable

@Serializable
public data class AnalysisContract(
    val input: AnalysisRequirementSchema,
    val output: AnalysisGuaranteeSchema,
    val source: ContractSource,
    val metadata: AnalysisContractMetadata = AnalysisContractMetadata(),
)

@Serializable
public data class AnalysisRequirementSchema(
    val root: AnalysisRequirementNode,
    val requirements: List<AnalysisRequirementExpr>,
    val opaqueRegions: List<OpaqueRegion>,
)

@Serializable
public data class AnalysisGuaranteeSchema(
    val root: AnalysisGuaranteeNode,
    val mayEmitNull: Boolean,
    val opaqueRegions: List<OpaqueRegion>,
)

@Serializable
public data class AnalysisRequirementNode(
    val required: Boolean,
    val shape: ValueShape,
    val open: Boolean,
    val children: LinkedHashMap<String, AnalysisRequirementNode>,
    val evidence: List<AnalysisInferenceEvidence>,
)

@Serializable
public data class AnalysisGuaranteeNode(
    val required: Boolean,
    val shape: ValueShape,
    val open: Boolean,
    val origin: OriginKind,
    val children: LinkedHashMap<String, AnalysisGuaranteeNode>,
    val evidence: List<AnalysisInferenceEvidence>,
)

@Serializable
public sealed interface AnalysisRequirementExpr {
    @Serializable
    public data class AllOf(val children: List<AnalysisRequirementExpr>) : AnalysisRequirementExpr

    @Serializable
    public data class AnyOf(val children: List<AnalysisRequirementExpr>) : AnalysisRequirementExpr

    @Serializable
    public data class PathPresent(val path: AccessPath) : AnalysisRequirementExpr

    @Serializable
    public data class PathNonNull(val path: AccessPath) : AnalysisRequirementExpr
}

@Serializable
public data class OpaqueRegion(
    val path: AccessPath,
    val reason: DynamicReason,
)

@Serializable
public data class AnalysisInferenceEvidence(
    val sourceSpans: List<Token>,
    val ruleId: String,
    val confidence: Double,
    val notes: String? = null,
)

@Serializable
public data class AnalysisContractMetadata(
    val runtimeFit: RuntimeFitExtensionPoint = RuntimeFitExtensionPoint(),
    val typeEval: TypeEvalExtensionPoint = TypeEvalExtensionPoint(),
    val inference: InferenceExtensionPoint = InferenceExtensionPoint(),
)

@Serializable
public data class RuntimeFitExtensionPoint(
    val supported: Boolean = true,
    val strategy: String = "static-base-with-observed-evidence",
)

@Serializable
public data class TypeEvalExtensionPoint(
    val supported: Boolean = true,
    val rules: List<String> = listOf(
        "coalesce",
        "numeric-binary-op",
        "logical-operator",
    ),
)

@Serializable
public data class InferenceExtensionPoint(
    val inputTypeSeeded: Boolean = false,
)
