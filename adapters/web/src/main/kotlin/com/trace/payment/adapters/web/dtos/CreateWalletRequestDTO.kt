package com.trace.payment.adapters.web.dtos

import kotlinx.serialization.Serializable

@Serializable
data class CreateWalletRequestDTO(
    val ownerName: String? = null,
)
