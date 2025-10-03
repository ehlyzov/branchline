package v2.std

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class StdTimeModule : StdModule {
    override fun register(r: StdRegistry) { r.fn("NOW", ::fnNOW) }
}

@OptIn(ExperimentalTime::class)
private fun fnNOW(args: List<Any?>): Any {
    require(args.isEmpty()) { "NOW()" }
    return Clock.System.now().toString()
}

