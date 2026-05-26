package com.trace.payment.core.entities

data class PaginationResult<T>(
    val items: List<T>,
    val nextCursor: String?,
    val previousCursor: String?,
    val total: Int,
)
