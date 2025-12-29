package v2.ir

import v2.FuncDecl
import v2.debug.Tracer

/**
 * Multiplatform-friendly helpers to run IR and build runners without relying on the VM or parser.
 */

/** Execute a list of IR nodes via the interpreter (Exec) with a prepared environment. */
fun runIR(
    ir: List<IRNode>,
    env: MutableMap<String, Any?>,
    hostFns: Map<String, (List<Any?>) -> Any?> = emptyMap(),
    funcs: Map<String, FuncDecl> = emptyMap(),
    tracer: Tracer? = null,
    stringifyKeys: Boolean = false,
): Any? {
    // Set up a minimal registry for transforms. On JVM this delegates to the full actual; on JS we use the JS actual.
    val reg = TransformRegistry(funcs, hostFns, emptyMap())
    val eval = makeEval(hostFns, funcs, reg, tracer)
    val exec = Exec(ir, eval, tracer)
    return exec.run(env, stringifyKeys)
}

/** Build a simple runner from IR that expects a single input map. */
fun buildRunnerFromIRMP(
    ir: List<IRNode>,
    hostFns: Map<String, (List<Any?>) -> Any?> = emptyMap(),
    funcs: Map<String, FuncDecl> = emptyMap(),
    tracer: Tracer? = null,
): (Map<String, Any?>) -> Any? {
    return { input: Map<String, Any?> ->
        val env = HashMap<String, Any?>().apply {
            this[v2.DEFAULT_INPUT_ALIAS] = input
            putAll(input)
            // Back-compat alias
            for (alias in v2.COMPAT_INPUT_ALIASES) {
                this[alias] = input
            }
        }
        runIR(ir, env, hostFns, funcs, tracer)
    }
}

/** Build a runner from a full Branchline program listing (STREAM transform). */
fun buildRunnerFromProgramMP(
    program: String,
    hostFns: Map<String, (List<Any?>) -> Any?> = v2.std.StdLib.fns,
    runSema: Boolean = false,
    tracer: Tracer? = null,
): (Map<String, Any?>) -> Any? {
    val tokens = v2.Lexer(program).lex()
    val prog = v2.Parser(tokens, program).parse()

    val funcs = prog.decls.filterIsInstance<FuncDecl>().associateBy { it.name }
    val transforms = prog.decls.filterIsInstance<v2.TransformDecl>()
    require(transforms.size == 1) { "Program must contain exactly one TRANSFORM" }
    val t = transforms.single()
    require(t.mode == v2.Mode.BUFFER) { "Only buffer mode is supported" }

    if (runSema) {
        v2.sema.SemanticAnalyzer(hostFns.keys).analyze(prog)
    }

    val ir = ToIR(funcs, hostFns).compile(t.body.statements)
    val typeDecls = prog.decls.filterIsInstance<v2.TypeDecl>()
    val descriptors = buildTransformDescriptors(transforms, typeDecls, hostFns.keys)
    val reg = TransformRegistry(funcs, hostFns, descriptors)
    val eval = makeEval(hostFns, funcs, reg, tracer)
    val exec = Exec(ir, eval, tracer)

    return { input: Map<String, Any?> ->
        val env = HashMap<String, Any?>().apply {
            this[v2.DEFAULT_INPUT_ALIAS] = input
            putAll(input)
            for (alias in v2.COMPAT_INPUT_ALIASES) {
                this[alias] = input
            }
        }
        exec.run(env)
    }
}
