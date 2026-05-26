package com.trace.payment.adapters.web.dtos

import kotlinx.serialization.Serializable

@Serializable
data class CreatePolicyRequestDTO(
    val name: String? = null,
    val category: String? = null,
    val maxPerPayment: String? = null,
    val daytimeDailyLimit: String? = null,
    val nighttimeDailyLimit: String? = null,
    val weekendDailyLimit: String? = null,
    val dailyTransactionLimit: Int? = null,
)
