package io.github.ehlyzov.branchline

import io.github.ehlyzov.branchline.contract.ContractJsonRenderer
import io.github.ehlyzov.branchline.contract.ContractWitnessGenerator
import io.github.ehlyzov.branchline.contract.TransformContract
import io.github.ehlyzov.branchline.contract.TransformContractBuilder
import io.github.ehlyzov.branchline.json.JsonNumberMode
import io.github.ehlyzov.branchline.json.toJsonElement
import io.github.ehlyzov.branchline.normalize.AstRenderer
import io.github.ehlyzov.branchline.normalize.UnsupportedNodeException
import io.github.ehlyzov.branchline.sema.SemanticAnalyzer
import io.github.ehlyzov.branchline.sema.SemanticException
import io.github.ehlyzov.branchline.sema.SemanticWarning
import io.github.ehlyzov.branchline.sema.TypeResolver
import io.github.ehlyzov.branchline.std.StdLib
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
public data class BranchlineInspectRequest(
    val programText: String,
    val transformName: String? = null,
    val includeDebugMetadata: Boolean = false,
    val includeWitness: Boolean = false,
    val includeNormalizedSource: Boolean = false,
)

@Serializable
public data class BranchlineFeatureUsageSummary(
    val features: List<String>,
)

@Serializable
public enum class BranchlineSubsetCompatibility {
    COMPATIBLE,
    INCOMPATIBLE,
    UNKNOWN,
}

public data class BranchlineInspectTransform(
    val name: String,
    val contractSource: String,
    val explicitContract: JsonObject?,
    val inferredContract: JsonObject,
    val mergedContract: JsonObject,
    val witness: JsonObject?,
)

public data class BranchlineInspectResult(
    val success: Boolean,
    val transforms: List<BranchlineInspectTransform>,
    val diagnostics: List<BranchlineDiagnostic>,
    val warnings: List<BranchlineDiagnostic>,
    val featureUsage: BranchlineFeatureUsageSummary,
    val subsetCompatibility: BranchlineSubsetCompatibility,
    val normalizedSource: String?,
) {
    public fun contractsJson(pretty: Boolean = true): String {
        val payload = if (transforms.size == 1) {
            contractJsonEntry(transforms.first())
        } else {
            buildJsonObject {
                put(
                    "transforms",
                    buildJsonArray {
                        transforms.forEach { add(contractJsonEntry(it)) }
                    },
                )
            }
        }
        val json = if (pretty) BRANCHLINE_FACADE_JSON_PRETTY else BRANCHLINE_FACADE_JSON_COMPACT
        return json.encodeToString(JsonElement.serializer(), payload)
    }

    public fun inspectJson(pretty: Boolean = true): String {
        val payload = inspectJsonPayload()
        val json = if (pretty) BRANCHLINE_FACADE_JSON_PRETTY else BRANCHLINE_FACADE_JSON_COMPACT
        return json.encodeToString(JsonElement.serializer(), payload)
    }

    public fun inspectJsonPayload(): JsonObject = buildJsonObject {
        put("success", success)
        put(
            "diagnostics",
            buildJsonArray {
                diagnostics.forEach { add(diagnosticPayload(it)) }
            },
        )
        put(
            "warnings",
            buildJsonArray {
                warnings.forEach { add(diagnosticPayload(it)) }
            },
        )
        put(
            "featureUsage",
            buildJsonObject {
                put(
                    "features",
                    buildJsonArray {
                        featureUsage.features.forEach { add(JsonPrimitive(it)) }
                    },
                )
            },
        )
        put("subsetCompatibility", subsetCompatibility.name)
        put("normalizedSource", normalizedSource?.let(::JsonPrimitive) ?: JsonNull)
        put(
            "transforms",
            buildJsonArray {
                transforms.forEach { add(inspectTransformPayload(it)) }
            },
        )

        // Compatibility fields for existing single-transform --contracts-json consumers.
        if (transforms.size == 1) {
            contractJsonEntry(transforms.first()).forEach { (key, value) ->
                put(key, value)
            }
        }
    }
}

