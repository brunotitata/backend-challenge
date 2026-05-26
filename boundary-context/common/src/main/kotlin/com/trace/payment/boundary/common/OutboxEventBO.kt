package com.trace.payment.boundary.common

import java.time.Instant
import java.util.UUID

data class OutboxEventBO(
    val id: UUID = UUID.randomUUID(),
    val aggregateType: String,
    val aggregateId: String,
    val eventType: String,
    val payload: String,
    val createdAt: Instant = Instant.now(),
    val processedAt: Instant? = null,
    val retryCount: Int = 0,
    val status: String? = null,
)
