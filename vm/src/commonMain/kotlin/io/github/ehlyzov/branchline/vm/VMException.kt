package io.github.ehlyzov.branchline.vm

sealed class VMException(message: String? = null) : RuntimeException(message) {
    class StackOverflow : VMException("Stack overflow")
    class StackUnderflow : VMException("Stack underflow")
    class DivisionByZero : VMException("Division by zero")
    class TypeMismatch(expected: String, actual: String?) : VMException("Type mismatch: expected $expected, got ${actual ?: "null"}")
    class IndexOutOfBounds(index: Int, size: Int) : VMException("Index out of bounds: $index (size=$size)")
    class VariableNotFound(name: String) : VMException("Variable not found: $name")
    class Abort(val value: Any?) : VMException("Abort")
}