public object BranchlineFacade {
    public fun inspect(request: BranchlineInspectRequest): BranchlineInspectResult {
        return try {
            val tokens = Lexer(request.programText).lex()
            val program = Parser(tokens, request.programText).parse()
            val hostFns = StdLib.fns
            val analyzer = SemanticAnalyzer(hostFns.keys)
            analyzer.analyze(program)
            val transforms = program.decls.filterIsInstance<TransformDecl>()
            if (transforms.isEmpty()) {
                return failedInspectResult(
                    diagnostic = BranchlineDiagnostic(
                        code = "no_transform_blocks",
                        message = "Program must declare at least one TRANSFORM block.",
                        severity = BranchlineDiagnosticSeverity.ERROR,
                        category = BranchlineDiagnosticCategory.SEMANTIC,
                        payload = BranchlineDiagnosticPayload(
                            hint = "Add a TRANSFORM block before inspecting contracts.",
                        ),
                    ),
                )
            }
            val selected = selectTransforms(transforms, request.transformName)
                ?: return failedInspectResult(
                    diagnostic = BranchlineDiagnostic(
                        code = "transform_not_found",
                        message = "Transform '${request.transformName}' not found",
                        severity = BranchlineDiagnosticSeverity.ERROR,
                        category = BranchlineDiagnosticCategory.SEMANTIC,
                        payload = BranchlineDiagnosticPayload(
                            operation = "select-transform",
                            targetPath = request.transformName,
                            expectedKind = "declared-transform",
                            hint = "Use one of the transform names declared in the program.",
                        ),
                    ),
                )
            val typeDecls = program.decls.filterIsInstance<TypeDecl>()
            val contractBuilder = TransformContractBuilder(TypeResolver(typeDecls), hostFns.keys)
            val inspectTransforms = selected.map { transform ->
                inspectTransform(
                    transform = transform,
                    contractBuilder = contractBuilder,
                    includeDebugMetadata = request.includeDebugMetadata,
                    includeWitness = request.includeWitness,
                )
            }
            val warningDiagnostics = analyzer.warnings.map(::warningDiagnostic)
            val subsetDiagnostics = analyzeAiSubset(program, selected)
            val compatibility = if (subsetDiagnostics.isEmpty()) {
                BranchlineSubsetCompatibility.COMPATIBLE
            } else {
                BranchlineSubsetCompatibility.INCOMPATIBLE
            }
            val normalizationDiagnostics = mutableListOf<BranchlineDiagnostic>()
            val normalizedSource = if (
                request.includeNormalizedSource &&
                compatibility == BranchlineSubsetCompatibility.COMPATIBLE
            ) {
                try {
                    AstRenderer.renderProgram(program)
                } catch (ex: UnsupportedNodeException) {
                    normalizationDiagnostics += BranchlineDiagnostic(
                        code = "normalization_unsupported_node",
                        message = "Cannot canonicalize node '${ex.nodeKind}': ${ex.detail}",
                        severity = BranchlineDiagnosticSeverity.WARNING,
                        category = BranchlineDiagnosticCategory.NORMALIZATION,
                        payload = BranchlineDiagnosticPayload(
                            operation = "normalize",
                            actualKind = ex.nodeKind,
                            hint = "Keep the original source or remove unsupported canonical-subset constructs.",
                        ),
                    )
                    null
                }
            } else {
                null
            }
            BranchlineInspectResult(
                success = true,
                transforms = inspectTransforms,
                diagnostics = subsetDiagnostics + warningDiagnostics + normalizationDiagnostics,
                warnings = warningDiagnostics + normalizationDiagnostics,
                featureUsage = BranchlineFeatureUsageSummary(
                    features = collectFeatureUsage(program, selected)
                ),
                subsetCompatibility = compatibility,
                normalizedSource = normalizedSource,
            )
        } catch (ex: ParseException) {
            failedInspectResult(parseDiagnostic(ex))
        } catch (ex: SemanticException) {
            failedInspectResult(semanticDiagnostic(ex))
        }
    }
}

private val BRANCHLINE_FACADE_JSON_PRETTY: Json = Json { prettyPrint = true }
private val BRANCHLINE_FACADE_JSON_COMPACT: Json = Json

private fun failedInspectResult(diagnostic: BranchlineDiagnostic): BranchlineInspectResult = BranchlineInspectResult(
    success = false,
    transforms = emptyList(),
    diagnostics = listOf(diagnostic),
    warnings = emptyList(),
    featureUsage = BranchlineFeatureUsageSummary(emptyList()),
    subsetCompatibility = BranchlineSubsetCompatibility.UNKNOWN,
    normalizedSource = null,
)

