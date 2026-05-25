package com.trace.payment.adapters.web.dtos

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponseDTO(
    val error: ErrorDTO,
)

@Serializable
data class ErrorDTO(
    val code: String,
    val message: String,
)
