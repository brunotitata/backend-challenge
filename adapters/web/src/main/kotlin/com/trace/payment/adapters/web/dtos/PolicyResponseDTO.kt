package com.trace.payment.adapters.web.dtos

import kotlinx.serialization.Serializable

@Serializable
data class PolicyResponseDTO(
    val id: String,
    val name: String,
    val category: String,
    val maxPerPayment: String?,
    val daytimeDailyLimit: String?,
    val nighttimeDailyLimit: String?,
    val weekendDailyLimit: String?,
    val createdAt: String,
    val updatedAt: String,
)
