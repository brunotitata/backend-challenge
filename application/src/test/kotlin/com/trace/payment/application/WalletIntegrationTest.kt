package com.trace.payment.application

import com.trace.payment.adapters.database.config.DatabaseFactory
import com.trace.payment.adapters.database.config.JooqFactory
import com.trace.payment.adapters.database.dao.WalletDAOSpecImpl
import com.trace.payment.adapters.web.configs.configureErrorHandling
import com.trace.payment.adapters.web.configs.configureSerialization
import com.trace.payment.adapters.web.routes.configureWalletRoutes
import com.trace.payment.boundary.common.DatabaseConfigBO
import com.trace.payment.core.usecase.CreateWalletUseCaseSpecImpl
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
import java.time.Instant
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WalletIntegrationTest {

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
                statement.execute("TRUNCATE TABLE wallet_policies, wallets RESTART IDENTITY CASCADE")
            }
        }
    }

    @Test
    fun `POST wallets with valid ownerName returns 201 with wallet data`() = testApplication {
        application { configureTestWalletApplication() }

        val response = client.post("/wallets") {
            contentType(ContentType.Application.Json)
            setBody("""{"ownerName": "Maria Silva"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains(""""ownerName":"Maria Silva""""))
        assertTrue(body.contains(""""id":"""))
        assertTrue(body.contains(""""createdAt":"""))
        val createdAt = body.jsonString("createdAt")
        Instant.parse(createdAt)
    }

    @Test
    fun `POST wallets persists wallet in database`() = testApplication {
        application { configureTestWalletApplication() }

        val response = client.post("/wallets") {
            contentType(ContentType.Application.Json)
            setBody("""{"ownerName": "Maria Silva"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.bodyAsText()

        val id = body.jsonString("id")
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT COUNT(*) FROM wallets WHERE id = ?::uuid AND owner_name = ?").use { stmt ->
                stmt.setString(1, id)
                stmt.setString(2, "Maria Silva")
                stmt.executeQuery().use { rs ->
                    rs.next()
                    assertEquals(1, rs.getInt(1))
                }
            }
        }
    }

    @Test
    fun `POST wallets with missing ownerName returns 400`() = testApplication {
        application { configureTestWalletApplication() }

        val response = client.post("/wallets") {
            contentType(ContentType.Application.Json)
            setBody("""{}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST wallets with empty ownerName returns 400`() = testApplication {
        application { configureTestWalletApplication() }

        val response = client.post("/wallets") {
            contentType(ContentType.Application.Json)
            setBody("""{"ownerName": ""}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST wallets with blank ownerName returns 400`() = testApplication {
        application { configureTestWalletApplication() }

        val response = client.post("/wallets") {
            contentType(ContentType.Application.Json)
            setBody("""{"ownerName": "   "}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST wallets with null ownerName returns 400`() = testApplication {
        application { configureTestWalletApplication() }

        val response = client.post("/wallets") {
            contentType(ContentType.Application.Json)
            setBody("""{"ownerName": null}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST wallets without body returns 400`() = testApplication {
        application { configureTestWalletApplication() }

        val response = client.post("/wallets") {
            contentType(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST wallets associates default active policy`() = testApplication {
        application { configureTestWalletApplication() }

        val response = client.post("/wallets") {
            contentType(ContentType.Application.Json)
            setBody("""{"ownerName": "Maria Silva"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val walletId = response.bodyAsText().jsonString("id")

        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT p.name, p.category, wp.active
                FROM wallet_policies wp
                JOIN policies p ON p.id = wp.policy_id
                WHERE wp.wallet_id = ?::uuid
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, walletId)
                statement.executeQuery().use { resultSet ->
                    assertTrue(resultSet.next())
                    assertEquals("DEFAULT_VALUE_LIMIT", resultSet.getString("name"))
                    assertEquals("VALUE_LIMIT", resultSet.getString("category"))
                    assertTrue(resultSet.getBoolean("active"))
                }
            }
        }
    }

    @Test
    fun `database migration creates wallet policy indexes`() {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT indexname
                FROM pg_indexes
                WHERE tablename = 'wallet_policies'
                AND indexname IN ('idx_wallet_policies_wallet_id', 'idx_wallet_policies_policy_id')
                """.trimIndent(),
            ).use { statement ->
                statement.executeQuery().use { resultSet ->
                    val indexNames = mutableSetOf<String>()
                    while (resultSet.next()) {
                        indexNames += resultSet.getString("indexname")
                    }

                    assertTrue(indexNames.contains("idx_wallet_policies_wallet_id"))
                    assertTrue(indexNames.contains("idx_wallet_policies_policy_id"))
                }
            }
        }
    }

    private fun Application.configureTestWalletApplication() {
        val walletDAO = WalletDAOSpecImpl(dsl)
        val createWalletUseCase = CreateWalletUseCaseSpecImpl(walletDAO)

        configureSerialization()
        configureErrorHandling()
        configureWalletRoutes(createWalletUseCase)
    }

    private fun String.jsonString(field: String): String {
        return Regex("\\\"$field\\\":\\\"([^\\\"]+)\\\"")
            .find(this)
            ?.groupValues
            ?.get(1)
            ?: error("Field $field not found in JSON: $this")
    }

    companion object {
        @Container
        @JvmField
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }
}
