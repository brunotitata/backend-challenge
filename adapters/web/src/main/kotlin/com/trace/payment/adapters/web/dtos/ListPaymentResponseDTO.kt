package com.trace.payment.adapters.web.dtos

import kotlinx.serialization.Serializable

@Serializable
data class ListPaymentResponseDTO(
    val id: String,
    val walletId: String,
    val amount: String,
    val occurredAt: String,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
)
