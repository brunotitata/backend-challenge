package com.trace.payment.boundary.database

import com.trace.payment.boundary.common.TransactionContext
import com.trace.payment.core.entities.PolicyEntity
import java.util.UUID

interface PolicyDAOSpec {
    fun save(policy: PolicyEntity, tx: TransactionContext): PolicyEntity
    fun findAll(): List<PolicyEntity>
    fun findByWalletId(walletId: UUID): List<PolicyEntity>
    fun findById(policyId: UUID): PolicyEntity?
    fun findActiveByWalletId(walletId: UUID): PolicyEntity?
    fun assignPolicy(walletId: UUID, policyId: UUID, tx: TransactionContext)
}
