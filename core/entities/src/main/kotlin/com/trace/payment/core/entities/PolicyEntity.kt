package com.trace.payment.core.entities

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class PolicyEntity(
    val id: UUID,
    val name: String,
    val category: String,
    val maxPerPayment: BigDecimal?,
    val daytimeDailyLimit: BigDecimal?,
    val nighttimeDailyLimit: BigDecimal?,
    val weekendDailyLimit: BigDecimal?,
    val dailyTransactionLimit: Int?,
    val active: Boolean? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
