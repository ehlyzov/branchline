package io.github.ehlyzov.branchline.json

internal actual fun platformDoubleToString(value: Double): String {
    val number = value.asDynamic()
    return number.toString()
}
