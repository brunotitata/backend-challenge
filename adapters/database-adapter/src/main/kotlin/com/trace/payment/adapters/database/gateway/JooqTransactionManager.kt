package com.trace.payment.adapters.database.gateway

import com.trace.payment.boundary.common.TransactionContext
import com.trace.payment.boundary.database.TransactionManagerSpec
import org.jooq.DSLContext
import org.jooq.impl.DSL

class JooqTransactionManager(
    private val dsl: DSLContext,
) : TransactionManagerSpec {

    override fun <T> runInTransaction(block: (TransactionContext) -> T): T {
        return dsl.transactionResult { configuration ->
            val tx = DSL.using(configuration)
            block(JooqTransactionContext(tx))
        }
    }
}
