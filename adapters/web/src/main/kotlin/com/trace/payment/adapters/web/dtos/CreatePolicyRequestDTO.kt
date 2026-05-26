package com.trace.payment.adapters.web.dtos

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class CreatePolicyRequestDTO(
    val name: String? = null,
    val category: String? = null,
    val maxPerPayment: JsonPrimitive? = null,
    val daytimeDailyLimit: JsonPrimitive? = null,
    val nighttimeDailyLimit: JsonPrimitive? = null,
    val weekendDailyLimit: JsonPrimitive? = null,
    val dailyTransactionLimit: Int? = null,
)
