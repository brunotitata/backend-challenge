package com.trace.payment.adapters.databaseadapter

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.AfterEach
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertTrue

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DatabaseMigrationIntegrationTest {
    private var dataSource: HikariDataSource? = null

    @Test
    fun `Ktor runs Flyway migrations against isolated PostgreSQL container`() {
        val dataSource = createDataSource()
        this.dataSource = dataSource

        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()

        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                val result = statement.executeQuery(
                    "select count(*) from flyway_schema_history where success = true",
                )
                result.next()
                assertTrue(result.getInt(1) >= 1)
            }
        }
    }

    @Test
    fun `migrations create integrity constraints and performance indexes`() {
        val dataSource = createMigratedDataSource()

        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT constraint_name
                FROM information_schema.table_constraints
                WHERE constraint_schema = 'public'
                AND constraint_name IN (
                    'chk_wallets_owner_name_not_blank',
                    'chk_policies_category',
                    'chk_policies_value_limit_fields',
                    'chk_policies_tx_count_fields',
                    'chk_payments_amount_positive',
                    'chk_payments_period_type',
                    'chk_payments_status',
                    'chk_limit_consumptions_non_negative',
                    'chk_payment_idempotency_response_status',
                    'chk_payment_audit_status'
                )
                """.trimIndent(),
            ).use { statement ->
                statement.executeQuery().use { resultSet ->
                    val constraints = mutableSetOf<String>()
                    while (resultSet.next()) {
                        constraints += resultSet.getString("constraint_name")
                    }

                    assertTrue(constraints.contains("chk_wallets_owner_name_not_blank"))
                    assertTrue(constraints.contains("chk_policies_category"))
                    assertTrue(constraints.contains("chk_policies_value_limit_fields"))
                    assertTrue(constraints.contains("chk_policies_tx_count_fields"))
                    assertTrue(constraints.contains("chk_payments_amount_positive"))
                    assertTrue(constraints.contains("chk_payments_period_type"))
                    assertTrue(constraints.contains("chk_payments_status"))
                    assertTrue(constraints.contains("chk_limit_consumptions_non_negative"))
                    assertTrue(constraints.contains("chk_payment_idempotency_response_status"))
                    assertTrue(constraints.contains("chk_payment_audit_status"))
                }
            }

            connection.prepareStatement(
                """
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'public'
                AND indexname IN (
                    'idx_policies_name_unique',
                    'idx_payments_approved_wallet_occurred_id'
                )
                """.trimIndent(),
            ).use { statement ->
                statement.executeQuery().use { resultSet ->
                    val indexNames = mutableSetOf<String>()
                    while (resultSet.next()) {
                        indexNames += resultSet.getString("indexname")
                    }

                    assertTrue(indexNames.contains("idx_policies_name_unique"))
                    assertTrue(indexNames.contains("idx_payments_approved_wallet_occurred_id"))
                }
            }
        }
    }

    @AfterEach
    fun tearDown() {
        dataSource?.close()
        dataSource = null
    }

    private fun createMigratedDataSource(): HikariDataSource {
        val dataSource = createDataSource()
        this.dataSource = dataSource

        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()

        return dataSource
    }

    private fun createDataSource(): HikariDataSource {
        val config = HikariConfig().apply {
            jdbcUrl = postgres.jdbcUrl
            username = postgres.username
            password = postgres.password
            driverClassName = postgres.driverClassName
        }
        return HikariDataSource(config)
    }

    companion object {
        @Container
        @JvmField
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }
}
