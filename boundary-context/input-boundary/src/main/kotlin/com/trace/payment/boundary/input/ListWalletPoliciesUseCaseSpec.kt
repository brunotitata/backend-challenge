package com.trace.payment.boundary.input

import com.trace.payment.core.entities.PolicyEntity
import java.util.UUID

interface ListWalletPoliciesUseCaseSpec {
    fun execute(walletId: UUID): List<PolicyEntity>
}
