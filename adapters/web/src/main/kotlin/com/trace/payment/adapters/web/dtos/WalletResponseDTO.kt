package com.trace.payment.adapters.web.dtos

import kotlinx.serialization.Serializable

@Serializable
data class WalletResponseDTO(
    val id: String,
    val ownerName: String,
    val createdAt: String,
)
