package com.example.ktorservice.api

import com.example.ktorservice.ApplicationConfig
import com.example.ktorservice.module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Stateful host behavior is a separate gate from the pure three-engine corpus. */
class PricePilotReplayIT {
    @Test
    fun `replay preserves cents and suppresses stale duplicate and invalid effects`() = testApplication {
        val mapper = jacksonObjectMapper().registerModule(JavaTimeModule())
        application {
            module(ApplicationConfig(clock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC)))
        }
        data class Step(val tx: String, val price: String, val minute: String, val ack: Boolean, val status: String)
        val steps = listOf(
            Step("tx-001", "0.00", "00", false, "CREATED"),
            Step("tx-002", "0.01", "01", true, "UPDATED"),
            Step("tx-old", "9.00", "00", true, "STALE"),
            Step("tx-002", "99.00", "02", true, "DUPLICATE"),
            Step("tx-scale", "1.001", "03", true, "VALIDATION_FAILED"),
            Step("tx-negative", "-0.01", "03", true, "VALIDATION_FAILED"),
            Step("tx-003", "0.02", "01", true, "UPDATED"),
        )
        for (step in steps) {
            val response = client.post("/api/v1/price-change") {
                contentType(ContentType.Application.Json)
                setBody(mapper.writeValueAsString(mapOf(
                    "transactionId" to step.tx,
                    "warehouseId" to "11111111-1111-1111-1111-111111111111",
                    "skuId" to "22222222-2222-2222-2222-222222222222",
                    "price" to BigDecimal(step.price),
                    "effectiveTime" to "2026-01-01T10:${step.minute}:00Z",
                    "source" to "synthetic-replay",
                    "needAck" to step.ack,
                )))
            }
            assertEquals(HttpStatusCode.OK, response.status, step.tx)
            val payload: Map<String, Any?> = mapper.readValue(response.bodyAsText())
            assertEquals(step.status, payload["mutationStatus"], step.tx)
            assertEquals(step.status in listOf("CREATED", "UPDATED", "DUPLICATE"), payload["accepted"], step.tx)
            if (!step.ack) assertNull(payload["ack"], step.tx)
            if (step.status !in listOf("CREATED", "UPDATED")) assertNull(payload["event"], step.tx)
            if (step.tx == "tx-scale") assertEquals(listOf("price scale must be <= 2"), payload["errors"])
        }
        val prices: List<Map<String, Any?>> = mapper.readValue(client.get("/api/v1/prices").bodyAsText())
        assertEquals(1, prices.size)
        assertEquals(0, BigDecimal("0.02").compareTo(BigDecimal(prices.single()["price"].toString())))
        assertEquals("tx-003", prices.single()["transactionId"])
        for ((route, count) in mapOf("history" to 3, "outbox/events" to 3, "outbox/acks" to 6)) {
            val records: List<Map<String, Any?>> = mapper.readValue(client.get("/api/v1/$route").bodyAsText())
            assertEquals(count, records.size, route)
        }
    }
}
