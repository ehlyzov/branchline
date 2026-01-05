package io.github.ehlyzov.branchline.ir

import io.github.ehlyzov.branchline.Parser
import io.github.ehlyzov.branchline.FuncDecl
import io.github.ehlyzov.branchline.SharedDecl
import io.github.ehlyzov.branchline.debug.Tracer
import io.github.ehlyzov.branchline.std.SharedResourceHandle
import io.github.ehlyzov.branchline.std.SharedStoreProvider
import io.github.ehlyzov.branchline.std.SharedResourceKind

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
    val eval = makeEval(hostFns, funcs, reg, tracer, sharedStore = SharedStoreProvider.store)
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
            this[io.github.ehlyzov.branchline.DEFAULT_INPUT_ALIAS] = input
            putAll(input)
            // Back-compat alias
            for (alias in io.github.ehlyzov.branchline.COMPAT_INPUT_ALIASES) {
                this[alias] = input
            }
        }
        runIR(ir, env, hostFns, funcs, tracer)
    }
}

/** Build a runner from a full Branchline program listing (STREAM transform). */
fun buildRunnerFromProgramMP(
    program: String,
    hostFns: Map<String, (List<Any?>) -> Any?> = io.github.ehlyzov.branchline.std.StdLib.fns,
    runSema: Boolean = false,
    tracer: Tracer? = null,
): (Map<String, Any?>) -> Any? {
    val tokens = io.github.ehlyzov.branchline.Lexer(program).lex()
    val prog = Parser(tokens, program).parse()
    val sharedDecls = prog.decls.filterIsInstance<SharedDecl>()
    SharedStoreProvider.store?.let { store ->
        for (decl in sharedDecls) {
            val kind = when (decl.kind) {
                io.github.ehlyzov.branchline.SharedKind.SINGLE -> SharedResourceKind.SINGLE
                io.github.ehlyzov.branchline.SharedKind.MANY -> SharedResourceKind.MANY
            }
            if (!store.hasResource(decl.name)) {
                store.addResource(decl.name, kind)
            }
        }
    }

    val funcs = prog.decls.filterIsInstance<FuncDecl>().associateBy { it.name }
    val transforms = prog.decls.filterIsInstance<io.github.ehlyzov.branchline.TransformDecl>()
    require(transforms.size == 1) { "Program must contain exactly one TRANSFORM" }
    val t = transforms.single()
    require(t.mode == io.github.ehlyzov.branchline.Mode.BUFFER) { "Only buffer mode is supported" }

    if (runSema) {
        io.github.ehlyzov.branchline.sema.SemanticAnalyzer(hostFns.keys).analyze(prog)
    }

    val ir = ToIR(funcs, hostFns).compile(t.body.statements)
    val typeDecls = prog.decls.filterIsInstance<io.github.ehlyzov.branchline.TypeDecl>()
    val descriptors = buildTransformDescriptors(transforms, typeDecls, hostFns.keys)
    val reg = TransformRegistry(funcs, hostFns, descriptors)
    val eval = makeEval(hostFns, funcs, reg, tracer)
    val exec = Exec(ir, eval, tracer)

    return { input: Map<String, Any?> ->
        val env = HashMap<String, Any?>().apply {
            this[io.github.ehlyzov.branchline.DEFAULT_INPUT_ALIAS] = input
            putAll(input)
        for (alias in io.github.ehlyzov.branchline.COMPAT_INPUT_ALIASES) {
            this[alias] = input
        }
        for (decl in sharedDecls) {
            if (!containsKey(decl.name)) {
                this[decl.name] = SharedResourceHandle(decl.name)
            }
        }
    }
        exec.run(env)
    }
}
