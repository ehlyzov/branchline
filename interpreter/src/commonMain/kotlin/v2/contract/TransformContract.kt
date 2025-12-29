package v2.contract

import kotlinx.serialization.Serializable
import v2.Token

@Serializable
public enum class ContractSource {
    EXPLICIT,
    INFERRED,
}

@Serializable
public data class TransformContract(
    val input: SchemaRequirement,
    val output: SchemaGuarantee,
    val source: ContractSource,
)

@Serializable
public data class SchemaRequirement(
    val fields: LinkedHashMap<String, FieldConstraint>,
    val open: Boolean,
    val dynamicAccess: List<DynamicAccess>,
)

@Serializable
public data class SchemaGuarantee(
    val fields: LinkedHashMap<String, FieldShape>,
    val mayEmitNull: Boolean,
    val dynamicFields: List<DynamicField>,
)

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
public enum class OriginKind {
    SET,
    MODIFY,
    APPEND,
    OUTPUT,
    MERGED,
}

@Serializable
public data class DynamicAccess(
    val path: AccessPath,
    val valueShape: ValueShape?,
    val reason: DynamicReason,
)

@Serializable
public data class DynamicField(
    val path: AccessPath,
    val valueShape: ValueShape?,
    val reason: DynamicReason,
)

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
    public data object Unknown : ValueShape

    @Serializable
    public data object Null : ValueShape

    @Serializable
    public data object BooleanShape : ValueShape

    @Serializable
    public data object NumberShape : ValueShape

    @Serializable
    public data object TextShape : ValueShape

    @Serializable
    public data class ArrayShape(val element: ValueShape) : ValueShape

    @Serializable
    public data class ObjectShape(val schema: SchemaGuarantee, val closed: Boolean) : ValueShape

    @Serializable
    public data class Union(val options: List<ValueShape>) : ValueShape
}
