@file:Suppress("UnsafeCastFromDynamic")

package io.branchline.cli

import kotlin.js.json

fun main() {
    val process: dynamic = js("process")
    val argsList = mutableListOf<String>()
    val length = process.argv.length as Int
    var index = 2
    while (index < length) {
        argsList += process.argv[index] as String
        index += 1
    }
    val status = BranchlineCli.run(argsList, PlatformKind.JS)
    process.exit(status)
}