private fun inspectTransform(
    transform: TransformDecl,
    contractBuilder: TransformContractBuilder,
    includeDebugMetadata: Boolean,
    includeWitness: Boolean,
): BranchlineInspectTransform {
    val explicitContract = contractBuilder.buildExplicitContract(transform)
    val inferredContract = contractBuilder.buildInferredContract(transform)
    val mergedContract = contractBuilder.build(transform)
    return BranchlineInspectTransform(
        name = transform.name ?: "<anonymous>",
        contractSource = mergedContract.source.name.lowercase(),
        explicitContract = explicitContract?.let { contractPayload(it, includeDebugMetadata) },
        inferredContract = contractPayload(inferredContract, includeDebugMetadata),
        mergedContract = mergedContractPayload(
            transformName = transform.name ?: "<anonymous>",
            contract = mergedContract,
            includeDebugMetadata = includeDebugMetadata,
            includeWitness = includeWitness,
        ),
        witness = witnessPayload(mergedContract, includeWitness),
    )
}

private fun mergedContractPayload(
    transformName: String,
    contract: TransformContract,
    includeDebugMetadata: Boolean,
    includeWitness: Boolean,
): JsonObject = buildJsonObject {
    put("name", transformName)
    put("source", contract.source.name.lowercase())
    put("input", ContractJsonRenderer.inputElement(contract, includeDebugMetadata))
    put("output", ContractJsonRenderer.outputElement(contract, includeDebugMetadata))
    witnessPayload(contract, includeWitness)?.let { put("witness", it) }
}

private fun contractPayload(
    contract: TransformContract,
    includeDebugMetadata: Boolean,
): JsonObject = buildJsonObject {
    put("input", ContractJsonRenderer.inputElement(contract, includeDebugMetadata))
    put("output", ContractJsonRenderer.outputElement(contract, includeDebugMetadata))
}

private fun witnessPayload(
    contract: TransformContract,
    includeWitness: Boolean,
): JsonObject? {
    if (!includeWitness) return null
    val witness = ContractWitnessGenerator.generate(contract)
    return buildJsonObject {
        put("input", toJsonElement(witness.input, JsonNumberMode.SAFE))
        put("output", toJsonElement(witness.output, JsonNumberMode.SAFE))
    }
}

private fun contractJsonEntry(transform: BranchlineInspectTransform): JsonObject = transform.mergedContract

private fun inspectTransformPayload(transform: BranchlineInspectTransform): JsonObject = buildJsonObject {
    put("name", transform.name)
    put("contractSource", transform.contractSource)
    put("explicitContract", transform.explicitContract ?: JsonNull)
    put("inferredContract", transform.inferredContract)
    put("mergedContract", transform.mergedContract)
    put("witness", transform.witness ?: JsonNull)
}

private fun diagnosticPayload(diagnostic: BranchlineDiagnostic): JsonObject = buildJsonObject {
    put("code", diagnostic.code)
    put("message", diagnostic.message)
    put("severity", diagnostic.severity.name)
    put("category", diagnostic.category.id)
    val span = diagnostic.span
    if (span == null) {
        put("span", JsonNull)
    } else {
        put(
            "span",
            buildJsonObject {
                put("startLine", span.startLine)
                put("startColumn", span.startColumn)
                put("endLine", span.endLine)
                put("endColumn", span.endColumn)
            },
        )
    }
    val payload = diagnostic.payload
    if (payload == null) {
        put("payload", JsonNull)
    } else {
        put(
            "payload",
            buildJsonObject {
                payload.operation?.let { put("operation", it) }
                payload.targetPath?.let { put("targetPath", it) }
                payload.expectedKind?.let { put("expectedKind", it) }
                payload.actualKind?.let { put("actualKind", it) }
                payload.expected?.let { put("expected", it) }
                payload.actual?.let { put("actual", it) }
                payload.hint?.let { put("hint", it) }
            },
        )
    }
}

private fun selectTransforms(
    transforms: List<TransformDecl>,
    name: String?,
): List<TransformDecl>? {
    if (name == null) return transforms
    val matches = transforms.filter { it.name == name }
    return matches.ifEmpty { null }
}

