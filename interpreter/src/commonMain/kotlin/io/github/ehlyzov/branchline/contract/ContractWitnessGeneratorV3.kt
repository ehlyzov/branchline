package io.github.ehlyzov.branchline.contract

public data class ContractWitnessV3(
    val input: Any?,
    val output: Any?,
)

public object ContractWitnessGeneratorV3 {
    public fun generateInput(requirement: RequirementSchemaV3): Any? {
        val witness = buildValue(requirement.root)
        val rootMap = witness as? MutableMap<String, Any?> ?: linkedMapOf<String, Any?>()
        applyObligations(rootMap, requirement.obligations, requirement.root)
        return rootMap
    }

    public fun generateOutput(guarantee: GuaranteeSchemaV3): Any? {
        val witness = buildValue(guarantee.root)
        val rootMap = witness as? MutableMap<String, Any?> ?: linkedMapOf<String, Any?>()
        applyObligations(rootMap, guarantee.obligations, guarantee.root)
        return rootMap
    }

    public fun generate(contract: TransformContractV3): ContractWitnessV3 = ContractWitnessV3(
        input = generateInput(contract.input),
        output = generateOutput(contract.output),
    )

    private fun buildValue(node: NodeV3): Any? = when (node.kind) {
        NodeKindV3.NEVER -> null
        NodeKindV3.ANY -> linkedMapOf<String, Any?>()
        NodeKindV3.NULL -> null
        NodeKindV3.BOOLEAN -> false
        NodeKindV3.NUMBER -> 0
        NodeKindV3.BYTES -> byteArrayOf()
        NodeKindV3.TEXT -> node.domains.firstOrNull()?.let(::valueFromDomain) ?: "text"
        NodeKindV3.OBJECT -> {
            val value = linkedMapOf<String, Any?>()
            node.children.forEach { (name, child) ->
                if (child.required) {
                    value[name] = buildValue(child)
                }
            }
            value
        }
        NodeKindV3.ARRAY -> {
            val element = node.element?.let(::buildValue)
            if (element == null) mutableListOf<Any?>() else mutableListOf(element)
        }
        NodeKindV3.SET -> {
            val element = node.element?.let(::buildValue)
            if (element == null) linkedSetOf<Any?>() else linkedSetOf(element)
        }
        NodeKindV3.UNION -> {
            val first = node.options.firstOrNull() ?: return null
            buildValue(first)
        }
    }

    private fun applyObligations(
        root: MutableMap<String, Any?>,
        obligations: List<ContractObligationV3>,
        rootNode: NodeV3,
    ) {
        for (obligation in obligations) {
            applyExpr(root, obligation.expr, rootNode)
        }
    }

    private fun applyExpr(root: MutableMap<String, Any?>, expr: ConstraintExprV3, rootNode: NodeV3) {
        when (expr) {
            is ConstraintExprV3.PathPresent -> ensurePath(root, expr.path, nonNull = false, rootNode = rootNode)
            is ConstraintExprV3.PathNonNull -> ensurePath(root, expr.path, nonNull = true, rootNode = rootNode)
            is ConstraintExprV3.OneOf -> expr.children.firstOrNull()?.let { child ->
                applyExpr(root, child, rootNode)
            }
            is ConstraintExprV3.AllOf -> expr.children.forEach { child -> applyExpr(root, child, rootNode) }
            is ConstraintExprV3.Exists -> ensureExists(root, expr.path, expr.minCount, rootNode)
            is ConstraintExprV3.ValueDomain -> setPathValue(root, expr.path, valueFromDomain(expr.domain))
            is ConstraintExprV3.ForAll -> ensureForAll(root, expr, rootNode)
        }
    }

    private fun ensurePath(
        root: MutableMap<String, Any?>,
        path: AccessPath,
        nonNull: Boolean,
        rootNode: NodeV3,
    ) {
        if (path.segments.isEmpty()) return
        var cursor: Any? = root
        path.segments.forEachIndexed { index, segment ->
            when (segment) {
                is AccessSegment.Field -> {
                    val map = cursor as? MutableMap<String, Any?> ?: return
                    val isLeaf = index == path.segments.lastIndex
                    if (!map.containsKey(segment.name)) {
                        map[segment.name] = if (isLeaf && nonNull) {
                            defaultNonNullValue(path, rootNode)
                        } else {
                            linkedMapOf<String, Any?>()
                        }
                    }
                    cursor = map[segment.name]
                    if (isLeaf && nonNull && cursor == null) {
                        map[segment.name] = defaultNonNullValue(path, rootNode)
                    }
                }
                is AccessSegment.Index -> {
                    val list = when (cursor) {
                        is MutableList<*> -> cursor as MutableList<Any?>
                        else -> mutableListOf<Any?>().also { created ->
                            when (val container = cursor) {
                                is MutableMap<*, *> -> {
                                    val map = container as MutableMap<String, Any?>
                                    map[segment.index] = created
                                }
                                else -> Unit
                            }
                        }
                    }
                    val idx = segment.index.toIntOrNull() ?: 0
                    while (list.size <= idx) list += null
                    if (list[idx] == null) {
                        list[idx] = if (index == path.segments.lastIndex && nonNull) {
                            defaultNonNullValue(path, rootNode)
                        } else {
                            linkedMapOf<String, Any?>()
                        }
                    }
                    cursor = list[idx]
                }
                AccessSegment.Dynamic -> return
            }
        }
    }

