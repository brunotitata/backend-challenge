package com.trace.payment.scheduler

import com.trace.payment.boundary.common.EventPublisherSpec
import com.trace.payment.boundary.common.OutboxEventBO
import com.trace.payment.boundary.database.OutboxGatewaySpec
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class OutboxSchedulerTest {

    private val publishedEvents = mutableListOf<Triple<String, String, String>>()
    private val markedProcessed = mutableListOf<UUID>()
    private val retriedEvents = mutableListOf<UUID>()

    private val outboxGateway = object : OutboxGatewaySpec {
        private val events = mutableListOf<OutboxEventBO>()

        fun addEvent(event: OutboxEventBO) {
            events.add(event)
        }

        override fun save(event: OutboxEventBO) {}
        override fun save(event: OutboxEventBO, tx: com.trace.payment.boundary.common.TransactionContext) {}
        override fun findUnprocessed(limit: Int): List<OutboxEventBO> {
            return events.filter { it.processedAt == null }.take(limit)
        }

        override fun markAsProcessed(id: UUID) {
            markedProcessed.add(id)
            val idx = events.indexOfFirst { it.id == id }
            if (idx >= 0) {
                events[idx] = events[idx].copy(processedAt = java.time.Instant.now())
            }
        }

        override fun incrementRetry(id: UUID) {
            retriedEvents.add(id)
        }
    }

    private val eventPublisher = object : EventPublisherSpec {
        var shouldFailNext = false

        override fun publish(exchange: String, routingKey: String, payload: String) {
            if (shouldFailNext) {
                shouldFailNext = false
                throw RuntimeException("publish failed")
            }
            publishedEvents.add(Triple(exchange, routingKey, payload))
        }
    }

    private val scheduler = OutboxScheduler(
        outboxGateway = outboxGateway,
        eventPublisher = eventPublisher,
        exchangeName = "payment.events",
        pollIntervalSeconds = 15,
    )

    @Test
    fun `processBatch publishes unprocessed events and marks them processed`() {
        val event1 = OutboxEventBO(
            aggregateType = "wallet",
            aggregateId = UUID.randomUUID().toString(),
            eventType = "WALLET_CREATED",
            payload = """{"id":"a","ownerName":"Maria"}""",
        )
        val event2 = OutboxEventBO(
            aggregateType = "policy",
            aggregateId = UUID.randomUUID().toString(),
            eventType = "POLICY_CREATED",
            payload = """{"id":"b","name":"P1"}""",
        )
        outboxGateway.addEvent(event1)
        outboxGateway.addEvent(event2)

        scheduler.processBatch()

        assertEquals(2, publishedEvents.size)
        assertEquals("payment.events", publishedEvents[0].first)
        assertEquals("wallet", publishedEvents[0].second)
        assertEquals("payment.events", publishedEvents[1].first)
        assertEquals("policy", publishedEvents[1].second)

        assertEquals(2, markedProcessed.size)
        assertEquals(event1.id, markedProcessed[0])
        assertEquals(event2.id, markedProcessed[1])
    }

    @Test
    fun `processBatch increments retry on publish failure`() {
        val event = OutboxEventBO(
            aggregateType = "wallet",
            aggregateId = UUID.randomUUID().toString(),
            eventType = "WALLET_CREATED",
            payload = """{"id":"a"}""",
        )
        outboxGateway.addEvent(event)
        eventPublisher.shouldFailNext = true

        scheduler.processBatch()

        assertEquals(0, publishedEvents.size)
        assertEquals(0, markedProcessed.size)
        assertEquals(1, retriedEvents.size)
        assertEquals(event.id, retriedEvents[0])
    }

    @Test
    fun `processBatch does nothing when no unprocessed events`() {
        scheduler.processBatch()

        assertEquals(0, publishedEvents.size)
        assertEquals(0, markedProcessed.size)
    }
}
