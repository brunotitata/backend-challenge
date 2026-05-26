package com.trace.payment.boundary.input

import com.trace.payment.core.entities.PaginationResult
import com.trace.payment.core.entities.PaymentEntity
import java.time.Instant
import java.util.UUID

interface ListPaymentsUseCaseSpec {
    fun execute(
        walletId: UUID,
        startDate: Instant? = null,
        endDate: Instant? = null,
        cursor: String? = null,
        limit: Int = 20,
    ): PaginationResult<PaymentEntity>
}
