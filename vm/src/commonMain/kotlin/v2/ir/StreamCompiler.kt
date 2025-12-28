package v2.ir

import v2.ExecutionEngine
import v2.FuncDecl
import v2.Mode
import v2.TransformDecl
import v2.ir.TransformDescriptor
import v2.vm.VMExec

private fun dumpIR(nodes: List<IRNode>, indent: String = "") {
    for (n in nodes) {
        when (n) {
            is IRLet -> println("${indent}LET ${n.name} = …")
            is IRAppendTo -> println("${indent}APPEND TO ${n.target} INIT=${n.init != null}")
            is IRSet -> println("${indent}SET ${n.target} = …")
            is IRModify -> println("${indent}MODIFY ${n.target} …")
            is IRExprOutput -> println("${indent}OUTPUT<expr>")
            is IROutput -> println("${indent}OUTPUT{fields=${n.fields.size}}")
            is IRReturn -> println("${indent}RETURN …")
            is IRAbort -> println("${indent}ABORT …")
            is IRIf -> {
                println("${indent}IF (…) {")
                dumpIR(n.thenBody, indent + "  ")
                val elseBody = n.elseBody
                if (elseBody != null) {
                    println("$indent} ELSE {")
                    dumpIR(elseBody, indent + "  ")
                }
                println("$indent}")
            }
            is IRForEach -> {
                println("${indent}FOR ${n.varName} IN ${n.iterable} {")
                dumpIR(n.body, indent + "  ")
                println("$indent}")
            }
            is IRTryCatch -> {
                println("${indent}TRY … (retry=${n.retry})")
            }

            is IRExprStmt -> println("${indent}EXPR<expr>")
            is IRAppendVar -> println("${indent}APPEND TO ${n.name} INIT=${n.init != null}")
            is IRSetVar -> println("${indent}SET ${n.name} = …")
        }
    }
}

fun compileStream(
    t: TransformDecl,
    funcs: Map<String, FuncDecl> = emptyMap(),
    hostFns: Map<String, (List<Any?>) -> Any?> = emptyMap(),
    transforms: Map<String, TransformDescriptor> = emptyMap(), // обязателен!
    engine: ExecutionEngine = ExecutionEngine.INTERPRETER,
): (Map<String, Any?>) -> Any? {
    require(t.mode == Mode.BUFFER) { "Only buffer mode is supported" }

    /* 1. compile AST → IR */
    val irRoot = ToIR(funcs, hostFns).compile(t.body.statements)

    /* 2. prepare registry + eval */
    val registry = TransformRegistry(funcs, hostFns, transforms)
    val eval = makeEval(hostFns, funcs, registry)

    // 3. choose execution engine
    if (engine == ExecutionEngine.VM) {
        val vmExec = VMExec(irRoot, eval, hostFns = hostFns, funcs = funcs)
        return { input: Map<String, Any?> ->
            val env = HashMap<String, Any?>().apply {
                this[v2.DEFAULT_INPUT_ALIAS] = input
                putAll(input)
                for (alias in v2.COMPAT_INPUT_ALIASES) {
                    this[alias] = input
                }
            }
            val produced = vmExec.run(env)
            produced ?: error("No OUTPUT")
        }
    }

    val exec = Exec(irRoot, eval)
    return { input: Map<String, Any?> ->
        val env = HashMap<String, Any?>().apply {
            this[v2.DEFAULT_INPUT_ALIAS] = input
            putAll(input)
            for (alias in v2.COMPAT_INPUT_ALIASES) {
                this[alias] = input
            }
        }
        val produced = exec.run(env)
        produced ?: error("No OUTPUT")
    }
}
