package com.trace.payment.boundary.input

import com.trace.payment.core.entities.PolicyEntity
import java.math.BigDecimal

interface CreatePolicyUseCaseSpec {
    fun execute(
        name: String,
        category: String,
        maxPerPayment: BigDecimal?,
        daytimeDailyLimit: BigDecimal?,
        nighttimeDailyLimit: BigDecimal?,
        weekendDailyLimit: BigDecimal?,
        dailyTransactionLimit: Int?,
    ): PolicyEntity
}
