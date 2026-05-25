package com.trace.payment.adapters.database.config

import com.trace.payment.boundary.common.DatabaseConfigBO
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import javax.sql.DataSource

object DatabaseFactory {
    fun create(config: DatabaseConfigBO): DataSource {
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.url
            username = config.username
            password = config.password
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 10
            minimumIdle = 2
            idleTimeout = 30000
            connectionTimeout = 10000
        }

        val dataSource = HikariDataSource(hikariConfig)

        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()

        return dataSource
    }

    fun createFromEnv(): DataSource {
        val config = DatabaseConfigBO(
            url = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/payment_api",
            username = System.getenv("DATABASE_USER") ?: "payment_api",
            password = System.getenv("DATABASE_PASSWORD") ?: "payment_api",
        )
        return create(config)
    }
}
