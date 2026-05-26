package com.trace.payment.core.usecase

import com.trace.payment.boundary.common.OutboxEventBO
import com.trace.payment.boundary.database.OutboxGatewaySpec
import com.trace.payment.boundary.database.PolicyDAOSpec
import com.trace.payment.boundary.database.TransactionManagerSpec
import com.trace.payment.boundary.input.CreatePolicyUseCaseSpec
import com.trace.payment.boundary.exceptions.ValidationException
import com.trace.payment.core.entities.PolicyEntity
import com.trace.payment.core.usecase.events.PolicyCreatedEvent
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class CreatePolicyUseCaseImpl(
    private val policyDAO: PolicyDAOSpec,
    private val outboxGateway: OutboxGatewaySpec,
    private val transactionManager: TransactionManagerSpec,
) : CreatePolicyUseCaseSpec {

    override fun execute(
        name: String,
        category: String,
        maxPerPayment: BigDecimal?,
        daytimeDailyLimit: BigDecimal?,
        nighttimeDailyLimit: BigDecimal?,
        weekendDailyLimit: BigDecimal?,
        dailyTransactionLimit: Int?,
    ): PolicyEntity {
        if (name.isBlank()) throw ValidationException("name must not be blank")
        if (category.isBlank()) throw ValidationException("category must not be blank")

        if (category == "VALUE_LIMIT") {
            requirePositive("maxPerPayment", maxPerPayment)
            requirePositive("daytimeDailyLimit", daytimeDailyLimit)
            requirePositive("nighttimeDailyLimit", nighttimeDailyLimit)
            requirePositive("weekendDailyLimit", weekendDailyLimit)
        } else if (category == "TX_COUNT_LIMIT") {
            if (dailyTransactionLimit == null) throw ValidationException("dailyTransactionLimit is required for TX_COUNT_LIMIT")
            if (dailyTransactionLimit <= 0) throw ValidationException("dailyTransactionLimit must be greater than zero")
        } else {
            throw ValidationException("unknown category: $category")
        }

        val now = Instant.now()
        val policy = PolicyEntity(
            id = UUID.randomUUID(),
            name = name.trim(),
            category = category,
            maxPerPayment = maxPerPayment,
            daytimeDailyLimit = daytimeDailyLimit,
            nighttimeDailyLimit = nighttimeDailyLimit,
            weekendDailyLimit = weekendDailyLimit,
            dailyTransactionLimit = dailyTransactionLimit,
            createdAt = now,
            updatedAt = now,
        )

        return transactionManager.runInTransaction { tx ->
            val savedPolicy = policyDAO.save(policy, tx)
            val payload = Json.encodeToString(
                PolicyCreatedEvent.serializer(),
                PolicyCreatedEvent(
                    id = savedPolicy.id.toString(),
                    name = savedPolicy.name,
                    category = savedPolicy.category,
                    maxPerPayment = savedPolicy.maxPerPayment?.toString(),
                    daytimeDailyLimit = savedPolicy.daytimeDailyLimit?.toString(),
                    nighttimeDailyLimit = savedPolicy.nighttimeDailyLimit?.toString(),
                    weekendDailyLimit = savedPolicy.weekendDailyLimit?.toString(),
                    dailyTransactionLimit = savedPolicy.dailyTransactionLimit,
                ),
            )
            outboxGateway.save(
                OutboxEventBO(
                    aggregateType = "policy",
                    aggregateId = savedPolicy.id.toString(),
                    eventType = "POLICY_CREATED",
                    payload = payload,
                ),
                tx,
            )
            savedPolicy
        }
    }

    private fun requirePositive(field: String, value: BigDecimal?) {
        if (value == null) throw ValidationException("$field is required for VALUE_LIMIT")
        MoneyValidator.requireValid(field, value)
        if (value <= BigDecimal.ZERO) throw ValidationException("$field must be greater than zero")
    }
}
