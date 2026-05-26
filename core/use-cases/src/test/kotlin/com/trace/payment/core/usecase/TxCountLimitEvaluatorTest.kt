package com.trace.payment.core.usecase

import com.trace.payment.core.entities.PeriodType
import com.trace.payment.core.entities.PolicyEntity
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TxCountLimitEvaluatorTest {

    private val evaluator = TxCountLimitEvaluator()

    private val txCountPolicy = PolicyEntity(
        id = UUID.randomUUID(),
        name = "DAILY_TX_LIMIT",
        category = "TX_COUNT_LIMIT",
        maxPerPayment = null,
        daytimeDailyLimit = null,
        nighttimeDailyLimit = null,
        weekendDailyLimit = null,
        dailyTransactionLimit = 5,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    @Test
    fun `accepts payment when transaction count is below limit`() {
        val result = evaluator.evaluate(txCountPolicy, BigDecimal("10.00"), BigDecimal.ZERO, PeriodType.DAYTIME, 4)
        assertEquals(true, result.approved)
        assertNull(result.reason)
    }

    @Test
    fun `accepts payment when transaction count is exactly one below limit`() {
        val result = evaluator.evaluate(txCountPolicy, BigDecimal("10.00"), BigDecimal.ZERO, PeriodType.DAYTIME, 4)
        assertEquals(true, result.approved)
    }

    @Test
    fun `rejects payment when transaction count equals limit`() {
        val result = evaluator.evaluate(txCountPolicy, BigDecimal("10.00"), BigDecimal.ZERO, PeriodType.DAYTIME, 5)
        assertEquals(false, result.approved)
        assertEquals("DAILY_TRANSACTION_LIMIT_EXCEEDED", result.reason)
    }

    @Test
    fun `rejects payment when transaction count exceeds limit`() {
        val result = evaluator.evaluate(txCountPolicy, BigDecimal("10.00"), BigDecimal.ZERO, PeriodType.DAYTIME, 6)
        assertEquals(false, result.approved)
        assertEquals("DAILY_TRANSACTION_LIMIT_EXCEEDED", result.reason)
    }

    @Test
    fun `accepts payment with zero consumed amount`() {
        val result = evaluator.evaluate(txCountPolicy, BigDecimal("999999.99"), BigDecimal.ZERO, PeriodType.DAYTIME, 0)
        assertEquals(true, result.approved)
    }

    @Test
    fun `ignores consumed amount for approval decision`() {
        val result = evaluator.evaluate(txCountPolicy, BigDecimal("10.00"), BigDecimal("999999.99"), PeriodType.DAYTIME, 2)
        assertEquals(true, result.approved)
    }

    @Test
    fun `works with any period type`() {
        val daytime = evaluator.evaluate(txCountPolicy, BigDecimal("10.00"), BigDecimal.ZERO, PeriodType.DAYTIME, 2)
        assertEquals(true, daytime.approved)

        val nighttime = evaluator.evaluate(txCountPolicy, BigDecimal("10.00"), BigDecimal.ZERO, PeriodType.NIGHTTIME, 2)
        assertEquals(true, nighttime.approved)

        val weekend = evaluator.evaluate(txCountPolicy, BigDecimal("10.00"), BigDecimal.ZERO, PeriodType.WEEKEND, 2)
        assertEquals(true, weekend.approved)
    }

    @Test
    fun `returns EvaluationResult with approved and reason fields`() {
        val approved = evaluator.evaluate(txCountPolicy, BigDecimal("10.00"), BigDecimal.ZERO, PeriodType.DAYTIME, 0)
        assertEquals(true, approved.approved)
        assertNull(approved.reason)

        val rejected = evaluator.evaluate(txCountPolicy, BigDecimal("10.00"), BigDecimal.ZERO, PeriodType.DAYTIME, 5)
        assertEquals(false, rejected.approved)
        assertNotNull(rejected.reason)
    }
}
