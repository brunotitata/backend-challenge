package com.trace.payment.core.usecase

import com.trace.payment.boundary.database.PolicyDAOSpec
import com.trace.payment.boundary.database.WalletDAOSpec
import com.trace.payment.boundary.input.AssignPolicyUseCaseSpec
import com.trace.payment.boundary.exceptions.NotFoundException
import java.util.UUID

class AssignPolicyUseCaseImpl(
    private val policyDAO: PolicyDAOSpec,
    private val walletDAO: WalletDAOSpec,
) : AssignPolicyUseCaseSpec {

    override fun execute(walletId: UUID, policyId: UUID) {
        val walletExists = walletDAO.existsById(walletId)
        if (!walletExists) throw NotFoundException("wallet not found")

        val policyExists = policyDAO.findById(policyId)
        if (policyExists == null) throw NotFoundException("policy not found")

        policyDAO.assignPolicy(walletId, policyId)
    }
}
