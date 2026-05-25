package com.trace.payment.adapters.database.dao

import com.trace.payment.boundary.database.WalletDAOSpec
import com.trace.payment.core.entities.WalletEntity
import java.sql.Connection
import java.sql.Timestamp
import java.util.UUID
import javax.sql.DataSource

class WalletDAOSpecImpl(
    private val dataSource: DataSource,
) : WalletDAOSpec {

    override fun save(wallet: WalletEntity): WalletEntity {

        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                ensureDefaultPolicy(connection)
                val savedWallet = insertWallet(connection, wallet)
                insertDefaultActivePolicy(connection, savedWallet.id)
                connection.commit()
                return savedWallet
            } catch (cause: Throwable) {
                connection.rollback()
                throw cause
            }
        }

    }

    override fun findActivePolicyName(walletId: UUID): String? {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT p.name
                FROM wallet_policies wp
                JOIN policies p ON p.id = wp.policy_id
                WHERE wp.wallet_id = ? AND wp.active = TRUE
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, walletId)
                statement.executeQuery().use { resultSet ->
                    return if (resultSet.next()) {
                        resultSet.getString("name")
                    } else {
                        null
                    }
                }
            }
        }
    }

    override fun existsById(walletId: UUID): Boolean {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT 1 FROM wallets WHERE id = ?",
            ).use { statement ->
                statement.setObject(1, walletId)
                statement.executeQuery().use { resultSet ->
                    return resultSet.next()
                }
            }
        }
    }

    private fun insertWallet(connection: Connection, wallet: WalletEntity): WalletEntity {
        connection.prepareStatement(
            """
            INSERT INTO wallets (id, owner_name, created_at)
            VALUES (?, ?, ?)
            RETURNING id, created_at
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, wallet.id)
            statement.setString(2, wallet.ownerName)
            statement.setTimestamp(3, Timestamp.from(wallet.createdAt))
            statement.executeQuery().use { resultSet ->
                resultSet.next()
                return WalletEntity(
                    id = resultSet.getObject("id", UUID::class.java),
                    ownerName = wallet.ownerName,
                    createdAt = resultSet.getTimestamp("created_at").toInstant(),
                )
            }
        }
    }

    private fun insertDefaultActivePolicy(connection: Connection, walletId: UUID) {
        connection.prepareStatement(
            """
            INSERT INTO wallet_policies (wallet_id, policy_id, active)
            SELECT ?, id, TRUE
            FROM policies
            WHERE name = 'DEFAULT_VALUE_LIMIT'
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, walletId)
            val affectedRows = statement.executeUpdate()
            check(affectedRows == 1) {
                "Default policy DEFAULT_VALUE_LIMIT not found"
            }
        }
    }

    private fun ensureDefaultPolicy(connection: Connection) {
        connection.prepareStatement(
            """
            INSERT INTO policies (
                name,
                category,
                max_per_payment,
                daytime_daily_limit,
                nighttime_daily_limit,
                weekend_daily_limit
            )
            SELECT 'DEFAULT_VALUE_LIMIT', 'VALUE_LIMIT', 1000.00, 4000.00, 1000.00, 1000.00
            WHERE NOT EXISTS (
                SELECT 1 FROM policies WHERE name = 'DEFAULT_VALUE_LIMIT'
            )
            """.trimIndent(),
        ).use { statement ->
            statement.executeUpdate()
        }
    }
}
