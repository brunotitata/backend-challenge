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
import kotlin.test.assertTrue

class ListWalletPoliciesUseCaseImplTest {

    private val walletId = UUID.randomUUID()
    private val now = Instant.now()

    private val policies = listOf(
        PolicyEntity(
            id = UUID.randomUUID(),
            name = "POLICY_A",
            category = "VALUE_LIMIT",
            maxPerPayment = BigDecimal("1000.00"),
            daytimeDailyLimit = BigDecimal("4000.00"),
            nighttimeDailyLimit = BigDecimal("1000.00"),
            weekendDailyLimit = BigDecimal("1000.00"),
            dailyTransactionLimit = null,
            active = true,
            createdAt = now,
            updatedAt = now,
        ),
        PolicyEntity(
            id = UUID.randomUUID(),
            name = "POLICY_B",
            category = "VALUE_LIMIT",
            maxPerPayment = BigDecimal("500.00"),
            daytimeDailyLimit = BigDecimal("2000.00"),
            nighttimeDailyLimit = BigDecimal("500.00"),
            weekendDailyLimit = BigDecimal("500.00"),
            dailyTransactionLimit = null,
            active = false,
            createdAt = now,
            updatedAt = now,
        ),
    )

    private fun createUseCase(
        walletExists: Boolean = true,
        walletPolicies: List<PolicyEntity> = policies,
    ): ListWalletPoliciesUseCaseImpl {
        val walletDAO = object : WalletDAOSpec {
            override fun save(wallet: com.trace.payment.core.entities.WalletEntity) = wallet
            override fun findActivePolicyName(walletId: UUID): String? = null
            override fun existsById(walletId: UUID): Boolean = walletExists
        }

        val policyDAO = object : PolicyDAOSpec {
            override fun save(policy: PolicyEntity): PolicyEntity = policy
            override fun findAll(): List<PolicyEntity> = emptyList()
            override fun findByWalletId(walletId: UUID): List<PolicyEntity> = walletPolicies
            override fun findById(policyId: UUID): PolicyEntity? = null
            override fun findActiveByWalletId(walletId: UUID): PolicyEntity? = null
            override fun assignPolicy(walletId: UUID, policyId: UUID) {}
        }

        return ListWalletPoliciesUseCaseImpl(policyDAO, walletDAO)
    }

    @Test
    fun `returns policies for existing wallet`() {
        val useCase = createUseCase()
        val result = useCase.execute(walletId)
        assertEquals(2, result.size)
        assertTrue(result.any { it.name == "POLICY_A" && it.active == true })
        assertTrue(result.any { it.name == "POLICY_B" && it.active == false })
    }

    @Test
    fun `returns empty list when wallet has no policies`() {
        val useCase = createUseCase(walletPolicies = emptyList())
        val result = useCase.execute(walletId)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `throws not found when wallet does not exist`() {
        val useCase = createUseCase(walletExists = false)
        val exception = assertFailsWith<NotFoundException> {
            useCase.execute(walletId)
        }
        assertEquals("wallet not found", exception.message)
    }
}
