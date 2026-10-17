package io.github.ehlyzov.branchline.cli

import io.github.ehlyzov.branchline.debug.Debug
import io.github.ehlyzov.branchline.debug.TraceEvent
import io.github.ehlyzov.branchline.debug.TraceOptions
import io.github.ehlyzov.branchline.debug.Tracer
import io.github.ehlyzov.branchline.vm.BytecodeIO
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompiledFanoutIsolationTest {
    @Test
    fun overlappingCompiledWorkersKeepTheirOwnEnvironmentAndStack() {
        val fixture = fixture()
        val serial = execute(fixture, jobs = 1)
        assertEquals(0, serial.code, serial.err)
        assertEquals(Json.parseToJsonElement(fixture.expected), Json.parseToJsonElement(serial.out))
        val firstRead = CountDownLatch(1)
        val secondRead = CountDownLatch(1)
        val firstOutput = CountDownLatch(1)
        val visited = ThreadLocal.withInitial { false }
        val firstWorker = java.util.concurrent.atomic.AtomicReference<Thread>()
        val observedWorkers = java.util.concurrent.ConcurrentHashMap.newKeySet<Thread>()
        val previous = Debug.tracer
        Debug.tracer = object : Tracer {
            override val opts = TraceOptions()
            override fun on(event: TraceEvent) {
                if (event is TraceEvent.Read && event.name == "input" && !visited.get()) {
                    visited.set(true)
                    observedWorkers.add(Thread.currentThread())
                    if (firstWorker.compareAndSet(null, Thread.currentThread())) {
                        firstRead.countDown()
                        secondRead.awaitReady()
                    } else {
                        firstRead.awaitReady()
                        secondRead.countDown()
                        firstOutput.awaitReady()
                    }
                }
                if (event is TraceEvent.Output && Thread.currentThread() === firstWorker.get()) {
                    firstOutput.countDown()
                }
            }
        }
        val concurrent = try {
            execute(fixture, jobs = 2)
        } finally {
            firstRead.countDown()
            secondRead.countDown()
            firstOutput.countDown()
            Debug.tracer = previous
        }
        assertEquals(0, concurrent.code, concurrent.err)
        assertEquals(serial.out, concurrent.out)
        assertEquals(2, observedWorkers.size, "Two distinct workers must reach the VM")
    }

    @Test
    fun compiledJobsReuseWorkersWithContractsAndPreserveReportOrder() {
        val fixture = fixture(count = 12)
        for (jobs in listOf(1, 4)) {
            val result = execute(fixture, jobs, listOf("--contracts", "strict"))
            assertEquals(0, result.code, result.err)
            assertEquals(Json.parseToJsonElement(fixture.expected), Json.parseToJsonElement(result.out))
        }
        val summary = execute(fixture, 4, listOf("--summary-transform", "Summary"))
        assertEquals(0, summary.code, summary.err)
        assertEquals(Json.parseToJsonElement("""{"count":12,"first":"a.json","last":"l.json"}"""),
            Json.parseToJsonElement(summary.out))
    }

    @Test
    fun tracedCompiledFanoutMatchesSerialReportsAndTraceOrder() {
        val fixture = fixture()
        val serial = execute(fixture, 1, listOf("--trace-format", "json"))
        val parallel = execute(fixture, 4, listOf("--trace-format", "json"))
        assertEquals(0, serial.code, serial.err)
        assertEquals(0, parallel.code, parallel.err)
        assertEquals(Json.parseToJsonElement(fixture.expected), Json.parseToJsonElement(parallel.out))
        // Timing varies, but the event labels must have the same sequential order.
        fun labels(raw: String): List<String> = Regex("\"label\":\"([^\"]*)\"")
            .findAll(raw).map { it.groupValues[1] }.toList()
        assertTrue(labels(serial.err).isNotEmpty(), serial.err)
        assertEquals(labels(serial.err), labels(parallel.err))
    }

    @Test
    fun compiledFanoutReturnsRuntimeFailureWithoutPartialOutputAndRestoresStore() {
        val fixture = fixture(fail = true)
        val previousStore = io.github.ehlyzov.branchline.std.SharedStoreProvider.store
        for (jobs in listOf(1, 4)) {
            val result = execute(fixture, jobs)
            assertEquals(ExitCode.RUNTIME.code, result.code, result.err)
            assertTrue(result.err.contains("NUMBER: cannot parse 'invalid-a.json'"), result.err)
            assertEquals("", result.out)
            assertTrue(io.github.ehlyzov.branchline.std.SharedStoreProvider.store === previousStore)
        }
    }

    private data class Fixture(val artifact: String, val sharedDir: String, val expected: String)
    private data class Result(val code: Int, val out: String, val err: String)

    private fun fixture(count: Int = 2, fail: Boolean = false): Fixture {
        val source = """
            SHARED items MANY;
            TRANSFORM Item(input: { key: string, resource: string }) -> { key: string, value: number } {
                LET key = input.key;
                LET value = AWAIT_SHARED("items", key);
                OUTPUT { key: key, value: NUMBER(value) };
            }
            TRANSFORM Summary {
                OUTPUT { count: LENGTH(input.reports), first: input.reports[0].key,
                    last: input.reports[LENGTH(input.reports) - 1].key };
            }
        """.trimIndent()
        val runtime = BranchlineProgram(source)
        val transform = runtime.selectTransform("Item")
        val artifact = CompiledArtifact(
            transform = "Item", script = source,
            bytecode = BytecodeIO.serializeBytecode(runtime.compileBytecode(transform)),
        )
        val path = Files.createTempFile("compiled-fanout", ".blc")
        Files.writeString(path, ArtifactCodec.encode(artifact))
        val dir = Files.createTempDirectory("compiled-fanout-input")
        val expected = (0 until count).joinToString(",", "[", "]") { index ->
            val key = "${'a' + index}.json"
            val value = (index + 1) * 11
            Files.writeString(dir.resolve(key), if (fail) "\"invalid-$key\"" else value.toString())
            """{"path":"$key","resource":"items","key":"$key","value":$value}"""
        }
        return Fixture(path.toString(), dir.toString(), expected)
    }

    private fun execute(fixture: Fixture, jobs: Int, extraArgs: List<String> = emptyList()): Result {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val previousOut = System.out
        val previousErr = System.err
        try {
            System.setOut(PrintStream(out, true, StandardCharsets.UTF_8))
            System.setErr(PrintStream(err, true, StandardCharsets.UTF_8))
            val code = BranchlineCli.run(listOf(
                fixture.artifact, "--shared-dir", "items=${fixture.sharedDir}",
                "--shared-format", "json", "--shared-key", "basename", "--jobs", jobs.toString(),
                "--output-format", "json-compact",
            ) + extraArgs, PlatformKind.JVM, CliCommand.EXECUTE)
            return Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8))
        } finally {
            System.setOut(previousOut)
            System.setErr(previousErr)
        }
    }

    private fun CountDownLatch.awaitReady() {
        assertTrue(await(30, TimeUnit.SECONDS), "Worker coordination did not complete")
    }
}
