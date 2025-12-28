package v2.contract

import v2.Token

public enum class ContractSource {
    EXPLICIT,
    INFERRED,
}

public data class TransformContract(
    val input: SchemaRequirement,
    val output: SchemaGuarantee,
    val source: ContractSource,
)

public data class SchemaRequirement(
    val fields: LinkedHashMap<String, FieldConstraint>,
    val open: Boolean,
    val dynamicAccess: List<DynamicAccess>,
)

public data class SchemaGuarantee(
    val fields: LinkedHashMap<String, FieldShape>,
    val mayEmitNull: Boolean,
    val dynamicFields: List<DynamicField>,
)

public data class FieldConstraint(
    val required: Boolean,
    val shape: ValueShape,
    val sourceSpans: List<Token>,
)

public data class FieldShape(
    val required: Boolean,
    val shape: ValueShape,
    val origin: OriginKind,
)

public enum class OriginKind {
    SET,
    MODIFY,
    APPEND,
    OUTPUT,
    MERGED,
}

public data class DynamicAccess(
    val path: AccessPath,
    val valueShape: ValueShape?,
    val reason: DynamicReason,
)

public data class DynamicField(
    val path: AccessPath,
    val valueShape: ValueShape?,
    val reason: DynamicReason,
)

public enum class DynamicReason {
    KEY,
    INDEX,
    COMPUTED,
}

public data class AccessPath(
    val segments: List<AccessSegment>,
)

public sealed interface AccessSegment {
    public data class Field(val name: String) : AccessSegment
    public data class Index(val index: String) : AccessSegment
    public data object Dynamic : AccessSegment
}

public sealed interface ValueShape {
    public data object Unknown : ValueShape
    public data object Null : ValueShape
    public data object BooleanShape : ValueShape
    public data object NumberShape : ValueShape
    public data object TextShape : ValueShape
    public data class ArrayShape(val element: ValueShape) : ValueShape
    public data class ObjectShape(val schema: SchemaGuarantee, val closed: Boolean) : ValueShape
    public data class Union(val options: List<ValueShape>) : ValueShape
}
