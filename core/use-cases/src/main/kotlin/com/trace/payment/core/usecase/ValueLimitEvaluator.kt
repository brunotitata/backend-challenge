package com.trace.payment.core.usecase

import com.trace.payment.boundary.input.EvaluationResult
import com.trace.payment.boundary.input.PolicyEvaluatorSpec
import com.trace.payment.core.entities.PeriodType
import com.trace.payment.core.entities.PolicyEntity
import java.math.BigDecimal

class ValueLimitEvaluator : PolicyEvaluatorSpec {

    override fun evaluate(
        policy: PolicyEntity,
        amount: BigDecimal,
        consumedAmount: BigDecimal,
        periodType: PeriodType,
    ): EvaluationResult {
        val maxPerPayment = policy.maxPerPayment
        if (maxPerPayment != null && amount > maxPerPayment) {
            return EvaluationResult(approved = false, reason = "MAX_PER_PAYMENT_EXCEEDED")
        }

        val limit = when (periodType) {
            PeriodType.DAYTIME -> policy.daytimeDailyLimit
            PeriodType.NIGHTTIME -> policy.nighttimeDailyLimit
            PeriodType.WEEKEND -> policy.weekendDailyLimit
        }

        if (limit != null && consumedAmount + amount > limit) {
            return EvaluationResult(approved = false, reason = "DAILY_LIMIT_EXCEEDED")
        }

        return EvaluationResult(approved = true)
    }
}