    private fun ensureExists(root: MutableMap<String, Any?>, path: AccessPath, minCount: Int, rootNode: NodeV3) {
        if (path.segments.isEmpty()) return
        val targetNode = resolveNode(rootNode, path)
        val elementNode = when (targetNode?.kind) {
            NodeKindV3.ARRAY, NodeKindV3.SET -> targetNode.element
            else -> null
        }
        setPathValue(
            root,
            path,
            MutableList(minCount.coerceAtLeast(1)) { elementNode?.let(::buildValue) ?: linkedMapOf<String, Any?>() },
        )
    }

    private fun ensureForAll(root: MutableMap<String, Any?>, expr: ConstraintExprV3.ForAll, rootNode: NodeV3) {
        val existing = resolvePath(root, expr.path)
        val list = when (existing) {
            is MutableList<*> -> existing as MutableList<Any?>
            is List<*> -> existing.toMutableList()
            else -> mutableListOf<Any?>()
        }
        val targetNode = resolveNode(rootNode, expr.path)
        val elementNode = when (targetNode?.kind) {
            NodeKindV3.ARRAY, NodeKindV3.SET -> targetNode.element
            else -> null
        }
        if (list.isEmpty()) {
            list += (elementNode?.let(::buildValue) ?: linkedMapOf<String, Any?>())
        }
        for (index in list.indices) {
            val item = (list[index] as? MutableMap<String, Any?>) ?: linkedMapOf<String, Any?>()
            for (field in expr.requiredFields) {
                if (!item.containsKey(field) || item[field] == null) {
                    val fieldNode = elementNode?.children?.get(field)
                    item[field] = fieldNode?.let(::buildValue) ?: "value"
                }
            }
            expr.fieldDomains.forEach { (field, domain) ->
                item[field] = valueFromDomain(domain)
            }
            for (group in expr.requireAnyOf) {
                val first = group.firstOrNull() ?: continue
                if (group.none { name -> item[name] != null }) {
                    item[first] = "value"
                }
            }
            list[index] = item
        }
        setPathValue(root, expr.path, list)
    }

    private fun defaultNonNullValue(path: AccessPath, rootNode: NodeV3): Any {
        val targetNode = resolveNode(rootNode, path)
        val targetValue = targetNode?.let(::buildValue)
        return targetValue ?: "value"
    }

    private fun resolveNode(rootNode: NodeV3, path: AccessPath): NodeV3? {
        var current: NodeV3? = rootNode
        for (segment in path.segments) {
            current = when (segment) {
                is AccessSegment.Field -> current?.children?.get(segment.name)
                is AccessSegment.Index -> {
                    when (current?.kind) {
                        NodeKindV3.ARRAY, NodeKindV3.SET -> current.element
                        NodeKindV3.OBJECT -> current.children[segment.index]
                        else -> null
                    }
                }
                AccessSegment.Dynamic -> null
            }
            if (current == null) return null
        }
        return current
    }

    private fun resolvePath(root: MutableMap<String, Any?>, path: AccessPath): Any? {
        var current: Any? = root
        for (segment in path.segments) {
            current = when (segment) {
                is AccessSegment.Field -> (current as? Map<*, *>)?.get(segment.name)
                is AccessSegment.Index -> {
                    val idx = segment.index.toIntOrNull()
                    when (current) {
                        is List<*> -> if (idx == null) null else current.getOrNull(idx)
                        is Map<*, *> -> current[segment.index]
                        else -> null
                    }
                }
                AccessSegment.Dynamic -> return null
            }
            if (current == null) return null
        }
        return current
    }

    private fun setPathValue(root: MutableMap<String, Any?>, path: AccessPath, value: Any?) {
        if (path.segments.isEmpty()) return
        var cursor: MutableMap<String, Any?> = root
        path.segments.forEachIndexed { index, segment ->
            when (segment) {
                is AccessSegment.Field -> {
                    if (index == path.segments.lastIndex) {
                        cursor[segment.name] = value
                        return
                    }
                    val next = cursor[segment.name] as? MutableMap<String, Any?>
                    if (next != null) {
                        cursor = next
                    } else {
                        val created = linkedMapOf<String, Any?>()
                        cursor[segment.name] = created
                        cursor = created
                    }
                }
                is AccessSegment.Index -> {
                    if (index == path.segments.lastIndex) {
                        cursor[segment.index] = value
                        return
                    }
                    val next = cursor[segment.index] as? MutableMap<String, Any?>
                    if (next != null) {
                        cursor = next
                    } else {
                        val created = linkedMapOf<String, Any?>()
                        cursor[segment.index] = created
                        cursor = created
                    }
                }
                AccessSegment.Dynamic -> return
            }
        }
    }

    private fun valueFromDomain(domain: ValueDomainV3): Any = when (domain) {
        is ValueDomainV3.EnumText -> domain.values.firstOrNull() ?: ""
        is ValueDomainV3.NumberRange -> {
            val min = domain.min ?: 0.0
            if (domain.integerOnly) min.toInt() else min
        }
        is ValueDomainV3.Regex -> {
            if (domain.pattern.contains("[A-Za-z]")) "sample" else "value"
        }
    }
}