private fun parseDiagnostic(ex: ParseException): BranchlineDiagnostic = BranchlineDiagnostic(
    code = "parse_error",
    message = ex.message ?: "Parse error",
    severity = BranchlineDiagnosticSeverity.ERROR,
    span = tokenSpan(ex.token),
    category = BranchlineDiagnosticCategory.SYNTAX,
    payload = BranchlineDiagnosticPayload(
        operation = "parse",
        actualKind = ex.token.type.name,
        hint = "Fix the syntax near the reported span, then rerun inspect.",
    ),
)

private fun semanticDiagnostic(ex: SemanticException): BranchlineDiagnostic = BranchlineDiagnostic(
    code = "semantic_error",
    message = ex.message ?: "Semantic error",
    severity = BranchlineDiagnosticSeverity.ERROR,
    span = tokenSpan(ex.token),
    category = BranchlineDiagnosticCategory.SEMANTIC,
    payload = BranchlineDiagnosticPayload(
        operation = "analyze",
        actualKind = ex.token.type.name,
        hint = "Fix the semantic issue at the reported span, then rerun inspect.",
    ),
)

private fun warningDiagnostic(warning: SemanticWarning): BranchlineDiagnostic = BranchlineDiagnostic(
    code = "semantic_warning",
    message = warning.message,
    severity = BranchlineDiagnosticSeverity.WARNING,
    span = tokenSpan(warning.token),
    category = BranchlineDiagnosticCategory.SEMANTIC,
    payload = BranchlineDiagnosticPayload(
        operation = "analyze",
        hint = "Review the warning and prefer canonical syntax when possible.",
    ),
)

private fun tokenSpan(token: Token): BranchlineSourceSpan {
    val width = token.lexeme.length.coerceAtLeast(1)
    return BranchlineSourceSpan(
        startLine = token.line,
        startColumn = token.column,
        endLine = token.line,
        endColumn = token.column + width - 1,
    )
}

private fun collectFeatureUsage(
    program: Program,
    transforms: List<TransformDecl>,
): List<String> {
    val features = linkedSetOf<String>()
    if (program.decls.any { it is FuncDecl }) features += "func-decl"
    if (program.decls.any { it is TypeDecl }) features += "type-decl"
    transforms.forEach { transform ->
        if (transform.signature != null) features += "transform-signature"
        if (transform.options.shared.isNotEmpty()) features += "shared-option"
        collectBlockFeatures(transform.body, features)
    }
    return features.toList()
}

private fun analyzeAiSubset(
    program: Program,
    transforms: List<TransformDecl>,
): List<BranchlineDiagnostic> {
    return AiSubsetAnalyzer(program).analyze(transforms)
}

/** Mirrors host-first direct-call dispatch; named functions are followed once, including cycles. */
private class AiSubsetAnalyzer(program: Program) {
    private val functions = program.decls.filterIsInstance<FuncDecl>().associateBy { it.name }
    private val sharedDeclarations = program.decls.filterIsInstance<SharedDecl>()
    private val visitedFunctions = mutableSetOf<String>()
    private var bindings: Set<String> = emptySet()
    private var reassignedNames = mutableSetOf<String>()
    private val writesByBody = mutableMapOf<Ast, Set<String>>()
    private var collectingWrites = false

    fun analyze(transforms: List<TransformDecl>): List<BranchlineDiagnostic> {
        val diagnostics = mutableListOf<BranchlineDiagnostic>()
        // A mutable local can stop being callable, causing direct calls to fall back to FUNC.
        // Discover writes first; uncertain mutable shadows are checked conservatively.
        collectingWrites = true
        functions.values.forEach {
            reassignedNames = mutableSetOf()
            collectSubsetFuncBodyDiagnostics(it.body, diagnostics)
            writesByBody[it.body] = reassignedNames.toSet()
        }
        transforms.forEach {
            reassignedNames = mutableSetOf()
            collectSubsetStmtDiagnostics(it.body, diagnostics)
            writesByBody[it.body] = reassignedNames.toSet()
        }
        collectingWrites = false
        diagnostics.clear()
        visitedFunctions.clear()
        bindings = emptySet()
        sharedDeclarations.forEach { shared ->
            diagnostics += unsupportedSubsetDiagnostic(
                token = shared.token,
                feature = "SHARED",
                details = "SHARED resources depend on external runtime state and are outside the AI canonical subset MVP.",
            )
        }
        transforms.forEach { transform ->
            if (transform.body is GraphBody) {
                diagnostics += unsupportedSubsetDiagnostic(
                    token = transform.token,
                    feature = "GRAPH",
                    details = "Graph/orchestration transforms are outside the AI canonical subset MVP.",
                )
            }
            transform.options.shared.forEach { shared ->
                diagnostics += unsupportedSubsetDiagnostic(
                    token = shared.token,
                    feature = "SHARED",
                    details = "Transform-level SHARED declarations are outside the AI canonical subset MVP.",
                )
            }
            reassignedNames = writesByBody.getValue(transform.body).toMutableSet()
            withBindings(emptyList(), transform.params) { collectSubsetStmtDiagnostics(transform.body, diagnostics) }
        }
        return diagnostics
    }

