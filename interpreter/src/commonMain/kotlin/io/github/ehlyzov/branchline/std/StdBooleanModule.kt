package io.github.ehlyzov.branchline.std

class StdBooleanModule : StdModule {
    override fun register(r: StdRegistry) {
        r.fn("NOT", ::fnNOT)
        r.fn("EXISTS", ::fnEXISTS)
    }
}

/**
 * NOT(x) – returns Boolean NOT of the argument.  The argument is first cast to a boolean
 * using the same rules as the existing BOOLEAN() function.
 */
private fun fnNOT(args: List<Any?>): Boolean {
    require(args.size == 1) { "NOT(x)" }
    return !truthy(args[0])
}

/**
 * EXISTS(x) – returns true if the supplied value is not null (i.e. it "exists").
 */
private fun fnEXISTS(args: List<Any?>): Boolean {
    require(args.size == 1) { "EXISTS(x)" }
    return args[0] != null
}
