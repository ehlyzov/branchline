package io.github.ehlyzov.branchline.ir

import io.github.ehlyzov.branchline.FuncDecl
import io.github.ehlyzov.branchline.std.HostFnMetadata
expect class TransformRegistry(
    funcs: Map<String, FuncDecl>,
    hostFns: Map<String, (List<Any?>) -> Any?>,
    hostFnMeta: Map<String, HostFnMetadata>,
    transforms: Map<String, TransformDescriptor>,
) {
    fun get(name: String): (Map<String, Any?>) -> Any?
    fun getOrNull(name: String): ((Map<String, Any?>) -> Any?)?
    fun exists(name: String): Boolean
    fun descriptor(name: String): TransformDescriptor?
}
