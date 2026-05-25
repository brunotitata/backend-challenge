package com.trace.payment.core.usecase

import com.trace.payment.boundary.database.PolicyDAOSpec
import com.trace.payment.boundary.input.CreatePolicyUseCaseSpec
import com.trace.payment.boundary.exceptions.ValidationException
import com.trace.payment.core.entities.PolicyEntity
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class CreatePolicyUseCaseImpl(
    private val policyDAO: PolicyDAOSpec,
) : CreatePolicyUseCaseSpec {

    override fun execute(
        name: String,
        category: String,
        maxPerPayment: BigDecimal?,
        daytimeDailyLimit: BigDecimal?,
        nighttimeDailyLimit: BigDecimal?,
        weekendDailyLimit: BigDecimal?,
    ): PolicyEntity {
        if (name.isBlank()) throw ValidationException("name must not be blank")
        if (category.isBlank()) throw ValidationException("category must not be blank")

        if (category == "VALUE_LIMIT") {
            requirePositive("maxPerPayment", maxPerPayment)
            requirePositive("daytimeDailyLimit", daytimeDailyLimit)
            requirePositive("nighttimeDailyLimit", nighttimeDailyLimit)
            requirePositive("weekendDailyLimit", weekendDailyLimit)
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
            dailyTransactionLimit = null,
            createdAt = now,
            updatedAt = now,
        )

        return policyDAO.save(policy)
    }

    private fun requirePositive(field: String, value: BigDecimal?) {
        if (value == null) throw ValidationException("$field is required for VALUE_LIMIT")
        if (value <= BigDecimal.ZERO) throw ValidationException("$field must be greater than zero")
    }
}
