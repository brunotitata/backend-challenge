package com.trace.payment.core.usecase

import com.trace.payment.boundary.input.EvaluationResult
import com.trace.payment.boundary.input.PolicyEvaluatorSpec
import com.trace.payment.core.entities.PeriodType
import com.trace.payment.core.entities.PolicyEntity
import java.math.BigDecimal

class TxCountLimitEvaluator : PolicyEvaluatorSpec {

    override fun evaluate(
        policy: PolicyEntity,
        amount: BigDecimal,
        consumedAmount: BigDecimal,
        periodType: PeriodType,
        transactionCount: Int,
    ): EvaluationResult {
        val limit = policy.dailyTransactionLimit
            ?: return EvaluationResult(approved = false, reason = "DAILY_TRANSACTION_LIMIT_NOT_CONFIGURED")

        if (transactionCount >= limit) {
            return EvaluationResult(approved = false, reason = "DAILY_TRANSACTION_LIMIT_EXCEEDED")
        }

        return EvaluationResult(approved = true)
    }
}