    private fun collectSubsetStmtDiagnostics(
        body: TransformBody,
        diagnostics: MutableList<BranchlineDiagnostic>,
    ) {
        withBindings(emptyList()) {
            body.statements.forEach { stmt ->
                when (stmt) {
                    is SharedWriteStmt -> diagnostics += unsupportedSubsetDiagnostic(
                        token = stmt.token,
                        feature = "SHARED_WRITE",
                        details = "Shared-state writes are outside the AI canonical subset MVP.",
                    )
                    is NodeDecl -> diagnostics += unsupportedSubsetDiagnostic(
                        token = stmt.token,
                        feature = "GRAPH_NODE",
                        details = "Graph nodes are outside the AI canonical subset MVP.",
                    )
                    is Connection -> diagnostics += unsupportedSubsetDiagnostic(
                        token = stmt.token,
                        feature = "GRAPH_CONNECTION",
                        details = "Graph connections are outside the AI canonical subset MVP.",
                    )
                    is GraphOutput -> diagnostics += unsupportedSubsetDiagnostic(
                        token = stmt.token,
                        feature = "GRAPH_OUTPUT",
                        details = "Graph outputs are outside the AI canonical subset MVP.",
                    )
                    is CodeBlock -> collectSubsetStmtDiagnostics(stmt, diagnostics)
                    is IfStmt -> {
                        collectSubsetExprDiagnostics(stmt.condition, diagnostics)
                        collectSubsetStmtDiagnostics(stmt.thenBlock, diagnostics)
                        stmt.elseBlock?.let { collectSubsetStmtDiagnostics(it, diagnostics) }
                    }
                    is ForEachStmt -> {
                        collectSubsetExprDiagnostics(stmt.iterable, diagnostics)
                        withBindings(emptyList(), listOf(stmt.varName)) {
                            stmt.where?.let { collectSubsetExprDiagnostics(it, diagnostics) }
                            collectSubsetStmtDiagnostics(stmt.body, diagnostics)
                        }
                    }
                    is TryCatchStmt -> {
                        collectSubsetExprDiagnostics(stmt.tryExpr, diagnostics)
                        withBindings(emptyList(), listOf(stmt.exceptionName)) {
                            stmt.fallbackExpr?.let { collectSubsetExprDiagnostics(it, diagnostics) }
                            stmt.fallbackAbort?.let { collectSubsetStmtDiagnostics(CodeBlock(listOf(it), it.token), diagnostics) }
                        }
                    }
                    is LetStmt -> {
                        collectSubsetExprDiagnostics(stmt.expr, diagnostics)
                        bindings = if (stmt.name !in reassignedNames && isKnownCallable(stmt.expr)) {
                            bindings + stmt.name
                        } else bindings - stmt.name
                    }
                    is SetStmt -> {
                        collectSubsetExprDiagnostics(stmt.target, diagnostics)
                        collectSubsetExprDiagnostics(stmt.value, diagnostics)
                    }
                    is SetVarStmt -> {
                        reassignedNames += stmt.name
                        collectSubsetExprDiagnostics(stmt.value, diagnostics)
                        bindings = bindings - stmt.name
                    }
                    is PlusAssignStmt -> {
                        collectSubsetExprDiagnostics(stmt.target, diagnostics)
                        collectSubsetExprDiagnostics(stmt.value, diagnostics)
                    }
                    is PlusAssignVarStmt -> {
                        reassignedNames += stmt.name
                        collectSubsetExprDiagnostics(stmt.value, diagnostics)
                    }
                    is ModifyStmt -> {
                        collectSubsetExprDiagnostics(stmt.target, diagnostics)
                        stmt.updates.forEach { collectSubsetPropertyDiagnostics(it, diagnostics) }
                    }
                    is OutputStmt -> collectSubsetExprDiagnostics(stmt.template, diagnostics)
                    is ReturnStmt -> stmt.value?.let { collectSubsetExprDiagnostics(it, diagnostics) }
                    is AbortStmt -> stmt.value?.let { collectSubsetExprDiagnostics(it, diagnostics) }
                    is ExprStmt -> collectSubsetExprDiagnostics(stmt.expr, diagnostics)
                }
            }
        }
    }

