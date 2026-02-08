package io.github.ehlyzov.branchline.cli

public fun formatXmlOutput(
    value: Any?,
    pretty: Boolean = true,
    strictNamespaces: Boolean = true,
): String = io.github.ehlyzov.branchline.xml.formatXmlOutput(
    value = value,
    pretty = pretty,
    strictNamespaces = strictNamespaces,
)
