package com.trace.payment.boundary.database

import com.trace.payment.boundary.common.TransactionContext
import com.trace.payment.core.entities.WalletEntity
import java.util.UUID

interface WalletDAOSpec {
    fun save(wallet: WalletEntity, tx: TransactionContext): WalletEntity
    fun findActivePolicyName(walletId: UUID): String?
    fun existsById(walletId: UUID): Boolean
}