    private fun collectSubsetPropertyDiagnostics(
        property: Property,
        diagnostics: MutableList<BranchlineDiagnostic>,
    ) {
        when (property) {
            is LiteralProperty -> collectSubsetExprDiagnostics(property.value, diagnostics)
            is ComputedProperty -> {
                collectSubsetExprDiagnostics(property.keyExpr, diagnostics)
                collectSubsetExprDiagnostics(property.value, diagnostics)
            }
        }
    }

    private fun collectSubsetExprDiagnostics(
        expr: Expr,
        diagnostics: MutableList<BranchlineDiagnostic>,
    ) {
        when (expr) {
            is IdentifierExpr -> Unit
            is StringExpr -> Unit
            is NumberLiteral -> Unit
            is NullLiteral -> Unit
            is BoolExpr -> Unit
            is AccessExpr -> {
                collectSubsetExprDiagnostics(expr.base, diagnostics)
                expr.segs.filterIsInstance<AccessSeg.Dynamic>().forEach {
                    collectSubsetExprDiagnostics(it.keyExpr, diagnostics)
                }
            }
            is ObjectExpr -> expr.fields.forEach { collectSubsetPropertyDiagnostics(it, diagnostics) }
            is CallExpr -> {
                // Exec resolves registered host functions before locals or FUNC declarations.
                val name = expr.callee.name
                if (name == "NOW" || name == "RANDOM") {
                    diagnostics += unsupportedSubsetDiagnostic(
                        token = expr.callee.token,
                        feature = name,
                        details = "Ambient clock/randomness is non-deterministic; supply the value through input.",
                    )
                } else if (!collectingWrites && name !in bindings && name !in StdLib.fns) {
                    functions[name]?.let { function ->
                        val functionWrites = writesByBody.getValue(function.body)
                        val callableParams = function.params.filterIndexed { index, parameter ->
                            parameter !in functionWrites && expr.args.getOrNull(index)?.let(::isKnownCallable) == true
                        }.toSet()
                        val visitKey = name + ":" + function.params.map { it in callableParams }.joinToString()
                        if (visitedFunctions.add(visitKey)) {
                            val callerBindings = bindings
                            val callerWrites = reassignedNames
                            bindings = callableParams
                            reassignedNames = functionWrites.toMutableSet()
                            try {
                                collectSubsetFuncBodyDiagnostics(function.body, diagnostics)
                            } finally {
                                bindings = callerBindings
                                reassignedNames = callerWrites
                            }
                        }
                    }
                }
                expr.args.forEach { collectSubsetExprDiagnostics(it, diagnostics) }
            }
            is InvokeExpr -> {
                collectSubsetExprDiagnostics(expr.target, diagnostics)
                expr.args.forEach { collectSubsetExprDiagnostics(it, diagnostics) }
            }
            is BinaryExpr -> {
                collectSubsetExprDiagnostics(expr.left, diagnostics)
                collectSubsetExprDiagnostics(expr.right, diagnostics)
            }
            is UnaryExpr -> {
                if (expr.token.type == TokenType.SUSPEND) {
                    diagnostics += unsupportedSubsetDiagnostic(
                        token = expr.token,
                        feature = "SUSPEND",
                        details = "Suspension semantics are outside the AI canonical subset MVP.",
                    )
                }
                collectSubsetExprDiagnostics(expr.expr, diagnostics)
            }
            is LambdaExpr -> withBindings(emptyList(), expr.params) {
                collectSubsetFuncBodyDiagnostics(expr.body, diagnostics)
            }
            is ArrayExpr -> expr.elements.forEach { collectSubsetExprDiagnostics(it, diagnostics) }
            is ArrayCompExpr -> {
                collectSubsetExprDiagnostics(expr.iterable, diagnostics)
                withBindings(emptyList(), listOf(expr.varName)) {
                    collectSubsetExprDiagnostics(expr.mapExpr, diagnostics)
                    expr.where?.let { collectSubsetExprDiagnostics(it, diagnostics) }
                }
            }
            is IfElseExpr -> {
                collectSubsetExprDiagnostics(expr.condition, diagnostics)
                collectSubsetExprDiagnostics(expr.thenBranch, diagnostics)
                collectSubsetExprDiagnostics(expr.elseBranch, diagnostics)
            }
            is TryCatchExpr -> {
                collectSubsetExprDiagnostics(expr.tryExpr, diagnostics)
                withBindings(emptyList(), listOf(expr.exceptionName)) {
                    collectSubsetExprDiagnostics(expr.fallbackExpr, diagnostics)
                }
            }
            is CaseExpr -> {
                expr.whens.forEach { branch ->
                    collectSubsetExprDiagnostics(branch.condition, diagnostics)
                    collectSubsetExprDiagnostics(branch.result, diagnostics)
                }
                collectSubsetExprDiagnostics(expr.elseBranch, diagnostics)
            }
            is SharedStateAwaitExpr -> diagnostics += unsupportedSubsetDiagnostic(
                token = expr.token,
                feature = "AWAIT",
                details = "AWAIT on shared state is outside the AI canonical subset MVP.",
            )
        }
    }

