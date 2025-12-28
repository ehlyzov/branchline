package v2.ir

import v2.FuncDecl
expect class TransformRegistry(
    funcs: Map<String, FuncDecl>,
    hostFns: Map<String, (List<Any?>) -> Any?>,
    transforms: Map<String, TransformDescriptor>,
) {
    fun get(name: String): (Map<String, Any?>) -> Any?
    fun getOrNull(name: String): ((Map<String, Any?>) -> Any?)?
    fun exists(name: String): Boolean
    fun descriptor(name: String): TransformDescriptor?
}
