package com.trace.payment.core.usecase

import com.trace.payment.boundary.database.PolicyDAOSpec
import com.trace.payment.boundary.database.WalletDAOSpec
import com.trace.payment.boundary.input.ListWalletPoliciesUseCaseSpec
import com.trace.payment.boundary.exceptions.NotFoundException
import com.trace.payment.core.entities.PolicyEntity
import java.util.UUID

class ListWalletPoliciesUseCaseImpl(
    private val policyDAO: PolicyDAOSpec,
    private val walletDAO: WalletDAOSpec,
) : ListWalletPoliciesUseCaseSpec {

    override fun execute(walletId: UUID): List<PolicyEntity> {
        val walletExists = walletDAO.existsById(walletId)
        if (!walletExists) throw NotFoundException("wallet not found")

        return policyDAO.findByWalletId(walletId)
    }
}
