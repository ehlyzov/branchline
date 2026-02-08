package io.github.ehlyzov.branchline.sema

import io.github.ehlyzov.branchline.TokenType
import io.github.ehlyzov.branchline.contract.ValueShape

public data class BinaryTypeEvalInput(
    val operator: TokenType,
    val left: ValueShape,
    val right: ValueShape,
)

public data class BinaryTypeEvalResult(
    val shape: ValueShape,
    val ruleId: String,
    val confidence: Double = 0.85,
    val enforceOperandShape: ValueShape? = null,
)

public fun interface BinaryTypeEvalRule {
    public fun evaluate(input: BinaryTypeEvalInput): BinaryTypeEvalResult?
}

public object DefaultBinaryTypeEvalRules {
    private val numericOperators = setOf(
        TokenType.PLUS,
        TokenType.MINUS,
        TokenType.STAR,
        TokenType.SLASH,
        TokenType.PERCENT,
    )

    private val booleanOperators = setOf(
        TokenType.EQ,
        TokenType.NEQ,
        TokenType.LT,
        TokenType.GT,
        TokenType.LE,
        TokenType.GE,
        TokenType.AND,
        TokenType.OR,
    )

    public val rules: List<BinaryTypeEvalRule> = listOf(
        BinaryTypeEvalRule(::evaluateTextConcatRule),
        BinaryTypeEvalRule(::evaluateNumericRule),
        BinaryTypeEvalRule(::evaluateBooleanRule),
    )

    private fun evaluateTextConcatRule(input: BinaryTypeEvalInput): BinaryTypeEvalResult? {
        if (input.operator != TokenType.PLUS) return null
        if (input.left != ValueShape.TextShape && input.right != ValueShape.TextShape) return null
        return BinaryTypeEvalResult(
            shape = ValueShape.TextShape,
            ruleId = "text-concat-op",
            confidence = 0.9,
        )
    }

    private fun evaluateNumericRule(input: BinaryTypeEvalInput): BinaryTypeEvalResult? {
        if (input.operator !in numericOperators) return null
        if (input.left != ValueShape.NumberShape && input.right != ValueShape.NumberShape) return null
        return BinaryTypeEvalResult(
            shape = ValueShape.NumberShape,
            ruleId = "numeric-binary-op",
            confidence = 0.9,
            enforceOperandShape = ValueShape.NumberShape,
        )
    }

    private fun evaluateBooleanRule(input: BinaryTypeEvalInput): BinaryTypeEvalResult? {
        if (input.operator !in booleanOperators) return null
        return BinaryTypeEvalResult(
            shape = ValueShape.BooleanShape,
            ruleId = "boolean-binary-op",
            confidence = 0.85,
        )
    }
}
