package com.trace.payment.boundary.database

import com.trace.payment.boundary.common.TransactionContext

interface TransactionManagerSpec {
    fun <T> runInTransaction(block: (TransactionContext) -> T): T
}
