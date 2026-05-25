package com.trace.payment.adapters.web.dtos

import kotlinx.serialization.Serializable

@Serializable
data class CreatePaymentResponseDTO(
    val paymentId: String,
    val status: String,
    val amount: String,
    val occurredAt: String,
)
