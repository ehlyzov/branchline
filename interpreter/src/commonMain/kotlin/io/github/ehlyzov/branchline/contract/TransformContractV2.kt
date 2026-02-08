package io.github.ehlyzov.branchline.contract

import io.github.ehlyzov.branchline.Token
import kotlinx.serialization.Serializable

@Serializable
public data class TransformContractV2(
    val input: RequirementSchemaV2,
    val output: GuaranteeSchemaV2,
    val source: ContractSource,
    val metadata: ContractMetadataV2 = ContractMetadataV2(),
)

@Serializable
public data class RequirementSchemaV2(
    val root: RequirementNodeV2,
    val requirements: List<RequirementExprV2>,
    val opaqueRegions: List<OpaqueRegionV2>,
)

@Serializable
public data class GuaranteeSchemaV2(
    val root: GuaranteeNodeV2,
    val mayEmitNull: Boolean,
    val opaqueRegions: List<OpaqueRegionV2>,
)

@Serializable
public data class RequirementNodeV2(
    val required: Boolean,
    val shape: ValueShape,
    val open: Boolean,
    val children: LinkedHashMap<String, RequirementNodeV2>,
    val evidence: List<InferenceEvidenceV2>,
)

@Serializable
public data class GuaranteeNodeV2(
    val required: Boolean,
    val shape: ValueShape,
    val open: Boolean,
    val origin: OriginKind,
    val children: LinkedHashMap<String, GuaranteeNodeV2>,
    val evidence: List<InferenceEvidenceV2>,
)

@Serializable
public sealed interface RequirementExprV2 {
    @Serializable
    public data class AllOf(val children: List<RequirementExprV2>) : RequirementExprV2

    @Serializable
    public data class AnyOf(val children: List<RequirementExprV2>) : RequirementExprV2

    @Serializable
    public data class PathPresent(val path: AccessPath) : RequirementExprV2

    @Serializable
    public data class PathNonNull(val path: AccessPath) : RequirementExprV2
}

@Serializable
public data class OpaqueRegionV2(
    val path: AccessPath,
    val reason: DynamicReason,
)

@Serializable
public data class InferenceEvidenceV2(
    val sourceSpans: List<Token>,
    val ruleId: String,
    val confidence: Double,
    val notes: String? = null,
)

@Serializable
public data class ContractMetadataV2(
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
