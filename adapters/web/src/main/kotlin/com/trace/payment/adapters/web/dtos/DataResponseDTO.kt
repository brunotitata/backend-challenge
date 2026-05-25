package com.trace.payment.adapters.web.dtos

import kotlinx.serialization.Serializable

@Serializable
data class DataResponseDTO<T>(
    val data: List<T>,
    val meta: MetaDTO,
)

@Serializable
data class MetaDTO(
    val nextCursor: String? = null,
    val previousCursor: String? = null,
    val total: Int,
    val totalMatches: Int? = null,
)
