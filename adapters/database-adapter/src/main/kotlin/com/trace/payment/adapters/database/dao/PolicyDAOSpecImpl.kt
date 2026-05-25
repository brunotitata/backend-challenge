package com.trace.payment.adapters.database.dao

import com.trace.payment.boundary.database.PolicyDAOSpec
import com.trace.payment.core.entities.PolicyEntity
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class PolicyDAOSpecImpl(
    private val dataSource: DataSource,
) : PolicyDAOSpec {

    override fun save(policy: PolicyEntity): PolicyEntity {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO policies (id, name, category, max_per_payment, daytime_daily_limit, nighttime_daily_limit, weekend_daily_limit, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING *
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, policy.id)
                statement.setString(2, policy.name)
                statement.setString(3, policy.category)
                statement.setBigDecimal(4, policy.maxPerPayment)
                statement.setBigDecimal(5, policy.daytimeDailyLimit)
                statement.setBigDecimal(6, policy.nighttimeDailyLimit)
                statement.setBigDecimal(7, policy.weekendDailyLimit)
                statement.setTimestamp(8, Timestamp.from(policy.createdAt))
                statement.setTimestamp(9, Timestamp.from(policy.updatedAt))
                statement.executeQuery().use { resultSet ->
                    resultSet.next()
                    return mapToPolicyEntity(resultSet)
                }
            }
        }
    }

    override fun findAll(): List<PolicyEntity> {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT * FROM policies ORDER BY created_at DESC",
            ).use { statement ->
                statement.executeQuery().use { resultSet ->
                    val policies = mutableListOf<PolicyEntity>()
                    while (resultSet.next()) {
                        policies.add(mapToPolicyEntity(resultSet))
                    }
                    return policies
                }
            }
        }
    }

    override fun findByWalletId(walletId: UUID): List<PolicyEntity> {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT p.*, wp.active
                FROM policies p
                JOIN wallet_policies wp ON wp.policy_id = p.id
                WHERE wp.wallet_id = ?
                ORDER BY wp.created_at DESC
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, walletId)
                statement.executeQuery().use { resultSet ->
                    val policies = mutableListOf<PolicyEntity>()
                    while (resultSet.next()) {
                        policies.add(mapToPolicyEntity(resultSet, resultSet.getBoolean("active")))
                    }
                    return policies
                }
            }
        }
    }

    override fun findById(policyId: UUID): PolicyEntity? {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT * FROM policies WHERE id = ?",
            ).use { statement ->
                statement.setObject(1, policyId)
                statement.executeQuery().use { resultSet ->
                    return if (resultSet.next()) mapToPolicyEntity(resultSet) else null
                }
            }
        }
    }

    override fun findActiveByWalletId(walletId: UUID): PolicyEntity? {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT p.*
                FROM wallet_policies wp
                JOIN policies p ON p.id = wp.policy_id
                WHERE wp.wallet_id = ? AND wp.active = TRUE
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, walletId)
                statement.executeQuery().use { resultSet ->
                    return if (resultSet.next()) mapToPolicyEntity(resultSet) else null
                }
            }
        }
    }

    override fun assignPolicy(walletId: UUID, policyId: UUID) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                connection.prepareStatement(
                    "UPDATE wallet_policies SET active = FALSE WHERE wallet_id = ? AND active = TRUE",
                ).use { statement ->
                    statement.setObject(1, walletId)
                    statement.executeUpdate()
                }

                connection.prepareStatement(
                    """
                    INSERT INTO wallet_policies (wallet_id, policy_id, active)
                    VALUES (?, ?, TRUE)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, walletId)
                    statement.setObject(2, policyId)
                    statement.executeUpdate()
                }

                connection.commit()
            } catch (cause: Throwable) {
                connection.rollback()
                throw cause
            }
        }
    }

    private fun mapToPolicyEntity(resultSet: ResultSet, active: Boolean? = null): PolicyEntity {
        return PolicyEntity(
            id = resultSet.getObject("id", UUID::class.java),
            name = resultSet.getString("name"),
            category = resultSet.getString("category"),
            maxPerPayment = resultSet.getBigDecimal("max_per_payment"),
            daytimeDailyLimit = resultSet.getBigDecimal("daytime_daily_limit"),
            nighttimeDailyLimit = resultSet.getBigDecimal("nighttime_daily_limit"),
            weekendDailyLimit = resultSet.getBigDecimal("weekend_daily_limit"),
            dailyTransactionLimit = {
                val v = resultSet.getInt("daily_transaction_limit")
                if (resultSet.wasNull()) null else v
            }(),
            active = active,
            createdAt = resultSet.getTimestamp("created_at").toInstant(),
            updatedAt = resultSet.getTimestamp("updated_at").toInstant(),
        )
    }
}
