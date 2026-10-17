package io.github.ehlyzov.branchline.benchmarks.jsonata

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.DeserializationFeature
import java.math.BigDecimal

/** Functional evidence only: no timings, ratios, missing-engine skips or inferred oracle. */
public fun main() {
    // Keep numeric lexemes lossless in both the corpus tree and untyped input/oracle.
    val mapper = ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
    val fixtures = mapper.readTree(priceResource("cases.json"))
    check(fixtures.size() >= 30) { "At least 30 named inputs are required" }
    val ids = fixtures.map { it.get("id").asText() }
    check(ids.distinct().size == ids.size) { "Fixture names must be unique" }
    val branchline = BranchlineCompiler.compileInterpreter(priceResource("transform.bl"))
    val jsonata = JsonataEngines.require("dashjoin") // Unavailable is a gate failure.
    val expression = jsonata.compile(priceResource("transform.jsonata"))
    val jsonataClass = Class.forName("com.dashjoin.jsonata.Jsonata")
    val origin = jsonataClass.protectionDomain.codeSource.location.toString()
    check(origin.endsWith("/jsonata-0.9.10.jar")) { "Expected pinned Dashjoin 0.9.10, loaded $origin" }
    val engines: Map<String, (Any?) -> Any?> = linkedMapOf(
        "branchline-jvm" to branchline,
        "dashjoin-jsonata" to { input -> jsonata.evaluate(expression, input) },
        "kotlin" to ::kotlinPriceIngest,
    )
    val corrupt = System.getProperty("priceIngest.corruptEngine", "")
    require(corrupt.isEmpty() || corrupt in engines) { "Unknown corrupt engine: $corrupt" }
    val failures = mutableListOf<String>()
    var shapeCases = 0
    for ((index, fixture) in fixtures.withIndex()) {
        val id = fixture.get("id").asText()
        check(fixture.has("expected")) { "$id has no independent expected result" }
        val input = mapper.readValue(fixture.get("input").toString(), Any::class.java)
        val expected = canonicalPriceValue(mapper.readValue(fixture.get("expected").toString(), Any::class.java))
        if (!validPriceWireShape(input)) shapeCases++
        for ((engine, evaluate) in engines) {
            try {
                var actual = if (validPriceWireShape(input)) evaluate(interoperablePriceInput(input)) else shapeError()
                if (index == 0 && engine == corrupt) actual = shapeError()
                check(canonicalPriceValue(actual) == expected) {
                    "expected=${fixture.get("expected")} actual=${mapper.writeValueAsString(actual)}"
                }
            } catch (ex: Exception) {
                failures += "$engine / $id: ${ex.message}"
            }
        }
    }
    // The same equality check must reject altered output, even on an otherwise green run.
    val firstExpected = canonicalPriceValue(mapper.readValue(fixtures[0].get("expected").toString(), Any::class.java))
    check(firstExpected != canonicalPriceValue(shapeError())) { "Wrong-output sensitivity fixture must differ" }
    val exactDecimal = mapper.readValue("0.10000000000000001", Any::class.java)
    val roundedDecimal = mapper.readValue("0.1", Any::class.java)
    check(canonicalPriceValue(exactDecimal) != canonicalPriceValue(roundedDecimal)) {
        "Comparator lost an exact decimal difference"
    }
    println("Branchline: local JVM interpreter (repository source); Kotlin: ${KotlinVersion.CURRENT}")
    println("JSONata: Dashjoin JVM com.dashjoin:jsonata:0.9.10 ($origin)")
    println("JVM: ${System.getProperty("java.runtime.version")}; ${System.getProperty("os.name")} ${System.getProperty("os.arch")}")
    println("${fixtures.size()} named inputs; $shapeCases shared wire-schema rejects; ${fixtures.size() - shapeCases} business inputs x ${engines.size} required engines; wrong-output and exact-decimal sensitivity checked")
    check(failures.isEmpty()) { failures.joinToString("\n", prefix = "PRICE INGEST COMPARISON FAILED\n") }
    println("PASS: every declared output equals the independent fixture; no performance or authoring claim measured")
}

