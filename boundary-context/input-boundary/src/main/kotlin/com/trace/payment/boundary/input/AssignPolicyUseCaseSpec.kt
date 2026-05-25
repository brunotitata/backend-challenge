package com.trace.payment.boundary.input

import java.util.UUID

interface AssignPolicyUseCaseSpec {
    fun execute(walletId: UUID, policyId: UUID)
}
