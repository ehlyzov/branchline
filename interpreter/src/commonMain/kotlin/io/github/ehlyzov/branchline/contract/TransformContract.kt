package io.github.ehlyzov.branchline.contract

import io.github.ehlyzov.branchline.Token
import kotlinx.serialization.Serializable

@Serializable
public enum class ContractSource {
    EXPLICIT,
    INFERRED,
}

@Serializable
public enum class OriginKind {
    SET,
    MODIFY,
    APPEND,
    OUTPUT,
    MERGED,
}

@Serializable
public enum class DynamicReason {
    KEY,
    INDEX,
    COMPUTED,
}

@Serializable
public data class AccessPath(
    val segments: List<AccessSegment>,
)

@Serializable
public sealed interface AccessSegment {
    @Serializable
    public data class Field(val name: String) : AccessSegment

    @Serializable
    public data class Index(val index: String) : AccessSegment

    @Serializable
    public data object Dynamic : AccessSegment
}

@Serializable
public sealed interface ValueShape {
    @Serializable
    public data object Never : ValueShape

    @Serializable
    public data object Unknown : ValueShape

    @Serializable
    public data object Null : ValueShape

    @Serializable
    public data object BooleanShape : ValueShape

    @Serializable
    public data object NumberShape : ValueShape

    @Serializable
    public data object Bytes : ValueShape

    @Serializable
    public data object TextShape : ValueShape

    @Serializable
    public data class ArrayShape(val element: ValueShape) : ValueShape

    @Serializable
    public data class SetShape(val element: ValueShape) : ValueShape

    @Serializable
    public data class ObjectShape(
        val schema: SchemaGuarantee,
        val closed: Boolean,
    ) : ValueShape

    @Serializable
    public data class Union(val options: List<ValueShape>) : ValueShape
}

@Serializable
public data class FieldConstraint(
    val required: Boolean,
    val shape: ValueShape,
    val sourceSpans: List<Token>,
)

@Serializable
public data class FieldShape(
    val required: Boolean,
    val shape: ValueShape,
    val origin: OriginKind,
)

@Serializable
public data class DynamicAccess(
    val path: AccessPath,
    val valueShape: ValueShape?,
    val reason: DynamicReason,
)

@Serializable
public data class RequiredAnyOfGroup(
    val alternatives: List<AccessPath>,
)

@Serializable
public data class DynamicField(
    val path: AccessPath,
    val valueShape: ValueShape?,
    val reason: DynamicReason,
)

@Serializable
public data class SchemaRequirement(
    val fields: LinkedHashMap<String, FieldConstraint>,
    val open: Boolean,
    val dynamicAccess: List<DynamicAccess>,
    val requiredAnyOf: List<RequiredAnyOfGroup>,
)

@Serializable
public data class SchemaGuarantee(
    val fields: LinkedHashMap<String, FieldShape>,
    val mayEmitNull: Boolean,
    val dynamicFields: List<DynamicField>,
)

@Serializable
public data class TransformContract(
    val input: RequirementSchema,
    val output: GuaranteeSchema,
    val source: ContractSource,
    val metadata: ContractMetadata = ContractMetadata(),
)

@Serializable
public data class RequirementSchema(
    val root: Node,
    val obligations: List<ContractObligation>,
    val opaqueRegions: List<OpaqueRegion>,
    val evidence: List<InferenceEvidence> = emptyList(),
)

@Serializable
public data class GuaranteeSchema(
    val root: Node,
    val obligations: List<ContractObligation>,
    val mayEmitNull: Boolean,
    val opaqueRegions: List<OpaqueRegion>,
    val evidence: List<InferenceEvidence> = emptyList(),
)

@Serializable
public data class Node(
    val required: Boolean,
    val kind: NodeKind,
    val open: Boolean = true,
    val children: LinkedHashMap<String, Node> = linkedMapOf(),
    val element: Node? = null,
    val options: List<Node> = emptyList(),
    val domains: List<ValueDomain> = emptyList(),
    val origin: OriginKind? = null,
)

@Serializable
public enum class NodeKind {
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
public data class ContractObligation(
    val expr: ConstraintExpr,
    val confidence: Double = 1.0,
    val ruleId: String = "inferred",
    val heuristic: Boolean = false,
)

@Serializable
public sealed interface ConstraintExpr {
    @Serializable
    public data class PathPresent(val path: AccessPath) : ConstraintExpr

    @Serializable
    public data class PathNonNull(val path: AccessPath) : ConstraintExpr

    @Serializable
    public data class OneOf(val children: List<ConstraintExpr>) : ConstraintExpr

    @Serializable
    public data class AllOf(val children: List<ConstraintExpr>) : ConstraintExpr

    @Serializable
    public data class ForAll(
        val path: AccessPath,
        val requiredFields: List<String> = emptyList(),
        val fieldDomains: LinkedHashMap<String, ValueDomain> = linkedMapOf(),
        val requireAnyOf: List<List<String>> = emptyList(),
    ) : ConstraintExpr

    @Serializable
    public data class Exists(
        val path: AccessPath,
        val minCount: Int = 1,
    ) : ConstraintExpr

    @Serializable
    public data class DomainConstraint(
        val path: AccessPath,
        val domain: ValueDomain,
    ) : ConstraintExpr
}

@Serializable
public sealed interface ValueDomain {
    @Serializable
    public data class EnumText(val values: List<String>) : ValueDomain

    @Serializable
    public data class NumberRange(
        val min: Double? = null,
        val max: Double? = null,
        val integerOnly: Boolean = false,
    ) : ValueDomain

    @Serializable
    public data class Regex(val pattern: String) : ValueDomain
}

@Serializable
public data class InferenceEvidence(
    val sourceSpans: List<Token>,
    val ruleId: String,
    val confidence: Double,
    val notes: String? = null,
)

@Serializable
public data class ContractMetadata(
    val runtimeFit: RuntimeFitExtensionPoint = RuntimeFitExtensionPoint(),
    val typeEval: TypeEvalExtensionPoint = TypeEvalExtensionPoint(),
    val inference: InferenceExtensionPoint = InferenceExtensionPoint(),
    val satisfiability: SatisfiabilityMetadata = SatisfiabilityMetadata(),
)

@Serializable
public data class SatisfiabilityMetadata(
    val checked: Boolean = false,
    val satisfiable: Boolean = true,
    val diagnostics: List<String> = emptyList(),
)
