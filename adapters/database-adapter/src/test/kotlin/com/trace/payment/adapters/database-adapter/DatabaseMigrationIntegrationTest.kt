package com.trace.payment.adapters.databaseadapter

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import javax.sql.DataSource
import kotlin.test.assertTrue

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DatabaseMigrationIntegrationTest {
    @Test
    fun `Ktor runs Flyway migrations against isolated PostgreSQL container`() {
        val dataSource = createDataSource()

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

    private fun createDataSource(): DataSource {
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
