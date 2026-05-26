package com.trace.payment.core.usecase

import com.trace.payment.boundary.common.OutboxEventBO
import com.trace.payment.boundary.common.TransactionContext
import com.trace.payment.boundary.database.OutboxGatewaySpec
import java.util.UUID

object FakeOutboxGateway : OutboxGatewaySpec {
    override fun save(event: OutboxEventBO) {}
    override fun save(event: OutboxEventBO, tx: TransactionContext) {}
    override fun findUnprocessed(limit: Int): List<OutboxEventBO> = emptyList()
    override fun markAsProcessed(id: UUID) {}
    override fun incrementRetry(id: UUID) {}
}
