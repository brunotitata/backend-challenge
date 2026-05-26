package com.trace.payment.application

import com.trace.payment.adapters.database.config.DatabaseFactory
import com.trace.payment.adapters.database.config.JooqFactory
import com.trace.payment.adapters.database.dao.PolicyDAOSpecImpl
import com.trace.payment.adapters.database.dao.WalletDAOSpecImpl
import com.trace.payment.adapters.database.gateway.JooqTransactionManager
import com.trace.payment.adapters.database.gateway.OutboxGatewayImpl
import com.trace.payment.adapters.database.gateway.PaymentGatewayImpl
import com.trace.payment.adapters.web.configs.configureErrorHandling
import com.trace.payment.adapters.web.configs.configureSerialization
import com.trace.payment.adapters.web.routes.configurePaymentRoutes
import com.trace.payment.adapters.web.routes.configurePolicyRoutes
import com.trace.payment.adapters.web.routes.configureWalletRoutes
import com.trace.payment.boundary.common.DatabaseConfigBO
import com.trace.payment.core.usecase.AssignPolicyUseCaseImpl
import com.trace.payment.core.usecase.CreatePolicyUseCaseImpl
import com.trace.payment.core.usecase.CreateWalletUseCaseSpecImpl
import com.trace.payment.core.usecase.ListPaymentsUseCaseImpl
import com.trace.payment.core.usecase.ListPoliciesUseCaseImpl
import com.trace.payment.core.usecase.ListWalletPoliciesUseCaseImpl
import com.trace.payment.core.usecase.PolicyEvaluatorRegistryImpl
import com.trace.payment.core.usecase.PolicyResolverImpl
import com.trace.payment.core.usecase.ProcessPaymentUseCaseImpl
import com.trace.payment.core.usecase.TxCountLimitEvaluator
import com.trace.payment.core.usecase.ValueLimitEvaluator
import com.trace.payment.adapters.database.jooq.tables.PaymentIdempotencyKeys.PAYMENT_IDEMPOTENCY_KEYS
import com.trace.payment.adapters.database.jooq.tables.Payments.PAYMENTS
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.AfterEach
import org.jooq.DSLContext
import org.jooq.exception.DataAccessException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import com.trace.payment.adapters.database.jooq.tables.WalletPolicies
import java.net.URI
import java.time.OffsetDateTime
import java.math.BigDecimal
import java.net.ServerSocket
import org.junit.jupiter.api.Assertions
import io.ktor.client.HttpClient as KtorHttpClient

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PaymentIntegrationTest {

    private lateinit var dataSource: DataSource
    private lateinit var dsl: DSLContext

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
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("TRUNCATE TABLE wallet_policies, payment_idempotency_keys, payments, limit_consumptions, policies, wallets RESTART IDENTITY CASCADE")
            }
        }
    }

    @AfterEach
    fun tearDown() {
        (dataSource as? AutoCloseable)?.close()
    }

    @Test
    fun `POST payments within daytime limit returns 201`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        val body = """{"amount":500.00,"occurredAt":"2024-08-26T10:00:00.0000Z"}"""

        val response = client.post("/wallets/$walletId/payments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", "daytime-test-1")
            setBody(body)
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val responseBody = response.bodyAsText()
        assertTrue(responseBody.contains(""""paymentId":"""))
        assertTrue(responseBody.contains(""""status":"APPROVED""""))
        assertTrue(responseBody.contains(""""amount":"500.00""""))
    }

    @Test
    fun `POST payments within nighttime limit returns 201`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        val body = """{"amount":500.00,"occurredAt":"2024-08-26T22:00:00.0000Z"}"""

        val response = client.post("/wallets/$walletId/payments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", "nighttime-test-1")
            setBody(body)
        }

        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `POST payments within weekend limit returns 201`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        val body = """{"amount":500.00,"occurredAt":"2024-08-24T14:00:00.0000Z"}"""

        val response = client.post("/wallets/$walletId/payments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", "weekend-test-1")
            setBody(body)
        }

        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `POST payments with amount zero returns 400`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        val body = """{"amount":0,"occurredAt":"2024-08-26T10:00:00.0000Z"}"""

        val response = client.post("/wallets/$walletId/payments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", "amount-zero-test")
            setBody(body)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST payments with amount above maxPerPayment returns 400`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        val body = """{"amount":1500.00,"occurredAt":"2024-08-26T10:00:00.0000Z"}"""

        val response = client.post("/wallets/$walletId/payments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", "max-per-payment-test")
            setBody(body)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST payments with negative amount returns 400`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        val body = """{"amount":-100.00,"occurredAt":"2024-08-26T10:00:00.0000Z"}"""

        val response = client.post("/wallets/$walletId/payments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", "negative-amount-test")
            setBody(body)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST payments with more than two decimal places returns 400`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        val body = """{"amount":1.001,"occurredAt":"2024-08-26T10:00:00.0000Z"}"""

        val response = client.post("/wallets/$walletId/payments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", "too-many-decimals")
            setBody(body)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST payments with amount exceeding database precision returns 400`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        val body = """{"amount":999999999999999999.99,"occurredAt":"2024-08-26T10:00:00.0000Z"}"""

        val response = client.post("/wallets/$walletId/payments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", "too-large-amount")
            setBody(body)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST payments exceeding daytime limit returns 422`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        for (i in 0..3) {
            val hour = 10 + i
            val resp = client.post("/wallets/$walletId/payments") {
                contentType(ContentType.Application.Json)
                header("Idempotency-Key", "exceed-limit-$i")
                setBody("""{"amount":1000.00,"occurredAt":"2024-08-26T${hour}:00:00.0000Z"}""")
            }
            assertEquals(HttpStatusCode.Created, resp.status, "Payment ${i + 1} should be approved")
        }

        val response = client.post("/wallets/$walletId/payments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", "exceed-limit-final")
            setBody("""{"amount":100.00,"occurredAt":"2024-08-26T15:00:00.0000Z"}""")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `POST payments with non-existent wallet returns 404`() = testApplication {
        application { configureTestApplication() }

        val body = """{"amount":100.00,"occurredAt":"2024-08-26T10:00:00.0000Z"}"""
        val response = client.post("/wallets/${UUID.randomUUID()}/payments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", "nonexistent-wallet-test")
            setBody(body)
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `POST payments persists approved payment in database`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        val body = """{"amount":250.00,"occurredAt":"2024-08-26T14:00:00.0000Z"}"""

        val response = client.post("/wallets/$walletId/payments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", "persist-test")
            setBody(body)
        }

        assertEquals(HttpStatusCode.Created, response.status)

        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT COUNT(*) FROM payments WHERE wallet_id = ?::uuid AND status = 'APPROVED'",
            ).use { stmt ->
                stmt.setObject(1, UUID.fromString(walletId))
                stmt.executeQuery().use { rs ->
                    rs.next()
                    assertEquals(1, rs.getInt(1))
                }
            }
        }
    }

    @Test
    fun `POST payments without body returns 400`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        val response = client.post("/wallets/$walletId/payments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", "no-body-test")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST payments without occurredAt returns 400`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        val body = """{"amount":100.00}"""
        val response = client.post("/wallets/$walletId/payments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", "no-occurredat-test")
            setBody(body)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST payments with invalid occurredAt returns 400`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        val body = """{"amount":100.00,"occurredAt":"not-a-date"}"""
        val response = client.post("/wallets/$walletId/payments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", "invalid-date-test")
            setBody(body)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `consumption does not mix across different wallets`() = testApplication {
        application { configureTestApplication() }

        val walletA = createWallet("Alice")
        val walletB = createWallet("Bob")

        client.post("/wallets/$walletA/payments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", "wallet-a-consume")
            setBody("""{"amount":4000.00,"occurredAt":"2024-08-26T10:00:00.0000Z"}""")
        }

        val response = client.post("/wallets/$walletB/payments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", "wallet-b-consume")
            setBody("""{"amount":500.00,"occurredAt":"2024-08-26T10:00:00.0000Z"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `retry with same key and payload returns same paymentId`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        val body = """{"amount":300.00,"occurredAt":"2024-08-26T10:00:00.0000Z"}"""
        val idempotencyKey = "retry-same-payload"

        val firstResponse = client.post("/wallets/$walletId/payments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", idempotencyKey)
            setBody(body)
        }
        assertEquals(HttpStatusCode.Created, firstResponse.status)
        val firstBody = firstResponse.bodyAsText()

        val secondResponse = client.post("/wallets/$walletId/payments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", idempotencyKey)
            setBody(body)
        }
        assertEquals(HttpStatusCode.Created, secondResponse.status)
        val secondBody = secondResponse.bodyAsText()

        assertEquals(firstBody, secondBody)
    }

    @Test
    fun `retry with same key and different amount returns 409`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        val idempotencyKey = "retry-diff-amount"

        val firstResponse = client.post("/wallets/$walletId/payments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", idempotencyKey)
            setBody("""{"amount":300.00,"occurredAt":"2024-08-26T10:00:00.0000Z"}""")
        }
        assertEquals(HttpStatusCode.Created, firstResponse.status)

        val secondResponse = client.post("/wallets/$walletId/payments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", idempotencyKey)
            setBody("""{"amount":500.00,"occurredAt":"2024-08-26T10:00:00.0000Z"}""")
        }

        assertEquals(HttpStatusCode.Conflict, secondResponse.status)
    }

    @Test
    fun `retry with same key and different occurredAt returns 409`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        val idempotencyKey = "retry-diff-occurredat"

        val firstResponse = client.post("/wallets/$walletId/payments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", idempotencyKey)
            setBody("""{"amount":300.00,"occurredAt":"2024-08-26T10:00:00.0000Z"}""")
        }
        assertEquals(HttpStatusCode.Created, firstResponse.status)

        val secondResponse = client.post("/wallets/$walletId/payments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", idempotencyKey)
            setBody("""{"amount":300.00,"occurredAt":"2024-08-26T11:00:00.0000Z"}""")
        }

        assertEquals(HttpStatusCode.Conflict, secondResponse.status)
    }

    @Test
    fun `same key in different wallets does not conflict`() = testApplication {
        application { configureTestApplication() }

        val walletA = createWallet("Alice")
        val walletB = createWallet("Bob")

        val firstResponse = client.post("/wallets/$walletA/payments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", "shared-key")
            setBody("""{"amount":100.00,"occurredAt":"2024-08-26T10:00:00.0000Z"}""")
        }
        assertEquals(HttpStatusCode.Created, firstResponse.status)

        val secondResponse = client.post("/wallets/$walletB/payments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", "shared-key")
            setBody("""{"amount":200.00,"occurredAt":"2024-08-26T11:00:00.0000Z"}""")
        }

        assertEquals(HttpStatusCode.Created, secondResponse.status)
        assertNotEquals(firstResponse.bodyAsText(), secondResponse.bodyAsText())
    }

    @Test
    fun `missing Idempotency-Key header returns 400`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        val response = client.post("/wallets/$walletId/payments") {
            contentType(ContentType.Application.Json)
            setBody("""{"amount":100.00,"occurredAt":"2024-08-26T10:00:00.0000Z"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `empty Idempotency-Key header returns 400`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        val response = client.post("/wallets/$walletId/payments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", "")
            setBody("""{"amount":100.00,"occurredAt":"2024-08-26T10:00:00.0000Z"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `Idempotency-Key header exceeding max length returns 400`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        val longKey = "a".repeat(256)
        val response = client.post("/wallets/$walletId/payments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", longKey)
            setBody("""{"amount":100.00,"occurredAt":"2024-08-26T10:00:00.0000Z"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `idempotency does not consume limit twice`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        val idempotencyKey = "no-double-consume"

        val first = client.post("/wallets/$walletId/payments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", idempotencyKey)
            setBody("""{"amount":300.00,"occurredAt":"2024-08-26T10:00:00.0000Z"}""")
        }
        assertEquals(HttpStatusCode.Created, first.status)

        val second = client.post("/wallets/$walletId/payments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", idempotencyKey)
            setBody("""{"amount":300.00,"occurredAt":"2024-08-26T10:00:00.0000Z"}""")
        }
        assertEquals(HttpStatusCode.Created, second.status)

        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT consumed_amount FROM limit_consumptions WHERE wallet_id = ?::uuid",
            ).use { stmt ->
                stmt.setObject(1, UUID.fromString(walletId))
                stmt.executeQuery().use { rs ->
                    rs.next()
                    assertEquals(300.00, rs.getBigDecimal("consumed_amount").toDouble())
                }
            }
        }
    }

    @Test
    fun `validation error does not register idempotency key`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        val idempotencyKey = "validation-no-register"

        client.post("/wallets/$walletId/payments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", idempotencyKey)
            setBody("""{"amount":0,"occurredAt":"2024-08-26T10:00:00.0000Z"}""")
        }

        val retry = client.post("/wallets/$walletId/payments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", idempotencyKey)
            setBody("""{"amount":500.00,"occurredAt":"2024-08-26T11:00:00.0000Z"}""")
        }

        assertEquals(HttpStatusCode.Created, retry.status)
    }

    @Test
    fun `retry after 422 returns same 422`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        val idempotencyKey = "retry-422"

        for (i in 0..3) {
            val hour = 10 + i
            val resp = client.post("/wallets/$walletId/payments") {
                contentType(ContentType.Application.Json)
                header("Idempotency-Key", "pre-consume-$i")
                setBody("""{"amount":1000.00,"occurredAt":"2024-08-26T${hour}:00:00.0000Z"}""")
            }
            assertEquals(HttpStatusCode.Created, resp.status, "Payment ${i + 1} should be approved")
        }

        val first = client.post("/wallets/$walletId/payments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", idempotencyKey)
            setBody("""{"amount":100.00,"occurredAt":"2024-08-26T14:00:00.0000Z"}""")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, first.status)

        val second = client.post("/wallets/$walletId/payments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", idempotencyKey)
            setBody("""{"amount":100.00,"occurredAt":"2024-08-26T14:00:00.0000Z"}""")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, second.status)
    }

    @Test
    fun `concurrent 700+700 with 1000 limit approves only one`() {
        val walletId = createWallet("ConcurrentWallet")
        val httpClient = HttpClient.newHttpClient()

        fun postPayment(key: String, amount: String, time: String): HttpResponse<String> = httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$port/wallets/$walletId/payments"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", key)
                .method("POST", HttpRequest.BodyPublishers.ofString("""{"amount":$amount,"occurredAt":"2024-08-26T$time"}"""))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

        assertEquals(201, postPayment("pre-consume-concurrent-1", "1000.00", "10:00:00.0000Z").statusCode())
        assertEquals(201, postPayment("pre-consume-concurrent-2", "1000.00", "11:00:00.0000Z").statusCode())
        assertEquals(201, postPayment("pre-consume-concurrent-3", "1000.00", "12:00:00.0000Z").statusCode())

        val executor = Executors.newFixedThreadPool(2)

        val futures = listOf(
            executor.submit<HttpResponse<String>> {
                httpClient.send(
                    HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:$port/wallets/$walletId/payments"))
                        .header("Content-Type", "application/json")
                        .header("Idempotency-Key", "concurrent-700-a")
                        .method("POST", HttpRequest.BodyPublishers.ofString("""{"amount":700.00,"occurredAt":"2024-08-26T13:00:00.0000Z"}"""))
                        .build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
            },
            executor.submit<HttpResponse<String>> {
                httpClient.send(
                    HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:$port/wallets/$walletId/payments"))
                        .header("Content-Type", "application/json")
                        .header("Idempotency-Key", "concurrent-700-b")
                        .method("POST", HttpRequest.BodyPublishers.ofString("""{"amount":700.00,"occurredAt":"2024-08-26T13:00:00.0000Z"}"""))
                        .build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
            },
        )

        executor.shutdown()
        executor.awaitTermination(10, TimeUnit.SECONDS)

        val results = futures.map { it.get() }
        val approved = results.count { it.statusCode() == 201 }
        val rejected = results.count { it.statusCode() == 422 }

        assertEquals(1, approved, "Exactly one payment should be approved")
        assertEquals(1, rejected, "Exactly one payment should be rejected")
    }

    @Test
    fun `concurrent payments without previous limit_consumptions row`() {
        val httpClient = HttpClient.newHttpClient()
        val executor = Executors.newFixedThreadPool(2)

        val policyId = httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$port/policies"))
                .header("Content-Type", "application/json")
                .method("POST", HttpRequest.BodyPublishers.ofString(
                    """{"name":"CONCURRENT_TEST","category":"VALUE_LIMIT","maxPerPayment":"5000.00","daytimeDailyLimit":"4000.00","nighttimeDailyLimit":"1000.00","weekendDailyLimit":"1000.00"}"""
                ))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        ).body().let { body ->
            Regex(""""id":"([^"]+)"""").find(body)?.groupValues?.get(1)
                ?: error("Could not parse policy id from: $body")
        }

        val walletId = createWallet("FreshWallet")

        httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$port/wallets/$walletId/policy"))
                .header("Content-Type", "application/json")
                .method("PUT", HttpRequest.BodyPublishers.ofString("""{"policyId":"$policyId"}"""))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

        fun postPayment(key: String, amount: String): HttpResponse<String> = httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$port/wallets/$walletId/payments"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", key)
                .method("POST", HttpRequest.BodyPublishers.ofString("""{"amount":$amount,"occurredAt":"2024-08-26T10:00:00.0000Z"}"""))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

        val futures = listOf(
            executor.submit<HttpResponse<String>> { postPayment("fresh-concurrent-a", "2500.00") },
            executor.submit<HttpResponse<String>> { postPayment("fresh-concurrent-b", "2500.00") },
        )

        executor.shutdown()
        executor.awaitTermination(10, TimeUnit.SECONDS)

        val results = futures.map { it.get() }
        val approved = results.count { it.statusCode() == 201 }
        val rejected = results.count { it.statusCode() == 422 }

        assertEquals(1, approved, "Exactly one concurrent payment should be approved (2500 < 4000)")
        assertEquals(1, rejected, "Exactly one concurrent payment should be rejected (2500+2500 > 4000)")
    }

    @Test
    fun `concurrent idempotency with same key and payload returns same paymentId`() {
        val walletId = createWallet("IdempotentWallet")

        val executor = Executors.newFixedThreadPool(2)
        val httpClient = HttpClient.newHttpClient()

        val futures = listOf(
            executor.submit<java.net.http.HttpResponse<String>> {
                httpClient.send(
                    HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:$port/wallets/$walletId/payments"))
                        .header("Content-Type", "application/json")
                        .header("Idempotency-Key", "concurrent-idem-key")
                        .method("POST",                     HttpRequest.BodyPublishers.ofString("""{"amount":500.00,"occurredAt":"2024-08-26T10:00:00.0000Z"}"""))
                        .build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
            },
            executor.submit<java.net.http.HttpResponse<String>> {
                httpClient.send(
                    HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:$port/wallets/$walletId/payments"))
                        .header("Content-Type", "application/json")
                        .header("Idempotency-Key", "concurrent-idem-key")
                        .method("POST",                     HttpRequest.BodyPublishers.ofString("""{"amount":500.00,"occurredAt":"2024-08-26T10:00:00.0000Z"}"""))
                        .build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
            },
        )

        executor.shutdown()
        executor.awaitTermination(10, TimeUnit.SECONDS)

        val results = futures.map { it.get() }
        assertEquals(2, results.size)
        results.forEach {
            assertEquals(201, it.statusCode(), "Both requests should return 201")
        }
        assertEquals(results[0].body(), results[1].body(), "Both responses should have same body (same paymentId)")
    }

    @Test
    fun `concurrent idempotency with same key and different payload returns 409`() {
        val walletId = createWallet("ConflictWallet")

        val executor = Executors.newFixedThreadPool(2)
        val httpClient = HttpClient.newHttpClient()

        val futures = listOf(
            executor.submit<java.net.http.HttpResponse<String>> {
                httpClient.send(
                    HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:$port/wallets/$walletId/payments"))
                        .header("Content-Type", "application/json")
                        .header("Idempotency-Key", "concurrent-conflict-key")
                        .method("POST",                     HttpRequest.BodyPublishers.ofString("""{"amount":500.00,"occurredAt":"2024-08-26T10:00:00.0000Z"}"""))
                        .build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
            },
            executor.submit<java.net.http.HttpResponse<String>> {
                httpClient.send(
                    HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:$port/wallets/$walletId/payments"))
                        .header("Content-Type", "application/json")
                        .header("Idempotency-Key", "concurrent-conflict-key")
                        .method("POST",                     HttpRequest.BodyPublishers.ofString("""{"amount":600.00,"occurredAt":"2024-08-26T10:00:00.0000Z"}"""))
                        .build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
            },
        )

        executor.shutdown()
        executor.awaitTermination(10, TimeUnit.SECONDS)

        val results = futures.map { it.get() }
        val hasSuccess = results.any { it.statusCode() == 201 }
        val hasConflict = results.any { it.statusCode() == 409 }

        assertTrue(hasSuccess, "One request should succeed")
        assertTrue(hasConflict, "One request should return 409 Conflict")
    }

    @Test
    fun `concurrent payments for different wallets both succeed`() {
        val walletA = createWallet("WalletA")
        val walletB = createWallet("WalletB")

        val executor = Executors.newFixedThreadPool(2)
        val httpClient = HttpClient.newHttpClient()

        val futures = listOf(
            executor.submit<java.net.http.HttpResponse<String>> {
                httpClient.send(
                    HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:$port/wallets/$walletA/payments"))
                        .header("Content-Type", "application/json")
                        .header("Idempotency-Key", "wallet-a-concurrent")
                        .method("POST",                     HttpRequest.BodyPublishers.ofString("""{"amount":1000.00,"occurredAt":"2024-08-26T10:00:00.0000Z"}"""))
                        .build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
            },
            executor.submit<java.net.http.HttpResponse<String>> {
                httpClient.send(
                    HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:$port/wallets/$walletB/payments"))
                        .header("Content-Type", "application/json")
                        .header("Idempotency-Key", "wallet-b-concurrent")
                        .method("POST",                     HttpRequest.BodyPublishers.ofString("""{"amount":1000.00,"occurredAt":"2024-08-26T10:00:00.0000Z"}"""))
                        .build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
            },
        )

        executor.shutdown()
        executor.awaitTermination(10, TimeUnit.SECONDS)

        val results = futures.map { it.get() }
        results.forEachIndexed { index, response ->
            assertEquals(201, response.statusCode(), "Payment for wallet ${if (index == 0) 'A' else 'B'} should succeed")
        }
    }

    @Test
    fun `unique constraint prevents duplicate idempotency key insertion`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        val paymentBody = """{"amount":500.00,"occurredAt":"2024-08-26T10:00:00.0000Z"}"""

        val first = client.post("/wallets/$walletId/payments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", "constraint-test-key")
            setBody(paymentBody)
        }
        assertEquals(HttpStatusCode.Created, first.status)

        val exception = org.junit.jupiter.api.assertThrows<DataAccessException> {
            dsl.insertInto(
                PAYMENT_IDEMPOTENCY_KEYS,
                PAYMENT_IDEMPOTENCY_KEYS.ID,
                PAYMENT_IDEMPOTENCY_KEYS.WALLET_ID,
                PAYMENT_IDEMPOTENCY_KEYS.IDEMPOTENCY_KEY,
                PAYMENT_IDEMPOTENCY_KEYS.REQUEST_HASH,
                PAYMENT_IDEMPOTENCY_KEYS.PAYMENT_ID,
                PAYMENT_IDEMPOTENCY_KEYS.RESPONSE_STATUS,
                PAYMENT_IDEMPOTENCY_KEYS.CREATED_AT,
                PAYMENT_IDEMPOTENCY_KEYS.UPDATED_AT,
            ).values(
                UUID.randomUUID(),
                UUID.fromString(walletId),
                "constraint-test-key",
                "hash",
                null,
                201,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
            ).execute()
        }

        Assertions.assertTrue(
            exception.message?.contains("unique") == true ||
            exception.message?.contains("duplicate") == true ||
            exception.message?.contains("Unique index") == true ||
            exception.message?.contains("already exists") == true,
            "Exception should mention unique constraint violation, got: ${exception.message}",
        )
    }

    @Test
    fun `GET payments lists all approved payments without filter`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        postPayment(client, walletId, 100.0, "2024-08-26T10:00:00.0000Z", "list-all-1")
        postPayment(client, walletId, 200.0, "2024-08-26T11:00:00.0000Z", "list-all-2")
        postPayment(client, walletId, 300.0, "2024-08-26T12:00:00.0000Z", "list-all-3")

        val response = client.get("/wallets/$walletId/payments")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains(""""total":3"""))
        assertTrue(body.contains(""""amount":"100.00""""))
        assertTrue(body.contains(""""amount":"200.00""""))
        assertTrue(body.contains(""""amount":"300.00""""))
        assertTrue(body.contains(""""totalMatches":null"""))
    }

    @Test
    fun `GET payments filters by startDate`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        postPayment(client, walletId, 100.0, "2024-08-25T10:00:00.0000Z", "start-filter-1")
        postPayment(client, walletId, 200.0, "2024-08-26T10:00:00.0000Z", "start-filter-2")
        postPayment(client, walletId, 300.0, "2024-08-27T10:00:00.0000Z", "start-filter-3")

        val response = client.get("/wallets/$walletId/payments") {
            parameter("startDate", "2024-08-26T00:00:00.0000Z")
        }
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains(""""amount":"200.00""""))
        assertTrue(body.contains(""""amount":"300.00""""))
        assertTrue(!body.contains(""""amount":"100.00""""))
    }

    @Test
    fun `GET payments filters by endDate`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        postPayment(client, walletId, 100.0, "2024-08-25T10:00:00.0000Z", "end-filter-1")
        postPayment(client, walletId, 200.0, "2024-08-26T10:00:00.0000Z", "end-filter-2")
        postPayment(client, walletId, 300.0, "2024-08-27T10:00:00.0000Z", "end-filter-3")

        val response = client.get("/wallets/$walletId/payments") {
            parameter("endDate", "2024-08-26T23:59:59.9999Z")
        }
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains(""""amount":"100.00""""))
        assertTrue(body.contains(""""amount":"200.00""""))
        assertTrue(!body.contains(""""amount":"300.00""""))
    }

    @Test
    fun `GET payments filters by startDate and endDate interval`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        postPayment(client, walletId, 100.0, "2024-08-25T10:00:00.0000Z", "interval-1")
        postPayment(client, walletId, 200.0, "2024-08-26T10:00:00.0000Z", "interval-2")
        postPayment(client, walletId, 300.0, "2024-08-27T10:00:00.0000Z", "interval-3")

        val response = client.get("/wallets/$walletId/payments") {
            parameter("startDate", "2024-08-26T00:00:00.0000Z")
            parameter("endDate", "2024-08-26T23:59:59.9999Z")
        }
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains(""""amount":"200.00""""))
        assertTrue(!body.contains(""""amount":"100.00""""))
        assertTrue(!body.contains(""""amount":"300.00""""))
        assertTrue(body.contains(""""total":1"""))
    }

    @Test
    fun `GET payments returns nextCursor when more pages exist`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        repeat(25) { i ->
            val day = "26"
            val minute = i
            val hour = minute / 60
            val min = minute % 60
            postPayment(client, walletId, 10.0, "2024-08-${day}T${"%02d".format(hour)}:${"%02d".format(min)}:00.0000Z", "cursor-test-$i")
        }

        val response = client.get("/wallets/$walletId/payments") {
            parameter("limit", "20")
        }
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains(""""nextCursor":""""), "Response should contain nextCursor")
    }

    @Test
    fun `GET payments uses cursor for next page`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        repeat(25) { i ->
            val day = "26"
            val minute = i
            val hour = minute / 60
            val min = minute % 60
            postPayment(client, walletId, 10.0, "2024-08-${day}T${"%02d".format(hour)}:${"%02d".format(min)}:00.0000Z", "cursor-next-$i")
        }

        val firstResponse = client.get("/wallets/$walletId/payments") {
            parameter("limit", "20")
        }
        val firstBody = firstResponse.bodyAsText()

        val nextCursor = """nextCursor":"([^"]+)""".toRegex().find(firstBody)?.groupValues?.getOrNull(1)
            ?: throw AssertionError("nextCursor should not be null")

        val secondResponse = client.get("/wallets/$walletId/payments") {
            parameter("limit", "20")
            parameter("cursor", nextCursor)
        }
        val secondBody = secondResponse.bodyAsText()

        assertEquals(HttpStatusCode.OK, secondResponse.status)
        assertTrue(secondBody.contains(""""amount":"10.00""""), "Second page should contain payments")
    }

    @Test
    fun `GET payments provides previousCursor for reverse navigation`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        repeat(25) { i ->
            val day = "26"
            val minute = i
            val hour = minute / 60
            val min = minute % 60
            postPayment(client, walletId, 10.0, "2024-08-${day}T${"%02d".format(hour)}:${"%02d".format(min)}:00.0000Z", "prev-cursor-$i")
        }

        val firstResponse = client.get("/wallets/$walletId/payments") {
            parameter("limit", "20")
        }
        val nextCursor = """nextCursor":"([^"]+)""".toRegex().find(firstResponse.bodyAsText())?.groupValues?.getOrNull(1)
            ?: throw AssertionError("nextCursor should not be null")

        val secondResponse = client.get("/wallets/$walletId/payments") {
            parameter("limit", "20")
            parameter("cursor", nextCursor)
        }
        val secondBody = secondResponse.bodyAsText()
        assertEquals(HttpStatusCode.OK, secondResponse.status)

        val previousCursor = """previousCursor":"([^"]+)""".toRegex().find(secondBody)?.groupValues?.getOrNull(1)
            ?: throw AssertionError("previousCursor should not be null for second page")

        val backResponse = client.get("/wallets/$walletId/payments") {
            parameter("cursor", previousCursor)
        }
        assertEquals(HttpStatusCode.OK, backResponse.status)
    }

    @Test
    fun `GET payments maintains stable ordering for same occurredAt`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        postPayment(client, walletId, 100.0, "2024-08-26T10:00:00.0000Z", "same-time-1")
        postPayment(client, walletId, 200.0, "2024-08-26T10:00:00.0000Z", "same-time-2")
        postPayment(client, walletId, 300.0, "2024-08-26T10:00:00.0000Z", "same-time-3")

        val response = client.get("/wallets/$walletId/payments")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        val amountPattern = """amount":"([^"]+)""".toRegex()
        val amounts = amountPattern.findAll(body).map { it.groupValues[1] }.toList()
        assertEquals(3, amounts.size, "Should return 3 payments")
    }

    @Test
    fun `GET payments does not mix payments from different wallets`() = testApplication {
        application { configureTestApplication() }

        val walletA = createWallet("Alice")
        createWallet("Bob")
        postPayment(client, walletA, 100.0, "2024-08-26T10:00:00.0000Z", "no-mix-a")
        postPayment(client, walletA, 200.0, "2024-08-26T10:00:00.0000Z", "no-mix-b")

        val response = client.get("/wallets/$walletA/payments")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains(""""amount":"100.00""""))
        assertTrue(body.contains(""""amount":"200.00""""))
    }

    @Test
    fun `GET payments lists only APPROVED payments`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        postPayment(client, walletId, 100.0, "2024-08-26T10:00:00.0000Z", "only-approved-1")

        val policyId = dsl.select(
            WalletPolicies.WALLET_POLICIES.POLICY_ID,
        ).from(WalletPolicies.WALLET_POLICIES)
            .where(WalletPolicies.WALLET_POLICIES.WALLET_ID.eq(UUID.fromString(walletId)))
            .and(WalletPolicies.WALLET_POLICIES.ACTIVE.eq(true))
            .fetchOne { it.get(WalletPolicies.WALLET_POLICIES.POLICY_ID) }
            ?: throw AssertionError("No active policy found for wallet")

        dsl.insertInto(
            PAYMENTS,
            PAYMENTS.ID, PAYMENTS.WALLET_ID, PAYMENTS.POLICY_ID, PAYMENTS.AMOUNT,
            PAYMENTS.OCCURRED_AT, PAYMENTS.PERIOD_TYPE, PAYMENTS.PERIOD_START,
            PAYMENTS.STATUS, PAYMENTS.CREATED_AT, PAYMENTS.UPDATED_AT,
        ).values(
            UUID.randomUUID(), UUID.fromString(walletId),
            policyId,
            BigDecimal("50.00"),
            OffsetDateTime.parse("2024-08-26T11:00:00.0000Z"),
            "DAYTIME", OffsetDateTime.parse("2024-08-26T06:00:00.0000Z"),
            "REJECTED", OffsetDateTime.now(), OffsetDateTime.now(),
        ).execute()

        val response = client.get("/wallets/$walletId/payments")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains(""""total":1"""), "Should count only APPROVED payments")
        assertTrue(body.contains(""""amount":"100.00""""), "Should list only the APPROVED payment")
        assertTrue(!body.contains(""""amount":"50.00""""), "Should NOT list the REJECTED payment")
    }

    @Test
    fun `GET payments with non-existent wallet returns 404`() = testApplication {
        application { configureTestApplication() }

        val response = client.get("/wallets/${UUID.randomUUID()}/payments")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET payments with invalid startDate returns 400`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        val response = client.get("/wallets/$walletId/payments") {
            parameter("startDate", "invalid-date")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET payments with invalid endDate returns 400`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        val response = client.get("/wallets/$walletId/payments") {
            parameter("endDate", "not-a-date")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET payments with startDate after endDate returns 400`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        val response = client.get("/wallets/$walletId/payments") {
            parameter("startDate", "2024-08-27T00:00:00.0000Z")
            parameter("endDate", "2024-08-26T00:00:00.0000Z")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET payments with invalid cursor returns 400`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        val response = client.get("/wallets/$walletId/payments") {
            parameter("cursor", "not-a-valid-cursor")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET payments with zero limit returns 400`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        val response = client.get("/wallets/$walletId/payments") {
            parameter("limit", "0")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET payments with limit above max returns 400`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        val response = client.get("/wallets/$walletId/payments") {
            parameter("limit", "200")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET payments meta contains expected fields`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        postPayment(client, walletId, 100.0, "2024-08-26T10:00:00.0000Z", "meta-fields")

        val response = client.get("/wallets/$walletId/payments")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains(""""nextCursor"""))
        assertTrue(body.contains(""""previousCursor"""))
        assertTrue(body.contains(""""total"""))
        assertTrue(body.contains(""""totalMatches":null"""))
    }

    @Test
    fun `POST payments with TX_COUNT_LIMIT approves up to daily limit`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        val policyId = createTxCountPolicy("DAILY_TX_LIMIT", 3)
        assignPolicy(walletId, policyId)

        val baseTime = "2024-08-26T10:00:00.0000Z"
        postPayment(client, walletId, 10.0, baseTime, "tx-1")
        postPayment(client, walletId, 20.0, baseTime, "tx-2")
        postPayment(client, walletId, 30.0, baseTime, "tx-3")

        val fourth = client.post("/wallets/$walletId/payments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", "tx-4")
            setBody("""{"amount":40.0,"occurredAt":"$baseTime"}""")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, fourth.status)
    }

    @Test
    fun `POST payments with TX_COUNT_LIMIT resets next day`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        val policyId = createTxCountPolicy("DAILY_TX_LIMIT", 2)
        assignPolicy(walletId, policyId)

        postPayment(client, walletId, 10.0, "2024-08-26T10:00:00.0000Z", "tx-day1-a")
        postPayment(client, walletId, 20.0, "2024-08-26T14:00:00.0000Z", "tx-day1-b")

        val nextDay = client.post("/wallets/$walletId/payments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", "tx-day2-a")
            setBody("""{"amount":30.0,"occurredAt":"2024-08-27T10:00:00.0000Z"}""")
        }
        assertEquals(HttpStatusCode.Created, nextDay.status)
    }

    @Test
    fun `POST payments with TX_COUNT_LIMIT does not depend on amount`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        val policyId = createTxCountPolicy("DAILY_TX_LIMIT", 2)
        assignPolicy(walletId, policyId)

        val largeAmount = client.post("/wallets/$walletId/payments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", "tx-large")
            setBody("""{"amount":999999.99,"occurredAt":"2024-08-26T10:00:00.0000Z"}""")
        }
        assertEquals(HttpStatusCode.Created, largeAmount.status)
    }

    @Test
    fun `POST payments with TX_COUNT_LIMIT concurrent requests do not exceed limit`() {
        val httpClient = HttpClient.newHttpClient()
        val executor = Executors.newFixedThreadPool(3)

        val policyId = httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$port/policies"))
                .header("Content-Type", "application/json")
                .method("POST", HttpRequest.BodyPublishers.ofString(
                    """{"name":"DAILY_TX_LIMIT","category":"TX_COUNT_LIMIT","dailyTransactionLimit":2}""",
                ))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        ).body().let { body ->
            Regex(""""id":"([^"]+)""").find(body)?.groupValues?.get(1)
                ?: error("Could not parse policy id from: $body")
        }

        val walletId = createWallet("TxCountWallet")

        httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$port/wallets/$walletId/policy"))
                .header("Content-Type", "application/json")
                .method("PUT", HttpRequest.BodyPublishers.ofString("""{"policyId":"$policyId"}"""))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

        fun postPayment(key: String): HttpResponse<String> = httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$port/wallets/$walletId/payments"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", key)
                .method("POST", HttpRequest.BodyPublishers.ofString("""{"amount":10.0,"occurredAt":"2024-08-26T10:00:00.0000Z"}"""))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

        val futures = listOf(
            executor.submit<HttpResponse<String>> { postPayment("tx-concurrent-a") },
            executor.submit<HttpResponse<String>> { postPayment("tx-concurrent-b") },
            executor.submit<HttpResponse<String>> { postPayment("tx-concurrent-c") },
        )

        executor.shutdown()
        executor.awaitTermination(10, TimeUnit.SECONDS)

        val results = futures.map { it.get() }
        val approved = results.count { it.statusCode() == 201 }
        val rejected = results.count { it.statusCode() == 422 }

        assertEquals(2, approved, "Expected exactly 2 approved, got $approved")
        assertEquals(1, rejected, "Expected exactly 1 rejected, got $rejected")
    }

    private fun Application.configureTestApplication() {
        configureApplication(dsl)
    }

    private fun createWallet(ownerName: String): String {
        val outboxGateway = OutboxGatewayImpl(dsl)
        val transactionManager = JooqTransactionManager(dsl)
        val walletDAO = WalletDAOSpecImpl(dsl)
        val useCase = CreateWalletUseCaseSpecImpl(walletDAO, outboxGateway, transactionManager)
        val wallet = useCase.execute(ownerName)
        return wallet.id.toString()
    }

    private fun createTxCountPolicy(name: String, limit: Int): String {
        val outboxGateway = OutboxGatewayImpl(dsl)
        val transactionManager = JooqTransactionManager(dsl)
        val policyDAO = PolicyDAOSpecImpl(dsl)
        val useCase = CreatePolicyUseCaseImpl(policyDAO, outboxGateway, transactionManager)
        val policy = useCase.execute(
            name = name,
            category = "TX_COUNT_LIMIT",
            maxPerPayment = null,
            daytimeDailyLimit = null,
            nighttimeDailyLimit = null,
            weekendDailyLimit = null,
            dailyTransactionLimit = limit,
        )
        return policy.id.toString()
    }

    private fun assignPolicy(walletId: String, policyId: String) {
        val outboxGateway = OutboxGatewayImpl(dsl)
        val transactionManager = JooqTransactionManager(dsl)
        val policyDAO = PolicyDAOSpecImpl(dsl)
        val walletDAO = WalletDAOSpecImpl(dsl)
        val useCase = AssignPolicyUseCaseImpl(policyDAO, walletDAO, outboxGateway, transactionManager)
        useCase.execute(UUID.fromString(walletId), UUID.fromString(policyId))
    }

    companion object {
        @Container
        @JvmField
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")

        var port: Int = 0
            private set

        private var server: ApplicationEngine? = null
        private var serverDataSource: DataSource? = null

        @BeforeAll
        @JvmStatic
        fun startServer() {
            val dataSource = DatabaseFactory.create(
                DatabaseConfigBO(
                    url = postgres.jdbcUrl,
                    username = postgres.username,
                    password = postgres.password,
                ),
            )
            serverDataSource = dataSource
            val dsl = JooqFactory.create(dataSource)
            port = ServerSocket(0).use { it.localPort }
            server = embeddedServer(Netty, port = port) {
                configureApplication(dsl)
            }.start(wait = false)
        }

        @AfterAll
        @JvmStatic
        fun stopServer() {
            server?.stop(1000, 2000)
            (serverDataSource as? AutoCloseable)?.close()
        }
    }
}

