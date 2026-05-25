package com.trace.payment.application

import com.trace.payment.adapters.database.config.DatabaseFactory
import com.trace.payment.adapters.database.config.JooqFactory
import com.trace.payment.adapters.database.dao.PolicyDAOSpecImpl
import com.trace.payment.adapters.database.dao.WalletDAOSpecImpl
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
import com.trace.payment.core.usecase.ListPoliciesUseCaseImpl
import com.trace.payment.core.usecase.ListWalletPoliciesUseCaseImpl
import com.trace.payment.core.usecase.PolicyEvaluatorRegistryImpl
import com.trace.payment.core.usecase.PolicyResolverImpl
import com.trace.payment.core.usecase.ProcessPaymentUseCaseImpl
import com.trace.payment.core.usecase.ValueLimitEvaluator
import com.trace.payment.adapters.database.jooq.tables.PaymentIdempotencyKeys.PAYMENT_IDEMPOTENCY_KEYS
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.testing.*
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
                .uri(java.net.URI.create("http://localhost:$port/wallets/$walletId/payments"))
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
                        .uri(java.net.URI.create("http://localhost:$port/wallets/$walletId/payments"))
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
                        .uri(java.net.URI.create("http://localhost:$port/wallets/$walletId/payments"))
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
                .uri(java.net.URI.create("http://localhost:$port/policies"))
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
                .uri(java.net.URI.create("http://localhost:$port/wallets/$walletId/policy"))
                .header("Content-Type", "application/json")
                .method("PUT", HttpRequest.BodyPublishers.ofString("""{"policyId":"$policyId"}"""))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

        fun postPayment(key: String, amount: String): HttpResponse<String> = httpClient.send(
            HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:$port/wallets/$walletId/payments"))
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
                    java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create("http://localhost:$port/wallets/$walletId/payments"))
                        .header("Content-Type", "application/json")
                        .header("Idempotency-Key", "concurrent-idem-key")
                        .method("POST",                     HttpRequest.BodyPublishers.ofString("""{"amount":500.00,"occurredAt":"2024-08-26T10:00:00.0000Z"}"""))
                        .build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
            },
            executor.submit<java.net.http.HttpResponse<String>> {
                httpClient.send(
                    java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create("http://localhost:$port/wallets/$walletId/payments"))
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
                    java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create("http://localhost:$port/wallets/$walletId/payments"))
                        .header("Content-Type", "application/json")
                        .header("Idempotency-Key", "concurrent-conflict-key")
                        .method("POST",                     HttpRequest.BodyPublishers.ofString("""{"amount":500.00,"occurredAt":"2024-08-26T10:00:00.0000Z"}"""))
                        .build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
            },
            executor.submit<java.net.http.HttpResponse<String>> {
                httpClient.send(
                    java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create("http://localhost:$port/wallets/$walletId/payments"))
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
                    java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create("http://localhost:$port/wallets/$walletA/payments"))
                        .header("Content-Type", "application/json")
                        .header("Idempotency-Key", "wallet-a-concurrent")
                        .method("POST",                     HttpRequest.BodyPublishers.ofString("""{"amount":1000.00,"occurredAt":"2024-08-26T10:00:00.0000Z"}"""))
                        .build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
            },
            executor.submit<java.net.http.HttpResponse<String>> {
                httpClient.send(
                    java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create("http://localhost:$port/wallets/$walletB/payments"))
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
                java.util.UUID.randomUUID(),
                java.util.UUID.fromString(walletId),
                "constraint-test-key",
                "hash",
                null,
                201,
                java.time.OffsetDateTime.now(),
                java.time.OffsetDateTime.now(),
            ).execute()
        }

        org.junit.jupiter.api.Assertions.assertTrue(
            exception.message?.contains("unique") == true ||
            exception.message?.contains("duplicate") == true ||
            exception.message?.contains("Unique index") == true ||
            exception.message?.contains("already exists") == true,
            "Exception should mention unique constraint violation, got: ${exception.message}",
        )
    }

    private fun Application.configureTestApplication() {
        configureApplication(dsl)
    }

    private fun createWallet(ownerName: String): String {
        val walletDAO = WalletDAOSpecImpl(dsl)
        val useCase = CreateWalletUseCaseSpecImpl(walletDAO)
        val wallet = useCase.execute(ownerName)
        return wallet.id.toString()
    }

    companion object {
        @Container
        @JvmField
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")

        var port: Int = 0
            private set

        private var server: ApplicationEngine? = null

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
            val dsl = JooqFactory.create(dataSource)
            port = java.net.ServerSocket(0).use { it.localPort }
            server = embeddedServer(Netty, port = port) {
                configureApplication(dsl)
            }.start(wait = false)
        }

        @AfterAll
        @JvmStatic
        fun stopServer() {
            server?.stop(1000, 2000)
        }
    }
}

private fun Application.configureApplication(dsl: DSLContext) {
    val walletDAO = WalletDAOSpecImpl(dsl)
    val policyDAO = PolicyDAOSpecImpl(dsl)
    val paymentGateway = PaymentGatewayImpl(dsl)

    val policyResolver = PolicyResolverImpl(policyDAO)
    val policyRegistry = PolicyEvaluatorRegistryImpl().apply {
        register("VALUE_LIMIT", ValueLimitEvaluator())
    }

    val createWalletUseCase = CreateWalletUseCaseSpecImpl(walletDAO)
    val createPolicyUseCase = CreatePolicyUseCaseImpl(policyDAO)
    val listPoliciesUseCase = ListPoliciesUseCaseImpl(policyDAO)
    val listWalletPoliciesUseCase = ListWalletPoliciesUseCaseImpl(policyDAO, walletDAO)
    val assignPolicyUseCase = AssignPolicyUseCaseImpl(policyDAO, walletDAO)
    val processPaymentUseCase = ProcessPaymentUseCaseImpl(walletDAO, policyResolver, policyRegistry, paymentGateway)

    configureSerialization()
    configureErrorHandling()
    configureWalletRoutes(createWalletUseCase)
    configurePolicyRoutes(
        createPolicyUseCase = createPolicyUseCase,
        listPoliciesUseCase = listPoliciesUseCase,
        listWalletPoliciesUseCase = listWalletPoliciesUseCase,
        assignPolicyUseCase = assignPolicyUseCase,
    )
    configurePaymentRoutes(processPaymentUseCase)
}
