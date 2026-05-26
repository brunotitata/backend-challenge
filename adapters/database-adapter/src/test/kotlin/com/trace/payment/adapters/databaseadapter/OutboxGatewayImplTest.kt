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
import com.trace.payment.adapters.database.jooq.tables.OutboxEvents.OUTBOX_EVENTS
import org.jooq.impl.DSL

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
        assertNull(unprocessed[0].status)
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
            val tx = DSL.using(configuration)
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
        outboxGateway.markAsSent(event1.id)

        val unprocessed = outboxGateway.findUnprocessed(10)
        assertEquals(1, unprocessed.size)
        assertEquals(event2.id, unprocessed[0].id)
    }

    @Test
    fun `markAsSent sets processed_at and status`() {
        val event = OutboxEventBO(
            aggregateType = "wallet",
            aggregateId = "w1",
            eventType = "WALLET_CREATED",
            payload = "{}",
        )
        outboxGateway.save(event)

        outboxGateway.markAsSent(event.id)

        val unprocessed = outboxGateway.findUnprocessed(10)
        assertTrue(unprocessed.isEmpty())

        val all = dsl.selectFrom(OUTBOX_EVENTS).fetch()
        assertEquals(1, all.size)
        assertEquals("SENT", all[0].status)
        assertNotNull(all[0].processedAt)
    }

    @Test
    fun `markAsError increases retry_count and sets status`() {
        val event = OutboxEventBO(
            aggregateType = "wallet",
            aggregateId = "w1",
            eventType = "WALLET_CREATED",
            payload = "{}",
        )
        outboxGateway.save(event)

        outboxGateway.markAsError(event.id)
        outboxGateway.markAsError(event.id)

        val unprocessed = outboxGateway.findUnprocessed(10)
        assertEquals(1, unprocessed.size)
        assertEquals(2, unprocessed[0].retryCount)
        assertEquals("ERROR", unprocessed[0].status)
    }

    companion object {
        @Container
        @JvmField
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }
}
