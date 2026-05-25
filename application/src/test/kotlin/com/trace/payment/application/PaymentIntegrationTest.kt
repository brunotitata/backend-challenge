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
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.testing.*
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
                statement.execute("TRUNCATE TABLE wallet_policies, payments, limit_consumptions, policies, wallets RESTART IDENTITY CASCADE")
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
                setBody("""{"amount":1000.00,"occurredAt":"2024-08-26T${hour}:00:00.0000Z"}""")
            }
            assertEquals(HttpStatusCode.Created, resp.status, "Payment ${i + 1} should be approved")
        }

        val response = client.post("/wallets/$walletId/payments") {
            contentType(ContentType.Application.Json)
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
            setBody("""{"amount":4000.00,"occurredAt":"2024-08-26T10:00:00.0000Z"}""")
        }

        val response = client.post("/wallets/$walletB/payments") {
            contentType(ContentType.Application.Json)
            setBody("""{"amount":500.00,"occurredAt":"2024-08-26T10:00:00.0000Z"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
    }

    private fun Application.configureTestApplication() {
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
    }
}
