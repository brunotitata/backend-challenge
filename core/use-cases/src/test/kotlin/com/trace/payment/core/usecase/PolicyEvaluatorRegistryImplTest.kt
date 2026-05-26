package com.trace.payment.core.usecase

import com.trace.payment.boundary.input.EvaluationResult
import com.trace.payment.boundary.input.PolicyEvaluatorSpec
import com.trace.payment.core.entities.PeriodType
import com.trace.payment.core.entities.PolicyEntity
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class PolicyEvaluatorRegistryImplTest {

    private val registry = PolicyEvaluatorRegistryImpl()

    private val valueEvaluator = object : PolicyEvaluatorSpec {
        override fun evaluate(
            policy: PolicyEntity,
            amount: BigDecimal,
            consumedAmount: BigDecimal,
            periodType: PeriodType,
            transactionCount: Int,
        ): EvaluationResult = EvaluationResult(true)
    }

    private val txEvaluator = object : PolicyEvaluatorSpec {
        override fun evaluate(
            policy: PolicyEntity,
            amount: BigDecimal,
            consumedAmount: BigDecimal,
            periodType: PeriodType,
            transactionCount: Int,
        ): EvaluationResult = EvaluationResult(false, "LIMIT_EXCEEDED")
    }

    @Test
    fun `returns registered evaluator`() {
        registry.register("VALUE_LIMIT", valueEvaluator)
        val result = registry.get("VALUE_LIMIT")
        assertSame(valueEvaluator, result)
    }

    @Test
    fun `returns different evaluators for different categories`() {
        registry.register("VALUE_LIMIT", valueEvaluator)
        registry.register("TX_COUNT_LIMIT", txEvaluator)

        assertSame(valueEvaluator, registry.get("VALUE_LIMIT"))
        assertSame(txEvaluator, registry.get("TX_COUNT_LIMIT"))
    }

    @Test
    fun `returns null for unregistered category`() {
        val result = registry.get("UNKNOWN")
        assertNull(result)
    }

    @Test
    fun `overwrites existing evaluator for same category`() {
        val newEvaluator = object : PolicyEvaluatorSpec {
            override fun evaluate(
                policy: PolicyEntity,
                amount: BigDecimal,
                consumedAmount: BigDecimal,
                periodType: PeriodType,
                transactionCount: Int,
            ): EvaluationResult = EvaluationResult(true)
        }

        registry.register("VALUE_LIMIT", valueEvaluator)
        registry.register("VALUE_LIMIT", newEvaluator)

        assertSame(newEvaluator, registry.get("VALUE_LIMIT"))
    }

    @Test
    fun `registered evaluator works correctly`() {
        registry.register("VALUE_LIMIT", valueEvaluator)
        val evaluator = registry.get("VALUE_LIMIT")!!
        val policy = PolicyEntity(
            id = java.util.UUID.randomUUID(),
            name = "TEST",
            category = "VALUE_LIMIT",
            maxPerPayment = BigDecimal("1000.00"),
            daytimeDailyLimit = BigDecimal("4000.00"),
            nighttimeDailyLimit = BigDecimal("1000.00"),
            weekendDailyLimit = BigDecimal("1000.00"),
            dailyTransactionLimit = null,
            createdAt = java.time.Instant.now(),
            updatedAt = java.time.Instant.now(),
        )
        val result = evaluator.evaluate(policy, BigDecimal("100.00"), BigDecimal.ZERO, PeriodType.DAYTIME, 0)
        assertEquals(true, result.approved)
    }
}
