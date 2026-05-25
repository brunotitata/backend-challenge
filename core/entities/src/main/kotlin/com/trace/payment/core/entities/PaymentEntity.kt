package com.trace.payment.core.entities

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class PaymentEntity(
    val id: UUID,
    val walletId: UUID,
    val policyId: UUID,
    val amount: BigDecimal,
    val occurredAt: Instant,
    val periodType: PeriodType,
    val periodStart: Instant,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)
