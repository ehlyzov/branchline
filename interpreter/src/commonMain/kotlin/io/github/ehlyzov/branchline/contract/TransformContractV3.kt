package io.github.ehlyzov.branchline.contract

import io.github.ehlyzov.branchline.Token
import kotlinx.serialization.Serializable

@Serializable
public data class TransformContractV3(
    val input: RequirementSchemaV3,
    val output: GuaranteeSchemaV3,
    val source: ContractSource,
    val metadata: ContractMetadataV3 = ContractMetadataV3(),
)

@Serializable
public data class RequirementSchemaV3(
    val root: NodeV3,
    val obligations: List<ContractObligationV3>,
    val opaqueRegions: List<OpaqueRegionV2>,
    val evidence: List<InferenceEvidenceV3> = emptyList(),
)

@Serializable
public data class GuaranteeSchemaV3(
    val root: NodeV3,
    val obligations: List<ContractObligationV3>,
    val mayEmitNull: Boolean,
    val opaqueRegions: List<OpaqueRegionV2>,
    val evidence: List<InferenceEvidenceV3> = emptyList(),
)

@Serializable
public data class NodeV3(
    val required: Boolean,
    val kind: NodeKindV3,
    val open: Boolean = true,
    val children: LinkedHashMap<String, NodeV3> = linkedMapOf(),
    val element: NodeV3? = null,
    val options: List<NodeV3> = emptyList(),
    val domains: List<ValueDomainV3> = emptyList(),
    val origin: OriginKind? = null,
)

@Serializable
public enum class NodeKindV3 {
    OBJECT,
    ARRAY,
    SET,
    UNION,
    ANY,
    NEVER,
    NULL,
    BOOLEAN,
    NUMBER,
    BYTES,
    TEXT,
}

@Serializable
public data class ContractObligationV3(
    val expr: ConstraintExprV3,
    val confidence: Double = 1.0,
    val ruleId: String = "inferred",
    val heuristic: Boolean = false,
)

@Serializable
public sealed interface ConstraintExprV3 {
    @Serializable
    public data class PathPresent(val path: AccessPath) : ConstraintExprV3

    @Serializable
    public data class PathNonNull(val path: AccessPath) : ConstraintExprV3

    @Serializable
    public data class OneOf(val children: List<ConstraintExprV3>) : ConstraintExprV3

    @Serializable
    public data class AllOf(val children: List<ConstraintExprV3>) : ConstraintExprV3

    @Serializable
    public data class ForAll(
        val path: AccessPath,
        val requiredFields: List<String> = emptyList(),
        val fieldDomains: LinkedHashMap<String, ValueDomainV3> = linkedMapOf(),
        val requireAnyOf: List<List<String>> = emptyList(),
    ) : ConstraintExprV3

    @Serializable
    public data class Exists(
        val path: AccessPath,
        val minCount: Int = 1,
    ) : ConstraintExprV3

    @Serializable
    public data class ValueDomain(
        val path: AccessPath,
        val domain: ValueDomainV3,
    ) : ConstraintExprV3
}

@Serializable
public sealed interface ValueDomainV3 {
    @Serializable
    public data class EnumText(val values: List<String>) : ValueDomainV3

    @Serializable
    public data class NumberRange(
        val min: Double? = null,
        val max: Double? = null,
        val integerOnly: Boolean = false,
    ) : ValueDomainV3

    @Serializable
    public data class Regex(val pattern: String) : ValueDomainV3
}

@Serializable
public data class InferenceEvidenceV3(
    val sourceSpans: List<Token>,
    val ruleId: String,
    val confidence: Double,
    val notes: String? = null,
)

@Serializable
public data class ContractMetadataV3(
    val runtimeFit: RuntimeFitExtensionPoint = RuntimeFitExtensionPoint(),
    val typeEval: TypeEvalExtensionPoint = TypeEvalExtensionPoint(),
    val inference: InferenceExtensionPoint = InferenceExtensionPoint(),
    val satisfiability: SatisfiabilityMetadataV3 = SatisfiabilityMetadataV3(),
)

@Serializable
public data class SatisfiabilityMetadataV3(
    val checked: Boolean = false,
    val satisfiable: Boolean = true,
    val diagnostics: List<String> = emptyList(),
)
