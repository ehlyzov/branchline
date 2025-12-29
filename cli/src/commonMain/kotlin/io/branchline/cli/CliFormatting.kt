package io.branchline.cli

public enum class OutputFormat(val id: String, val pretty: Boolean) {
    JSON("json", true),
    JSON_COMPACT("json-compact", false);

    companion object {
        fun parse(value: String): OutputFormat = when (value.lowercase()) {
            "json" -> JSON
            "json-compact", "json_compact" -> JSON_COMPACT
            else -> throw CliException("Unknown output format '$value'", kind = CliErrorKind.USAGE)
        }
    }
}

public enum class ErrorFormat(val id: String) {
    TEXT("text"),
    JSON("json");

    companion object {
        fun parse(value: String): ErrorFormat = when (value.lowercase()) {
            "text" -> TEXT
            "json" -> JSON
            else -> throw CliException("Unknown error format '$value'", kind = CliErrorKind.USAGE)
        }
    }
}

public enum class TraceFormat(val id: String) {
    TEXT("text"),
    JSON("json");

    companion object {
        fun parse(value: String): TraceFormat = when (value.lowercase()) {
            "text" -> TEXT
            "json" -> JSON
            else -> throw CliException("Unknown trace format '$value'", kind = CliErrorKind.USAGE)
        }
    }
}

public enum class ExitCode(val code: Int) {
    SUCCESS(0),
    USAGE(64),
    INPUT(65),
    IO(74),
    RUNTIME(70),
}

public enum class CliErrorKind(val id: String, val exitCode: ExitCode) {
    USAGE("usage", ExitCode.USAGE),
    INPUT("input", ExitCode.INPUT),
    IO("io", ExitCode.IO),
    RUNTIME("runtime", ExitCode.RUNTIME),
}

public data class CliError(
    val message: String,
    val kind: CliErrorKind,
    val command: CliCommand?,
)
