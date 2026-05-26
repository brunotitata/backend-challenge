package com.trace.payment.adapters.database.gateway

import com.trace.payment.adapters.database.config.DatabaseFactory
import com.trace.payment.adapters.database.config.JooqFactory
import com.trace.payment.boundary.common.DatabaseConfigBO
import com.trace.payment.boundary.common.OutboxEventBO
import org.jooq.DSLContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OutboxGatewayImplTest {

    private lateinit var dataSource: DataSource
    private lateinit var dsl: DSLContext
    private lateinit var outboxGateway: OutboxGatewayImpl

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseFactory.create(
            DatabaseConfigBO(
                url = postgres.jdbcUrl,
                username = postgres.username,
                password = postgres.password,
            ),
        )
        dsl = JooqFactory.create(dataSource)
        outboxGateway = OutboxGatewayImpl(dsl)
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("TRUNCATE TABLE outbox_events RESTART IDENTITY CASCADE")
            }
        }
    }

    @AfterEach
    fun tearDown() {
        (dataSource as? AutoCloseable)?.close()
    }

    @Test
    fun `save inserts event into outbox_events`() {
        val event = OutboxEventBO(
            aggregateType = "wallet",
            aggregateId = "test-wallet-id",
            eventType = "WALLET_CREATED",
            payload = """{"id":"test-wallet-id","ownerName":"Maria"}""",
        )

        outboxGateway.save(event)

        val unprocessed = outboxGateway.findUnprocessed(10)
        assertEquals(1, unprocessed.size)
        assertEquals(event.id, unprocessed[0].id)
        assertEquals("wallet", unprocessed[0].aggregateType)
        assertEquals("test-wallet-id", unprocessed[0].aggregateId)
        assertEquals("WALLET_CREATED", unprocessed[0].eventType)
        assertEquals(0, unprocessed[0].retryCount)
        assertNull(unprocessed[0].processedAt)
    }

    @Test
    fun `save in transaction inserts event`() {
        val event = OutboxEventBO(
            aggregateType = "payment",
            aggregateId = "payment-1",
            eventType = "PAYMENT_APPROVED",
            payload = """{"id":"payment-1","amount":100}""",
        )

        dsl.transactionResult { configuration ->
            val tx = org.jooq.impl.DSL.using(configuration)
            outboxGateway.save(event, JooqTransactionContext(tx))
            "done"
        }

        val unprocessed = outboxGateway.findUnprocessed(10)
        assertEquals(1, unprocessed.size)
        assertEquals("payment", unprocessed[0].aggregateType)
    }

    @Test
    fun `findUnprocessed returns only unprocessed events ordered by created_at`() {
        val event1 = OutboxEventBO(
            aggregateType = "wallet",
            aggregateId = "w1",
            eventType = "WALLET_CREATED",
            payload = "{}",
            createdAt = Instant.now().minusSeconds(10),
        )
        val event2 = OutboxEventBO(
            aggregateType = "policy",
            aggregateId = "p1",
            eventType = "POLICY_CREATED",
            payload = "{}",
            createdAt = Instant.now().minusSeconds(5),
        )
        outboxGateway.save(event1)
        outboxGateway.save(event2)
        outboxGateway.markAsProcessed(event1.id)

        val unprocessed = outboxGateway.findUnprocessed(10)
        assertEquals(1, unprocessed.size)
        assertEquals(event2.id, unprocessed[0].id)
    }

    @Test
    fun `markAsProcessed sets processed_at`() {
        val event = OutboxEventBO(
            aggregateType = "wallet",
            aggregateId = "w1",
            eventType = "WALLET_CREATED",
            payload = "{}",
        )
        outboxGateway.save(event)

        outboxGateway.markAsProcessed(event.id)

        val unprocessed = outboxGateway.findUnprocessed(10)
        assertTrue(unprocessed.isEmpty())
    }

    @Test
    fun `incrementRetry increases retry_count`() {
        val event = OutboxEventBO(
            aggregateType = "wallet",
            aggregateId = "w1",
            eventType = "WALLET_CREATED",
            payload = "{}",
        )
        outboxGateway.save(event)

        outboxGateway.incrementRetry(event.id)
        outboxGateway.incrementRetry(event.id)

        val unprocessed = outboxGateway.findUnprocessed(10)
        assertEquals(1, unprocessed.size)
        assertEquals(2, unprocessed[0].retryCount)
    }

    companion object {
        @Container
        @JvmField
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }
}
