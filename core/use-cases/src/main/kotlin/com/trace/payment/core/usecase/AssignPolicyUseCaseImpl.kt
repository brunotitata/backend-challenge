package com.trace.payment.core.usecase

import com.trace.payment.boundary.common.OutboxEventBO
import com.trace.payment.boundary.database.OutboxGatewaySpec
import com.trace.payment.boundary.database.PolicyDAOSpec
import com.trace.payment.boundary.database.TransactionManagerSpec
import com.trace.payment.boundary.database.WalletDAOSpec
import com.trace.payment.boundary.input.AssignPolicyUseCaseSpec
import com.trace.payment.boundary.exceptions.NotFoundException
import com.trace.payment.core.usecase.events.PolicyAssignedEvent
import kotlinx.serialization.json.Json
import java.util.UUID

class AssignPolicyUseCaseImpl(
    private val policyDAO: PolicyDAOSpec,
    private val walletDAO: WalletDAOSpec,
    private val outboxGateway: OutboxGatewaySpec,
    private val transactionManager: TransactionManagerSpec,
) : AssignPolicyUseCaseSpec {

    override fun execute(walletId: UUID, policyId: UUID) {
        val walletExists = walletDAO.existsById(walletId)
        if (!walletExists) throw NotFoundException("wallet not found")

        val policyExists = policyDAO.findById(policyId)
        if (policyExists == null) throw NotFoundException("policy not found")

        transactionManager.runInTransaction { tx ->
            policyDAO.assignPolicy(walletId, policyId, tx)
            val payload = Json.encodeToString(
                PolicyAssignedEvent.serializer(),
                PolicyAssignedEvent(walletId.toString(), policyId.toString()),
            )
            outboxGateway.save(
                OutboxEventBO(
                    aggregateType = "wallet_policy",
                    aggregateId = walletId.toString(),
                    eventType = "POLICY_ASSIGNED",
                    payload = payload,
                ),
                tx,
            )
        }
    }
}
