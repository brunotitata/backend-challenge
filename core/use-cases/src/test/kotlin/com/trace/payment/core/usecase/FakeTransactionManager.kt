package com.trace.payment.core.usecase

import com.trace.payment.boundary.common.TransactionContext
import com.trace.payment.boundary.database.TransactionManagerSpec

object FakeTransactionManager : TransactionManagerSpec {
    override fun <T> runInTransaction(block: (TransactionContext) -> T): T {
        return block(object : TransactionContext {})
    }
}