    private fun collectSubsetFuncBodyDiagnostics(
        body: FuncBody,
        diagnostics: MutableList<BranchlineDiagnostic>,
    ) {
        when (body) {
            is ExprBody -> collectSubsetExprDiagnostics(body.expr, diagnostics)
            is BlockBody -> collectSubsetStmtDiagnostics(body.block, diagnostics)
        }
    }

    private fun isKnownCallable(expr: Expr): Boolean =
        expr is LambdaExpr || (expr is IdentifierExpr && expr.name in bindings && expr.name !in reassignedNames)

    private inline fun <T> withBindings(
        names: List<String>,
        hiddenNames: List<String> = emptyList(),
        action: () -> T,
    ): T {
        val previous = bindings
        bindings = (bindings - hiddenNames.toSet()) + names
        return try { action() } finally { bindings = previous }
    }
}

private fun unsupportedSubsetDiagnostic(
    token: Token,
    feature: String,
    details: String,
): BranchlineDiagnostic = BranchlineDiagnostic(
    code = "unsupported_in_ai_subset",
    message = "$feature is unsupported in the AI canonical subset MVP. $details",
    severity = BranchlineDiagnosticSeverity.ERROR,
    span = tokenSpan(token),
    category = BranchlineDiagnosticCategory.UNSUPPORTED_SUBSET,
    payload = BranchlineDiagnosticPayload(
        operation = "ai-subset-check",
        actualKind = feature,
        expectedKind = "ai-canonical-subset",
        hint = "Remove this construct or gate the program outside the AI canonical subset.",
    ),
)

private fun collectBlockFeatures(
    body: TransformBody,
    features: MutableSet<String>,
) {
    body.statements.forEach { stmt -> collectStmtFeatures(stmt, features) }
}

private fun collectStmtFeatures(
    stmt: Stmt,
    features: MutableSet<String>,
) {
    when (stmt) {
        is LetStmt -> {
            features += "let"
            collectExprFeatures(stmt.expr, features)
        }
        is SetStmt -> {
            features += "set"
            collectExprFeatures(stmt.target, features)
            collectExprFeatures(stmt.value, features)
        }
        is SetVarStmt -> {
            features += "set-var"
            collectExprFeatures(stmt.value, features)
        }
        is PlusAssignStmt -> {
            features += "plus-assign"
            collectExprFeatures(stmt.target, features)
            collectExprFeatures(stmt.value, features)
        }
        is PlusAssignVarStmt -> {
            features += "plus-assign-var"
            collectExprFeatures(stmt.value, features)
        }
        is SharedWriteStmt -> {
            features += "shared-write"
            stmt.key?.let { collectExprFeatures(it, features) }
            collectExprFeatures(stmt.value, features)
        }
        is ModifyStmt -> {
            features += "modify"
            collectExprFeatures(stmt.target, features)
            stmt.updates.forEach { collectPropertyFeatures(it, features) }
        }
        is OutputStmt -> {
            features += "output"
            collectExprFeatures(stmt.template, features)
        }
        is IfStmt -> {
            features += "if"
            collectExprFeatures(stmt.condition, features)
            collectBlockFeatures(stmt.thenBlock, features)
            stmt.elseBlock?.let { collectBlockFeatures(it, features) }
        }
        is ForEachStmt -> {
            features += "foreach"
            collectExprFeatures(stmt.iterable, features)
            stmt.where?.let { collectExprFeatures(it, features) }
            collectBlockFeatures(stmt.body, features)
        }
        is TryCatchStmt -> {
            features += "try-catch"
            collectExprFeatures(stmt.tryExpr, features)
            stmt.fallbackExpr?.let { collectExprFeatures(it, features) }
            stmt.fallbackAbort?.let { collectStmtFeatures(it, features) }
        }
        is CodeBlock -> {
            features += "block"
            collectBlockFeatures(stmt, features)
        }
        is ReturnStmt -> {
            features += "return"
            stmt.value?.let { collectExprFeatures(it, features) }
        }
        is AbortStmt -> {
            features += "abort"
            stmt.value?.let { collectExprFeatures(it, features) }
        }
        is ExprStmt -> collectExprFeatures(stmt.expr, features)
        is NodeDecl -> {
            features += "graph-node"
            collectExprFeatures(stmt.target, features)
        }
        is Connection -> features += "graph-connection"
        is GraphOutput -> features += "graph-output"
    }
}

