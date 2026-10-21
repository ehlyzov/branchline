package io.github.ehlyzov.branchline.conformance

internal actual fun readGettingStartedGuide(): String {
    val fs = js("require('fs')")
    val path = js("require('path')")
    var directory = js("process.cwd()") as String
    while (true) {
        val candidate = path.resolve(directory, "docs/guides/getting-started.md") as String
        if (fs.existsSync(candidate) as Boolean) return fs.readFileSync(candidate, "utf8") as String
        val parent = path.resolve(directory, "..") as String
        if (parent == directory) error("Cannot locate docs/guides/getting-started.md from ${js("process.cwd()")}")
        directory = parent
    }
}
