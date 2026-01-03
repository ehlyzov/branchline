package io.github.ehlyzov.branchline.benchmarks

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole
import io.github.ehlyzov.branchline.FuncDecl
import io.github.ehlyzov.branchline.Lexer
import io.github.ehlyzov.branchline.Parser
import io.github.ehlyzov.branchline.TransformDecl
import io.github.ehlyzov.branchline.TypeDecl
import io.github.ehlyzov.branchline.ir.Exec
import io.github.ehlyzov.branchline.ir.ToIR
import io.github.ehlyzov.branchline.ir.TransformRegistry
import io.github.ehlyzov.branchline.ir.buildTransformDescriptors
import io.github.ehlyzov.branchline.ir.makeEval
import io.github.ehlyzov.branchline.std.StdLib
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public open class InterpreterTransformBenchmark {
    @Param("small", "medium", "large")
    public lateinit var dataset: String

    private lateinit var input: Map<String, Any?>
    private lateinit var env: MutableMap<String, Any?>
    private lateinit var pathExec: Exec
    private lateinit var arrayExec: Exec
    private lateinit var transformExec: Exec

    @Setup(Level.Trial)
    public fun setup() {
        val size = datasetSizeFromParam(dataset)
        input = BenchDatasets.buildInput(size)
        pathExec = buildExec(PATH_TRANSFORM)
        arrayExec = buildExec(ARRAY_COMP_TRANSFORM)
        transformExec = buildExec(TYPICAL_TRANSFORM)
    }

    @Setup(Level.Invocation)
    public fun resetEnv() {
        env = BenchDatasets.buildEnv(input)
    }

    @Benchmark
    public fun pathExpressions(bh: Blackhole) {
        bh.consume(pathExec.run(env))
    }

    @Benchmark
    public fun arrayComprehensions(bh: Blackhole) {
        bh.consume(arrayExec.run(env))
    }

    @Benchmark
    public fun typicalTransform(bh: Blackhole) {
        bh.consume(transformExec.run(env))
    }
}

private val PATH_TRANSFORM = """
    TRANSFORM T {
        LET firstSku = input.orders[0].items[0].sku;
        LET firstQty = input.orders[0].items[0].qty;
        LET secondSku = input.orders[0].items[1].sku;
        OUTPUT {
            customer: input.customer.name,
            firstSku: firstSku,
            firstQty: firstQty,
            secondSku: secondSku,
        }
    }
""".trimIndent()

private val ARRAY_COMP_TRANSFORM = """
    TRANSFORM T {
        LET expensive = [o.id FOR EACH o IN input.orders WHERE o.total > 150];
        LET skus = [i.sku FOR EACH i IN input.orders[0].items];
        OUTPUT {
            expensive: expensive,
            skus: skus,
        }
    }
""".trimIndent()

private val TYPICAL_TRANSFORM = """
    TRANSFORM T {
        LET total = 0;
        FOR EACH order IN input.orders {
            SET total = total + order.total;
        }
        OUTPUT {
            orderCount: LENGTH(input.orders),
            total: total,
        }
    }
""".trimIndent()

private fun datasetSizeFromParam(param: String): DatasetSize {
    return when (param.lowercase()) {
        "small" -> DatasetSize.SMALL
        "medium" -> DatasetSize.MEDIUM
        "large" -> DatasetSize.LARGE
        else -> error("Unknown dataset size: $param")
    }
}

private fun buildExec(program: String): Exec {
    val hostFns = StdLib.fns
    val tokens = Lexer(program).lex()
    val parsed = Parser(tokens, program).parse()
    val funcs = parsed.decls.filterIsInstance<FuncDecl>().associateBy { it.name }
    val transforms = parsed.decls.filterIsInstance<TransformDecl>()
    val transform = transforms.single()
    val ir = ToIR(funcs, hostFns).compile(transform.body.statements)
    val typeDecls = parsed.decls.filterIsInstance<TypeDecl>()
    val descriptors = buildTransformDescriptors(transforms, typeDecls, hostFns.keys)
    val registry = TransformRegistry(funcs, hostFns, descriptors)
    val eval = makeEval(hostFns, funcs, registry)
    return Exec(ir, eval)
}
