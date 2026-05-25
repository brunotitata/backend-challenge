package com.trace.payment.core.entities

import java.time.Instant
import java.util.UUID

data class IdempotencyRecord(
    val id: UUID,
    val walletId: UUID,
    val idempotencyKey: String,
    val requestHash: String,
    val paymentId: UUID?,
    val responseStatus: Int,
    val responseBody: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