private fun collectPropertyFeatures(
    property: Property,
    features: MutableSet<String>,
) {
    when (property) {
        is LiteralProperty -> collectExprFeatures(property.value, features)
        is ComputedProperty -> {
            features += "computed-property"
            collectExprFeatures(property.keyExpr, features)
            collectExprFeatures(property.value, features)
        }
    }
}

private fun collectExprFeatures(
    expr: Expr,
    features: MutableSet<String>,
) {
    when (expr) {
        is IdentifierExpr -> Unit
        is StringExpr -> Unit
        is AccessExpr -> {
            features += "access"
            collectExprFeatures(expr.base, features)
            expr.segs.forEach { seg ->
                if (seg is AccessSeg.Dynamic) {
                    features += "dynamic-access"
                    collectExprFeatures(seg.keyExpr, features)
                }
            }
        }
        is NumberLiteral -> Unit
        is NullLiteral -> Unit
        is ObjectExpr -> {
            features += "object"
            expr.fields.forEach { collectPropertyFeatures(it, features) }
        }
        is CallExpr -> {
            features += "call"
            expr.args.forEach { collectExprFeatures(it, features) }
        }
        is InvokeExpr -> {
            features += "invoke"
            collectExprFeatures(expr.target, features)
            expr.args.forEach { collectExprFeatures(it, features) }
        }
        is ArrayExpr -> {
            features += "array"
            expr.elements.forEach { collectExprFeatures(it, features) }
        }
        is ArrayCompExpr -> {
            features += "array-comprehension"
            collectExprFeatures(expr.iterable, features)
            expr.where?.let { collectExprFeatures(it, features) }
            collectExprFeatures(expr.mapExpr, features)
        }
        is BinaryExpr -> {
            features += "binary"
            collectExprFeatures(expr.left, features)
            collectExprFeatures(expr.right, features)
        }
        is UnaryExpr -> {
            features += "unary"
            collectExprFeatures(expr.expr, features)
        }
        is BoolExpr -> Unit
        is LambdaExpr -> {
            features += "lambda"
            collectFuncBodyFeatures(expr.body, features)
        }
        is IfElseExpr -> {
            features += "if-else-expr"
            collectExprFeatures(expr.condition, features)
            collectExprFeatures(expr.thenBranch, features)
            collectExprFeatures(expr.elseBranch, features)
        }
        is CaseExpr -> {
            features += "case"
            expr.whens.forEach { case ->
                collectExprFeatures(case.condition, features)
                collectExprFeatures(case.result, features)
            }
            collectExprFeatures(expr.elseBranch, features)
        }
        is TryCatchExpr -> {
            features += "try-catch-expr"
            collectExprFeatures(expr.tryExpr, features)
            collectExprFeatures(expr.fallbackExpr, features)
        }
        is SharedStateAwaitExpr -> {
            features += "shared-await"
        }
    }
}

private fun collectFuncBodyFeatures(
    body: FuncBody,
    features: MutableSet<String>,
) {
    when (body) {
        is ExprBody -> collectExprFeatures(body.expr, features)
        is BlockBody -> collectBlockFeatures(body.block, features)
    }
}
