package com.trace.payment.adapters.web.dtos

import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class CreatePaymentRequestDTO(
    @Serializable(with = BigDecimalSerializer::class) val amount: BigDecimal? = null,
    val occurredAt: String? = null,
)
