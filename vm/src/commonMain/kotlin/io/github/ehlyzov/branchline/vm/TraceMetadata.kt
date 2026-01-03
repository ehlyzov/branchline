package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.ObjKey
import io.github.ehlyzov.branchline.ir.IRNode

/**
 * Metadata emitted by the compiler to let the VM broadcast tracing events
 * without affecting the hot execution path. All collections are keyed by the
 * bytecode program counter where the event should fire.
 */
data class TraceMetadata(
    val enterEvents: Map<Int, List<IRNode>> = emptyMap(),
    val exitEvents: Map<Int, List<IRNode>> = emptyMap(),
    val pathWrites: Map<Int, PathWriteMeta> = emptyMap(),
)

enum class PathWriteOp { SET, APPEND }

sealed class PathSegmentMeta {
    data class Static(val key: ObjKey) : PathSegmentMeta()
    data class Dynamic(val tempName: String) : PathSegmentMeta()
}

data class PathWriteMeta(
    val op: PathWriteOp,
    val root: String,
    val segments: List<PathSegmentMeta> = emptyList(),
    val displayPath: List<Any>? = null,
    val directValues: Boolean = false,
)