private fun Application.configureApplication(dsl: DSLContext) {
    val outboxGateway = OutboxGatewayImpl(dsl)
    val transactionManager = JooqTransactionManager(dsl)
    val walletDAO = WalletDAOSpecImpl(dsl)
    val policyDAO = PolicyDAOSpecImpl(dsl)
    val paymentGateway = PaymentGatewayImpl(dsl)

    val policyResolver = PolicyResolverImpl(policyDAO)
    val policyRegistry = PolicyEvaluatorRegistryImpl().apply {
        register("VALUE_LIMIT", ValueLimitEvaluator())
        register("TX_COUNT_LIMIT", TxCountLimitEvaluator())
    }

    val createWalletUseCase = CreateWalletUseCaseSpecImpl(walletDAO, outboxGateway, transactionManager)
    val createPolicyUseCase = CreatePolicyUseCaseImpl(policyDAO, outboxGateway, transactionManager)
    val listPoliciesUseCase = ListPoliciesUseCaseImpl(policyDAO)
    val listWalletPoliciesUseCase = ListWalletPoliciesUseCaseImpl(policyDAO, walletDAO)
    val assignPolicyUseCase = AssignPolicyUseCaseImpl(policyDAO, walletDAO, outboxGateway, transactionManager)
    val processPaymentUseCase = ProcessPaymentUseCaseImpl(walletDAO, policyResolver, policyRegistry, paymentGateway, outboxGateway, transactionManager)
    val listPaymentsUseCase = ListPaymentsUseCaseImpl(walletDAO, paymentGateway)

    configureSerialization()
    configureErrorHandling()
    configureWalletRoutes(createWalletUseCase)
    configurePolicyRoutes(
        createPolicyUseCase = createPolicyUseCase,
        listPoliciesUseCase = listPoliciesUseCase,
        listWalletPoliciesUseCase = listWalletPoliciesUseCase,
        assignPolicyUseCase = assignPolicyUseCase,
    )
    configurePaymentRoutes(processPaymentUseCase, listPaymentsUseCase)
}

private suspend fun postPayment(
    client: KtorHttpClient,
    walletId: String,
    amount: Double,
    occurredAt: String,
    idempotencyKey: String,
) {
    val body = """{"amount":$amount,"occurredAt":"$occurredAt"}"""
    val response = client.post("/wallets/$walletId/payments") {
        contentType(ContentType.Application.Json)
        header("Idempotency-Key", idempotencyKey)
        setBody(body)
    }
    kotlin.test.assertEquals(
        HttpStatusCode.Created,
        response.status,
        "Failed to create payment: $body, status=${response.status}",
    )
}
