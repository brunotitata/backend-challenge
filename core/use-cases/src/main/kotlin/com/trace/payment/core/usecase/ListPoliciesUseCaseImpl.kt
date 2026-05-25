package com.trace.payment.core.usecase

import com.trace.payment.boundary.database.PolicyDAOSpec
import com.trace.payment.boundary.input.ListPoliciesUseCaseSpec
import com.trace.payment.core.entities.PolicyEntity

class ListPoliciesUseCaseImpl(
    private val policyDAO: PolicyDAOSpec,
) : ListPoliciesUseCaseSpec {

    override fun execute(): List<PolicyEntity> {
        return policyDAO.findAll()
    }
}
