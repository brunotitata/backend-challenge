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

class ValueLimitEvaluatorTest {

    private val evaluator = ValueLimitEvaluator()

    private val defaultPolicy = PolicyEntity(
        id = UUID.randomUUID(),
        name = "DEFAULT_VALUE_LIMIT",
        category = "VALUE_LIMIT",
        maxPerPayment = BigDecimal("1000.00"),
        daytimeDailyLimit = BigDecimal("4000.00"),
        nighttimeDailyLimit = BigDecimal("1000.00"),
        weekendDailyLimit = BigDecimal("1000.00"),
        dailyTransactionLimit = null,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    @Test
    fun `rejects amount above maxPerPayment`() {
        val result = evaluator.evaluate(defaultPolicy, BigDecimal("1500.00"), BigDecimal.ZERO, PeriodType.DAYTIME)
        assertEquals(false, result.approved)
        assertEquals("MAX_PER_PAYMENT_EXCEEDED", result.reason)
    }

    @Test
    fun `accepts amount equal to maxPerPayment`() {
        val result = evaluator.evaluate(defaultPolicy, BigDecimal("1000.00"), BigDecimal.ZERO, PeriodType.DAYTIME)
        assertEquals(true, result.approved)
    }

    @Test
    fun `rejects consumption above daytime limit`() {
        val result = evaluator.evaluate(defaultPolicy, BigDecimal("300.00"), BigDecimal("3800.00"), PeriodType.DAYTIME)
        assertEquals(false, result.approved)
        assertEquals("DAILY_LIMIT_EXCEEDED", result.reason)
    }

    @Test
    fun `accepts consumption exactly at daytime limit`() {
        val result = evaluator.evaluate(defaultPolicy, BigDecimal("200.00"), BigDecimal("3800.00"), PeriodType.DAYTIME)
        assertEquals(true, result.approved)
    }

    @Test
    fun `rejects consumption above nighttime limit`() {
        val result = evaluator.evaluate(defaultPolicy, BigDecimal("200.00"), BigDecimal("900.00"), PeriodType.NIGHTTIME)
        assertEquals(false, result.approved)
        assertEquals("DAILY_LIMIT_EXCEEDED", result.reason)
    }

    @Test
    fun `accepts consumption exactly at nighttime limit`() {
        val result = evaluator.evaluate(defaultPolicy, BigDecimal("100.00"), BigDecimal("900.00"), PeriodType.NIGHTTIME)
        assertEquals(true, result.approved)
    }

    @Test
    fun `rejects consumption above weekend limit`() {
        val result = evaluator.evaluate(defaultPolicy, BigDecimal("300.00"), BigDecimal("800.00"), PeriodType.WEEKEND)
        assertEquals(false, result.approved)
        assertEquals("DAILY_LIMIT_EXCEEDED", result.reason)
    }

    @Test
    fun `accepts consumption exactly at weekend limit`() {
        val result = evaluator.evaluate(defaultPolicy, BigDecimal("200.00"), BigDecimal("800.00"), PeriodType.WEEKEND)
        assertEquals(true, result.approved)
    }

    @Test
    fun `accepts payment within all limits`() {
        val result = evaluator.evaluate(defaultPolicy, BigDecimal("500.00"), BigDecimal.ZERO, PeriodType.DAYTIME)
        assertEquals(true, result.approved)
    }

    @Test
    fun `returns EvaluationResult with approved and reason fields`() {
        val approved = evaluator.evaluate(defaultPolicy, BigDecimal("500.00"), BigDecimal.ZERO, PeriodType.DAYTIME)
        assertEquals(true, approved.approved)
        assertNull(approved.reason)

        val rejected = evaluator.evaluate(defaultPolicy, BigDecimal("1500.00"), BigDecimal.ZERO, PeriodType.DAYTIME)
        assertEquals(false, rejected.approved)
        assertNotNull(rejected.reason)
    }
}
