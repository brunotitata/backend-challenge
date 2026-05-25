package com.trace.payment.boundary.input

import com.trace.payment.core.entities.PaymentEntity
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

interface ProcessPaymentUseCaseSpec {
    fun execute(walletId: UUID, amount: BigDecimal, occurredAt: Instant, idempotencyKey: String): PaymentEntity
}
