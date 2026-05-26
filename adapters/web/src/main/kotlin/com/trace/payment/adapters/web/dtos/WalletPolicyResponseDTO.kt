package com.trace.payment.adapters.web.dtos

import kotlinx.serialization.Serializable

@Serializable
data class WalletPolicyResponseDTO(
    val id: String,
    val name: String,
    val category: String,
    val maxPerPayment: String?,
    val daytimeDailyLimit: String?,
    val nighttimeDailyLimit: String?,
    val weekendDailyLimit: String?,
    val dailyTransactionLimit: Int?,
    val active: Boolean,
    val createdAt: String,
    val updatedAt: String,
)
