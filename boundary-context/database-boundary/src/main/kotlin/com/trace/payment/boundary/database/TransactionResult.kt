package com.trace.payment.boundary.database

import com.trace.payment.core.entities.PaymentEntity

sealed interface TransactionResult {
    data class Approved(val payment: PaymentEntity) : TransactionResult
    data object Rejected : TransactionResult
    data class IdempotentReplay(val statusCode: Int, val payment: PaymentEntity?) : TransactionResult
    data object Conflict : TransactionResult
}
