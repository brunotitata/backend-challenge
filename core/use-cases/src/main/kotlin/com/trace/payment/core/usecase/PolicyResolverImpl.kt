package com.trace.payment.core.usecase

import com.trace.payment.boundary.database.PolicyDAOSpec
import com.trace.payment.boundary.input.PolicyResolverSpec
import com.trace.payment.core.entities.PolicyEntity
import java.util.UUID

class PolicyResolverImpl(
    private val policyDAO: PolicyDAOSpec,
) : PolicyResolverSpec {

    override fun resolve(walletId: UUID): PolicyEntity? {
        return policyDAO.findActiveByWalletId(walletId)
    }
}
