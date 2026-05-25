package com.trace.payment.core.entities

import java.time.Instant
import java.util.UUID

data class WalletEntity(
    val id: UUID,
    val ownerName: String,
    val createdAt: Instant,
)
