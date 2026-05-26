package com.trace.payment.core.usecase

import com.trace.payment.boundary.database.PolicyDAOSpec
import com.trace.payment.boundary.database.WalletDAOSpec
import com.trace.payment.boundary.exceptions.NotFoundException
import com.trace.payment.core.entities.PolicyEntity
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AssignPolicyUseCaseImplTest {

    private val walletId = UUID.randomUUID()
    private val policyId = UUID.randomUUID()
    private val now = Instant.now()

    private val policy = PolicyEntity(
        id = policyId,
        name = "TEST_POLICY",
        category = "VALUE_LIMIT",
        maxPerPayment = BigDecimal("1000.00"),
        daytimeDailyLimit = BigDecimal("4000.00"),
        nighttimeDailyLimit = BigDecimal("1000.00"),
        weekendDailyLimit = BigDecimal("1000.00"),
        dailyTransactionLimit = null,
        createdAt = now,
        updatedAt = now,
    )

    private fun createUseCase(
        walletExists: Boolean = true,
        policyFound: PolicyEntity? = policy,
    ): AssignPolicyUseCaseImpl {
        val walletDAO = object : WalletDAOSpec {
            override fun save(wallet: com.trace.payment.core.entities.WalletEntity) = wallet
            override fun findActivePolicyName(walletId: UUID): String? = null
            override fun existsById(id: UUID): Boolean = walletExists
        }

        val policyDAO = object : PolicyDAOSpec {
            override fun save(policy: PolicyEntity): PolicyEntity = policy
            override fun findAll(): List<PolicyEntity> = emptyList()
            override fun findByWalletId(walletId: UUID): List<PolicyEntity> = emptyList()
            override fun findById(id: UUID): PolicyEntity? = policyFound
            override fun findActiveByWalletId(walletId: UUID): PolicyEntity? = null
            override fun assignPolicy(walletId: UUID, policyId: UUID) {}
        }

        return AssignPolicyUseCaseImpl(policyDAO, walletDAO)
    }

    @Test
    fun `assigns policy successfully`() {
        val useCase = createUseCase()
        useCase.execute(walletId, policyId)
    }

    @Test
    fun `throws not found when wallet does not exist`() {
        val useCase = createUseCase(walletExists = false)
        val exception = assertFailsWith<NotFoundException> {
            useCase.execute(walletId, policyId)
        }
        assertEquals("wallet not found", exception.message)
    }

    @Test
    fun `throws not found when policy does not exist`() {
        val useCase = createUseCase(policyFound = null)
        val exception = assertFailsWith<NotFoundException> {
            useCase.execute(walletId, policyId)
        }
        assertEquals("policy not found", exception.message)
    }
}
