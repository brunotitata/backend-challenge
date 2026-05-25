package com.trace.payment.boundary.database

import com.trace.payment.core.entities.PaymentEntity
import com.trace.payment.core.entities.PeriodType
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

interface PaymentGatewaySpec {
    fun processPaymentInTransaction(
        walletId: UUID,
        policyId: UUID,
        amount: BigDecimal,
        occurredAt: Instant,
        periodType: PeriodType,
        periodStart: Instant,
        checkLimit: (consumedAmount: BigDecimal) -> Boolean,
    ): PaymentEntity?
}
