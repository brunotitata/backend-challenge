package com.trace.payment.boundary.database

import com.trace.payment.core.entities.PaginationResult
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
        idempotencyKey: String,
        requestHash: String,
        checkLimit: (consumedAmount: BigDecimal, transactionCount: Int) -> Boolean,
    ): TransactionResult

    fun findById(paymentId: UUID): PaymentEntity?

    fun findApprovedByWalletId(
        walletId: UUID,
        startDate: Instant?,
        endDate: Instant?,
        cursor: String?,
        limit: Int,
    ): PaginationResult<PaymentEntity>
}
