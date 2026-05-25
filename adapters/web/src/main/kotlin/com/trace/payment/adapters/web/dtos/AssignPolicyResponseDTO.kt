package com.trace.payment.adapters.web.dtos

import kotlinx.serialization.Serializable

@Serializable
data class AssignPolicyResponseDTO(
    val walletId: String,
    val policyId: String,
    val active: Boolean,
    val updatedAt: String,
)
