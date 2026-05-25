package com.trace.payment.boundary.input

import com.trace.payment.core.entities.PeriodType
import com.trace.payment.core.entities.PolicyEntity
import java.math.BigDecimal

interface PolicyEvaluatorSpec {
    fun evaluate(policy: PolicyEntity, amount: BigDecimal, consumedAmount: BigDecimal, periodType: PeriodType): EvaluationResult
}
