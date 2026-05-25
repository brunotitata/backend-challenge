package com.trace.payment.boundary.database

import com.trace.payment.core.entities.IdempotencyRecord
import java.util.UUID

interface IdempotencyRepositorySpec {
    fun findByWalletAndKey(walletId: UUID, idempotencyKey: String): IdempotencyRecord?
    fun save(record: IdempotencyRecord): IdempotencyRecord
}
