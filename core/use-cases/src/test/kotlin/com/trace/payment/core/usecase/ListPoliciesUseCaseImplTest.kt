package com.trace.payment.core.usecase

import com.trace.payment.boundary.database.PolicyDAOSpec
import com.trace.payment.core.entities.PolicyEntity
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ListPoliciesUseCaseImplTest {

    private val now = Instant.now()

    private val policies = listOf(
        PolicyEntity(
            id = UUID.randomUUID(),
            name = "DEFAULT_VALUE_LIMIT",
            category = "VALUE_LIMIT",
            maxPerPayment = BigDecimal("1000.00"),
            daytimeDailyLimit = BigDecimal("4000.00"),
            nighttimeDailyLimit = BigDecimal("1000.00"),
            weekendDailyLimit = BigDecimal("1000.00"),
            dailyTransactionLimit = null,
            createdAt = now,
            updatedAt = now,
        ),
        PolicyEntity(
            id = UUID.randomUUID(),
            name = "DAILY_TX_LIMIT",
            category = "TX_COUNT_LIMIT",
            maxPerPayment = null,
            daytimeDailyLimit = null,
            nighttimeDailyLimit = null,
            weekendDailyLimit = null,
            dailyTransactionLimit = 5,
            createdAt = now,
            updatedAt = now,
        ),
    )

    private val policyDAO = object : PolicyDAOSpec {
        override fun save(policy: PolicyEntity): PolicyEntity = policy
        override fun findAll(): List<PolicyEntity> = policies
        override fun findByWalletId(walletId: UUID): List<PolicyEntity> = emptyList()
        override fun findById(policyId: UUID): PolicyEntity? = null
        override fun findActiveByWalletId(walletId: UUID): PolicyEntity? = null
        override fun assignPolicy(walletId: UUID, policyId: UUID) {}
    }

    private val useCase = ListPoliciesUseCaseImpl(policyDAO)

    @Test
    fun `returns all policies`() {
        val result = useCase.execute()
        assertEquals(2, result.size)
        assertTrue(result.any { it.name == "DEFAULT_VALUE_LIMIT" })
        assertTrue(result.any { it.name == "DAILY_TX_LIMIT" })
    }

    @Test
    fun `returns empty list when no policies exist`() {
        val emptyDAO = object : PolicyDAOSpec {
            override fun save(policy: PolicyEntity): PolicyEntity = policy
            override fun findAll(): List<PolicyEntity> = emptyList()
            override fun findByWalletId(walletId: UUID): List<PolicyEntity> = emptyList()
            override fun findById(policyId: UUID): PolicyEntity? = null
            override fun findActiveByWalletId(walletId: UUID): PolicyEntity? = null
            override fun assignPolicy(walletId: UUID, policyId: UUID) {}
        }
        val emptyUseCase = ListPoliciesUseCaseImpl(emptyDAO)
        val result = emptyUseCase.execute()
        assertTrue(result.isEmpty())
    }
}