private fun priceResource(name: String): String = checkNotNull(
    object {}.javaClass.getResourceAsStream("/price-ingest/$name"),
) { "Missing price-ingest resource $name" }.bufferedReader().use { it.readText() }

private val priceStringFields = listOf(
    "transactionId", "warehouseId", "skuId", "source", "effectiveTime", "storedAt", "eventId", "currency",
)

/** Identical boundary adapter: JSON types and the declared interoperable numeric domain. */
private fun validPriceWireShape(input: Any?): Boolean {
    if (input !is Map<*, *>) return false
    if (priceStringFields.any { input[it] != null && input[it] !is String }) return false
    if (listOf("priceCents", "schemaVersion").any { input[it] != null && input[it] !is Number }) return false
    if (listOf("priceCents", "schemaVersion").any { field ->
        val number = input[field] as Number?
        number != null && !interoperablePriceNumber(number)
    }) return false
    return input["needAck"] == null || input["needAck"] is Boolean
}

private fun interoperablePriceNumber(number: Number): Boolean {
    val binary = number.toDouble()
    return binary.isFinite() && number.toString().toBigDecimal().compareTo(BigDecimal.valueOf(binary)) == 0
}

/** Narrow only after lossless validation; the decimal roundtrip above must preserve value. */
private fun interoperablePriceInput(input: Any?): Map<*, *> = (input as Map<*, *>).mapValues { (key, value) ->
    if (key in listOf("priceCents", "schemaVersion") && value is BigDecimal) value.toDouble() else value
}

private fun shapeError(): Map<String, Any?> = mapOf(
    "accepted" to false, "errors" to listOf("INPUT_SCHEMA"), "ack" to null, "event" to null,
)

private fun kotlinPriceIngest(raw: Any?): Any {
    val input = raw as Map<*, *>
    val errors = mutableListOf<String>()
    val normalized = linkedMapOf<String, Any?>()
    for (field in priceStringFields.dropLast(1)) {
        val value = (input[field] as String?)?.trim() ?: ""
        normalized[field] = value
        if (value.isEmpty()) errors += "${field.uppercase()}_REQUIRED"
    }
    val price = (input["priceCents"] as Number?)?.toString()?.toBigDecimal()
    if (price == null) errors += "PRICE_REQUIRED" else {
        if (price < BigDecimal.ZERO) errors += "PRICE_NEGATIVE"
        if (price.stripTrailingZeros().scale() > 0) errors += "PRICE_FRACTIONAL"
        if (price > BigDecimal("1000000000")) errors += "PRICE_LIMIT"
    }
    val needAck = if (input.containsKey("needAck")) input["needAck"] else true
    if (needAck == null) errors += "NEED_ACK"
    val version = if (input.containsKey("schemaVersion")) input["schemaVersion"] else 1
    val validVersion = version is Number && (version.toDouble() == 1.0 || version.toDouble() == 2.0)
    if (!validVersion) errors += "SCHEMA_VERSION"
    val currency = if (input.containsKey("currency")) input["currency"] else if (version is Number && version.toDouble() == 2.0) null else "USD"
    if (currency != "USD" && currency != "EUR") errors += "CURRENCY"
    val accepted = errors.isEmpty()
    return linkedMapOf(
        "accepted" to accepted,
        "errors" to errors,
        "ack" to if (needAck == true) mapOf(
            "transactionId" to normalized["transactionId"],
            "status" to if (accepted) "ACCEPTED" else "REJECTED",
            "errors" to errors,
        ) else null,
        "event" to if (accepted) normalized + mapOf(
            "priceCents" to input["priceCents"], "currency" to currency, "schemaVersion" to version,
        ) else null,
    )
}

/** JSON numeric formatting alone is immaterial; absent keys, nulls and arrays stay distinct. */
private fun canonicalPriceValue(value: Any?): Any? = when (value) {
    is Number -> value.toString().toBigDecimal().stripTrailingZeros()
    is Map<*, *> -> value.mapValues { canonicalPriceValue(it.value) }
    is List<*> -> value.map(::canonicalPriceValue)
    else -> value
}
