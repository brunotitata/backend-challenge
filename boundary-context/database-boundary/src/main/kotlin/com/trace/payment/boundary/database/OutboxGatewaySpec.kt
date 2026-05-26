package com.trace.payment.boundary.database

import com.trace.payment.boundary.common.OutboxEventBO
import com.trace.payment.boundary.common.TransactionContext
import java.util.UUID

interface OutboxGatewaySpec {
    fun save(event: OutboxEventBO)
    fun save(event: OutboxEventBO, tx: TransactionContext)
    fun findUnprocessed(limit: Int): List<OutboxEventBO>
    fun markAsSent(id: UUID)
    fun markAsError(id: UUID)
}
