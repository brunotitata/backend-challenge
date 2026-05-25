package com.trace.payment.adapters.web.dtos

import kotlinx.serialization.Serializable

@Serializable
data class AssignPolicyRequestDTO(
    val policyId: String? = null,
)
