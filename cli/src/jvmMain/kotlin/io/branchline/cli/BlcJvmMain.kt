package io.branchline.cli

import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val status = BranchlineCli.run(args.toList(), PlatformKind.JVM, defaultCommand = CliCommand.COMPILE)
    exitProcess(status)
}

