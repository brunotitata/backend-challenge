package com.trace.payment.boundary.database

import com.trace.payment.boundary.common.OutboxEventBO
import com.trace.payment.boundary.common.TransactionContext
import java.util.UUID

interface OutboxGatewaySpec {
    fun save(event: OutboxEventBO)
    fun save(event: OutboxEventBO, tx: TransactionContext)
    fun findUnprocessed(limit: Int): List<OutboxEventBO>
    fun markAsProcessed(id: UUID)
    fun incrementRetry(id: UUID)
}
