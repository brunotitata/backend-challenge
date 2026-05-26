package com.trace.payment.core.usecase

import com.trace.payment.boundary.database.PolicyDAOSpec
import com.trace.payment.core.entities.PolicyEntity
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PolicyResolverImplTest {

    private val walletId = UUID.randomUUID()
    private val policyId = UUID.randomUUID()
    private val now = Instant.now()

    private val activePolicy = PolicyEntity(
        id = policyId,
        name = "ACTIVE_POLICY",
        category = "VALUE_LIMIT",
        maxPerPayment = BigDecimal("1000.00"),
        daytimeDailyLimit = BigDecimal("4000.00"),
        nighttimeDailyLimit = BigDecimal("1000.00"),
        weekendDailyLimit = BigDecimal("1000.00"),
        dailyTransactionLimit = null,
        createdAt = now,
        updatedAt = now,
    )

    private fun createResolver(policy: PolicyEntity?): PolicyResolverImpl {
        val policyDAO = object : PolicyDAOSpec {
            override fun save(policy: PolicyEntity): PolicyEntity = policy
            override fun findAll(): List<PolicyEntity> = emptyList()
            override fun findByWalletId(walletId: UUID): List<PolicyEntity> = emptyList()
            override fun findById(policyId: UUID): PolicyEntity? = null
            override fun findActiveByWalletId(walletId: UUID): PolicyEntity? = policy
            override fun assignPolicy(walletId: UUID, policyId: UUID) {}
        }
        return PolicyResolverImpl(policyDAO)
    }

    @Test
    fun `resolves active policy for wallet`() {
        val resolver = createResolver(activePolicy)
        val result = resolver.resolve(walletId)
        assertEquals(activePolicy, result)
    }

    @Test
    fun `returns null when no active policy exists`() {
        val resolver = createResolver(null)
        val result = resolver.resolve(walletId)
        assertNull(result)
    }

    @Test
    fun `returns correct policy fields`() {
        val resolver = createResolver(activePolicy)
        val result = resolver.resolve(walletId)
        assertEquals("ACTIVE_POLICY", result?.name)
        assertEquals("VALUE_LIMIT", result?.category)
        assertEquals(BigDecimal("1000.00"), result?.maxPerPayment)
    }
}
