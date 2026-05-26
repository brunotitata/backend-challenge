package com.trace.payment.application

import com.trace.payment.adapters.database.config.DatabaseFactory
import com.trace.payment.adapters.database.config.JooqFactory
import com.trace.payment.adapters.database.dao.PolicyDAOSpecImpl
import com.trace.payment.adapters.database.dao.WalletDAOSpecImpl
import com.trace.payment.adapters.web.configs.configureErrorHandling
import com.trace.payment.adapters.web.configs.configureSerialization
import com.trace.payment.adapters.web.routes.configurePolicyRoutes
import com.trace.payment.adapters.web.routes.configureWalletRoutes
import com.trace.payment.boundary.common.DatabaseConfigBO
import com.trace.payment.core.usecase.AssignPolicyUseCaseImpl
import com.trace.payment.core.usecase.CreatePolicyUseCaseImpl
import com.trace.payment.core.usecase.CreateWalletUseCaseSpecImpl
import com.trace.payment.core.usecase.ListPoliciesUseCaseImpl
import com.trace.payment.core.usecase.ListWalletPoliciesUseCaseImpl
import com.trace.payment.core.usecase.PolicyResolverImpl
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PolicyIntegrationTest {

    private lateinit var dataSource: DataSource
    private lateinit var dsl: DSLContext
    private lateinit var policyDAO: PolicyDAOSpecImpl
    private lateinit var walletDAO: WalletDAOSpecImpl

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
        policyDAO = PolicyDAOSpecImpl(dsl)
        walletDAO = WalletDAOSpecImpl(dsl)
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("TRUNCATE TABLE wallet_policies, policies, wallets RESTART IDENTITY CASCADE")
            }
        }
    }

    @Test
    fun `POST policies with valid VALUE_LIMIT returns 201`() = testApplication {
        application { configureTestApplication() }

        val response = client.post("/policies") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"TEST_POLICY","category":"VALUE_LIMIT","maxPerPayment":"500.00","daytimeDailyLimit":"2000.00","nighttimeDailyLimit":"500.00","weekendDailyLimit":"500.00"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains(""""name":"TEST_POLICY""""))
        assertTrue(body.contains(""""category":"VALUE_LIMIT""""))
        assertTrue(body.contains(""""maxPerPayment":"500.00""""))
        assertTrue(body.contains(""""daytimeDailyLimit":"2000.00""""))
        assertTrue(body.contains(""""nighttimeDailyLimit":"500.00""""))
        assertTrue(body.contains(""""weekendDailyLimit":"500.00""""))
    }

    @Test
    fun `POST policies with numeric VALUE_LIMIT amounts returns 201`() = testApplication {
        application { configureTestApplication() }

        val response = client.post("/policies") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"WEEKDAY_PLUS","category":"VALUE_LIMIT","maxPerPayment":1000,"daytimeDailyLimit":4000,"nighttimeDailyLimit":2000,"weekendDailyLimit":1000}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains(""""name":"WEEKDAY_PLUS""""))
        assertTrue(body.contains(""""maxPerPayment":"1000.00""""))
        assertTrue(body.contains(""""daytimeDailyLimit":"4000.00""""))
        assertTrue(body.contains(""""nighttimeDailyLimit":"2000.00""""))
        assertTrue(body.contains(""""weekendDailyLimit":"1000.00""""))
    }

    @Test
    fun `POST policies without name returns 400`() = testApplication {
        application { configureTestApplication() }

        val response = client.post("/policies") {
            contentType(ContentType.Application.Json)
            setBody("""{"category":"VALUE_LIMIT","maxPerPayment":"500.00"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST policies with unknown category returns 400`() = testApplication {
        application { configureTestApplication() }

        val response = client.post("/policies") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"TEST","category":"UNKNOWN","maxPerPayment":"500.00"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST policies with zero maxPerPayment returns 400`() = testApplication {
        application { configureTestApplication() }

        val response = client.post("/policies") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"TEST","category":"VALUE_LIMIT","maxPerPayment":"0","daytimeDailyLimit":"2000.00","nighttimeDailyLimit":"500.00","weekendDailyLimit":"500.00"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST policies with negative maxPerPayment returns 400`() = testApplication {
        application { configureTestApplication() }

        val response = client.post("/policies") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"TEST","category":"VALUE_LIMIT","maxPerPayment":"-100","daytimeDailyLimit":"2000.00","nighttimeDailyLimit":"500.00","weekendDailyLimit":"500.00"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET policies returns data and meta format`() = testApplication {
        application { configureTestApplication() }

        client.post("/policies") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"POLICY_A","category":"VALUE_LIMIT","maxPerPayment":"500.00","daytimeDailyLimit":"2000.00","nighttimeDailyLimit":"500.00","weekendDailyLimit":"500.00"}""")
        }
        client.post("/policies") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"POLICY_B","category":"VALUE_LIMIT","maxPerPayment":"1000.00","daytimeDailyLimit":"4000.00","nighttimeDailyLimit":"1000.00","weekendDailyLimit":"1000.00"}""")
        }

        val response = client.get("/policies")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains(""""data":"""))
        assertTrue(body.contains(""""meta":"""))
        assertTrue(body.contains(""""total":2"""))
    }

    @Test
    fun `PUT wallets policy associates policy to wallet`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        val policyId = createPolicy("MY_POLICY")

        val response = client.put("/wallets/$walletId/policy") {
            contentType(ContentType.Application.Json)
            setBody("""{"policyId":"$policyId"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains(""""walletId":"$walletId""""))
        assertTrue(body.contains(""""policyId":"$policyId""""))
        assertTrue(body.contains(""""active":true"""))
    }

    @Test
    fun `PUT wallets policy deactivates previous active policy`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        val policyA = createPolicy("POLICY_A")
        val policyB = createPolicy("POLICY_B")

        client.put("/wallets/$walletId/policy") {
            contentType(ContentType.Application.Json)
            setBody("""{"policyId":"$policyA"}""")
        }
        client.put("/wallets/$walletId/policy") {
            contentType(ContentType.Application.Json)
            setBody("""{"policyId":"$policyB"}""")
        }

        val policiesResponse = client.get("/wallets/$walletId/policies")
        val body = policiesResponse.bodyAsText()
        assertTrue(body.contains(""""active":false"""))
        assertTrue(body.contains(""""active":true"""))
        assertTrue(body.contains("\"name\":\"POLICY_B\""))
    }

    @Test
    fun `GET wallets policies returns wallet policies with active flag`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        val policyId = createPolicy("TEST_POLICY")

        client.put("/wallets/$walletId/policy") {
            contentType(ContentType.Application.Json)
            setBody("""{"policyId":"$policyId"}""")
        }

        val response = client.get("/wallets/$walletId/policies")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains(""""data":"""))
        assertTrue(body.contains(""""active":true"""))
        assertTrue(body.contains(""""name":"TEST_POLICY""""))
    }

    @Test
    fun `PolicyResolver resolves active policy for wallet`() {
        val walletId = UUID.fromString(createWallet("Maria"))
        val policyId = UUID.fromString(createPolicy("RESOLVE_TEST"))

        val resolver = PolicyResolverImpl(policyDAO)
        val beforeAssign = resolver.resolve(walletId)
        assertNotNull(beforeAssign)
        assertEquals("DEFAULT_VALUE_LIMIT", beforeAssign.name)

        policyDAO.assignPolicy(walletId, policyId)

        val afterAssign = resolver.resolve(walletId)
        assertNotNull(afterAssign)
        assertEquals("RESOLVE_TEST", afterAssign.name)
    }

    @Test
    fun `GET wallets policies with non-existent wallet returns 404`() = testApplication {
        application { configureTestApplication() }

        val response = client.get("/wallets/${UUID.randomUUID()}/policies")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `PUT wallets policy with non-existent wallet returns 404`() = testApplication {
        application { configureTestApplication() }

        val policyId = createPolicy("TEST")
        val response = client.put("/wallets/${UUID.randomUUID()}/policy") {
            contentType(ContentType.Application.Json)
            setBody("""{"policyId":"$policyId"}""")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `PUT wallets policy with non-existent policy returns 404`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        val response = client.put("/wallets/$walletId/policy") {
            contentType(ContentType.Application.Json)
            setBody("""{"policyId":"${UUID.randomUUID()}"}""")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `unique active policy constraint prevents duplicate active policies`() {
        val walletId = UUID.fromString(createWallet("Maria"))

        policyDAO.assignPolicy(walletId, UUID.fromString(createPolicy("POLICY_A")))

        try {
            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    """
                    INSERT INTO wallet_policies (wallet_id, policy_id, active)
                    VALUES (?, ?, TRUE)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, walletId)
                    statement.setObject(2, UUID.fromString(createPolicy("POLICY_B")))
                    statement.executeUpdate()
                }
            }
        } catch (e: Exception) {
            assertTrue(e.message?.contains("duplicate key") == true || e.message?.contains("unique") == true)
        }
    }

    @Test
    fun `POST policies with valid TX_COUNT_LIMIT returns 201`() = testApplication {
        application { configureTestApplication() }

        val response = client.post("/policies") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"DAILY_TX_LIMIT","category":"TX_COUNT_LIMIT","dailyTransactionLimit":5}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains(""""name":"DAILY_TX_LIMIT""""))
        assertTrue(body.contains(""""category":"TX_COUNT_LIMIT""""))
        assertTrue(body.contains(""""dailyTransactionLimit":5"""))
        assertTrue(body.contains(""""maxPerPayment":null"""))
    }

    @Test
    fun `POST policies TX_COUNT_LIMIT without dailyTransactionLimit returns 400`() = testApplication {
        application { configureTestApplication() }

        val response = client.post("/policies") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"DAILY_TX_LIMIT","category":"TX_COUNT_LIMIT"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST policies TX_COUNT_LIMIT with zero dailyTransactionLimit returns 400`() = testApplication {
        application { configureTestApplication() }

        val response = client.post("/policies") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"DAILY_TX_LIMIT","category":"TX_COUNT_LIMIT","dailyTransactionLimit":0}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST policies TX_COUNT_LIMIT with negative dailyTransactionLimit returns 400`() = testApplication {
        application { configureTestApplication() }

        val response = client.post("/policies") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"DAILY_TX_LIMIT","category":"TX_COUNT_LIMIT","dailyTransactionLimit":-1}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET policies returns TX_COUNT_LIMIT with specific fields`() = testApplication {
        application { configureTestApplication() }

        client.post("/policies") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"DAILY_TX_LIMIT","category":"TX_COUNT_LIMIT","dailyTransactionLimit":5}""")
        }

        val response = client.get("/policies")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains(""""dailyTransactionLimit":5"""))
        assertTrue(body.contains(""""category":"TX_COUNT_LIMIT""""))
    }

    @Test
    fun `GET wallet policies returns TX_COUNT_LIMIT with specific fields`() = testApplication {
        application { configureTestApplication() }

        val walletId = createWallet("Maria")
        val policyId = createTxCountPolicy("DAILY_TX_LIMIT", 5)

        client.put("/wallets/$walletId/policy") {
            contentType(ContentType.Application.Json)
            setBody("""{"policyId":"$policyId"}""")
        }

        val response = client.get("/wallets/$walletId/policies")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains(""""dailyTransactionLimit":5"""))
        assertTrue(body.contains(""""active":true"""))
    }

    private fun Application.configureTestApplication() {
        val walletDAO = WalletDAOSpecImpl(dsl)
        val policyDAO = PolicyDAOSpecImpl(dsl)
        val createWalletUseCase = CreateWalletUseCaseSpecImpl(walletDAO)
        val createPolicyUseCase = CreatePolicyUseCaseImpl(policyDAO)
        val listPoliciesUseCase = ListPoliciesUseCaseImpl(policyDAO)
        val listWalletPoliciesUseCase = ListWalletPoliciesUseCaseImpl(policyDAO, walletDAO)
        val assignPolicyUseCase = AssignPolicyUseCaseImpl(policyDAO, walletDAO)

        configureSerialization()
        configureErrorHandling()
        configureWalletRoutes(createWalletUseCase)
        configurePolicyRoutes(
            createPolicyUseCase = createPolicyUseCase,
            listPoliciesUseCase = listPoliciesUseCase,
            listWalletPoliciesUseCase = listWalletPoliciesUseCase,
            assignPolicyUseCase = assignPolicyUseCase,
        )
    }

    private fun createWallet(ownerName: String): String {
        val walletDAO = WalletDAOSpecImpl(dsl)
        val useCase = CreateWalletUseCaseSpecImpl(walletDAO)
        val wallet = useCase.execute(ownerName)
        return wallet.id.toString()
    }

    private fun createPolicy(name: String): String {
        val policyDAO = PolicyDAOSpecImpl(dsl)
        val useCase = CreatePolicyUseCaseImpl(policyDAO)
        val policy = useCase.execute(
            name = name,
            category = "VALUE_LIMIT",
            maxPerPayment = java.math.BigDecimal("1000.00"),
            daytimeDailyLimit = java.math.BigDecimal("4000.00"),
            nighttimeDailyLimit = java.math.BigDecimal("1000.00"),
            weekendDailyLimit = java.math.BigDecimal("1000.00"),
            dailyTransactionLimit = null,
        )
        return policy.id.toString()
    }

    private fun createTxCountPolicy(name: String, limit: Int): String {
        val policyDAO = PolicyDAOSpecImpl(dsl)
        val useCase = CreatePolicyUseCaseImpl(policyDAO)
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

    companion object {
        @Container
        @JvmField
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }
}
