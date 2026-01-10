package io.github.ehlyzov.branchline.benchmarks.jsonata

import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.Instant

private val ERROR_REPORT_PATH: Path = Paths.get(
    "jsonata-benchmarks",
    "build",
    "reports",
    "jsonata-benchmarks",
    "error-report.log",
)

public object BenchmarkErrorReporter {
    private val lock = Any()

    public fun record(engineId: String, caseId: String, phase: String, throwable: Throwable) {
        val message = buildMessage(engineId, caseId, phase, throwable)
        write(message)
    }

    public fun recordMessage(engineId: String, caseId: String, phase: String, message: String) {
        val payload = buildString {
            append("[")
            append(Instant.now())
            append("] ")
            append("engine=")
            append(engineId)
            append(" case=")
            append(caseId)
            append(" phase=")
            append(phase)
            append("\n")
            append(message)
            append("\n")
            append("---\n")
        }
        write(payload)
    }

    private fun buildMessage(engineId: String, caseId: String, phase: String, throwable: Throwable): String {
        val stackTrace = StringWriter().also { writer ->
            PrintWriter(writer).use { printer ->
                throwable.printStackTrace(printer)
            }
        }.toString()
        return buildString {
            append("[")
            append(Instant.now())
            append("] ")
            append("engine=")
            append(engineId)
            append(" case=")
            append(caseId)
            append(" phase=")
            append(phase)
            append("\n")
            append(stackTrace)
            append("\n---\n")
        }
    }

    private fun write(payload: String) {
        synchronized(lock) {
            Files.createDirectories(ERROR_REPORT_PATH.parent)
            Files.writeString(
                ERROR_REPORT_PATH,
                payload,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
            )
        }
    }
}
