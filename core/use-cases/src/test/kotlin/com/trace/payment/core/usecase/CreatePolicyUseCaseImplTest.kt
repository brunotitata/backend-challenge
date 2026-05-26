package com.trace.payment.core.usecase

import com.trace.payment.boundary.database.PolicyDAOSpec
import com.trace.payment.boundary.exceptions.ValidationException
import com.trace.payment.core.entities.PolicyEntity
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CreatePolicyUseCaseImplTest {

    private val savedPolicies = mutableListOf<PolicyEntity>()

    private val policyDAO = object : PolicyDAOSpec {
        override fun save(policy: PolicyEntity): PolicyEntity {
            savedPolicies.add(policy)
            return policy
        }

        override fun findAll(): List<PolicyEntity> = emptyList()
        override fun findByWalletId(walletId: UUID): List<PolicyEntity> = emptyList()
        override fun findById(policyId: UUID): PolicyEntity? = null
        override fun findActiveByWalletId(walletId: UUID): PolicyEntity? = null
        override fun assignPolicy(walletId: UUID, policyId: UUID) {}
    }

    private val useCase = CreatePolicyUseCaseImpl(policyDAO)

    @Test
    fun `creates VALUE_LIMIT policy with valid data`() {
        val result = useCase.execute(
            name = "CUSTOM_LIMIT",
            category = "VALUE_LIMIT",
            maxPerPayment = BigDecimal("500.00"),
            daytimeDailyLimit = BigDecimal("2000.00"),
            nighttimeDailyLimit = BigDecimal("500.00"),
            weekendDailyLimit = BigDecimal("500.00"),
            dailyTransactionLimit = null,
        )

        assertEquals("CUSTOM_LIMIT", result.name)
        assertEquals("VALUE_LIMIT", result.category)
        assertEquals(BigDecimal("500.00"), result.maxPerPayment)
        assertEquals(1, savedPolicies.size)
    }

    @Test
    fun `trims policy name`() {
        val result = useCase.execute(
            name = "  CUSTOM_LIMIT  ",
            category = "VALUE_LIMIT",
            maxPerPayment = BigDecimal("500.00"),
            daytimeDailyLimit = BigDecimal("2000.00"),
            nighttimeDailyLimit = BigDecimal("500.00"),
            weekendDailyLimit = BigDecimal("500.00"),
            dailyTransactionLimit = null,
        )
        assertEquals("CUSTOM_LIMIT", result.name)
    }

    @Test
    fun `creates TX_COUNT_LIMIT policy with valid data`() {
        val result = useCase.execute(
            name = "DAILY_TX_LIMIT",
            category = "TX_COUNT_LIMIT",
            maxPerPayment = null,
            daytimeDailyLimit = null,
            nighttimeDailyLimit = null,
            weekendDailyLimit = null,
            dailyTransactionLimit = 5,
        )

        assertEquals("DAILY_TX_LIMIT", result.name)
        assertEquals("TX_COUNT_LIMIT", result.category)
        assertEquals(5, result.dailyTransactionLimit)
        assertEquals(1, savedPolicies.size)
    }

    @Test
    fun `throws validation exception for blank name`() {
        val exception = assertFailsWith<ValidationException> {
            useCase.execute(
                name = "   ",
                category = "VALUE_LIMIT",
                maxPerPayment = BigDecimal("500.00"),
                daytimeDailyLimit = BigDecimal("2000.00"),
                nighttimeDailyLimit = BigDecimal("500.00"),
                weekendDailyLimit = BigDecimal("500.00"),
                dailyTransactionLimit = null,
            )
        }
        assertEquals("name must not be blank", exception.message)
    }

    @Test
    fun `throws validation exception for blank category`() {
        val exception = assertFailsWith<ValidationException> {
            useCase.execute(
                name = "TEST",
                category = "",
                maxPerPayment = BigDecimal("500.00"),
                daytimeDailyLimit = BigDecimal("2000.00"),
                nighttimeDailyLimit = BigDecimal("500.00"),
                weekendDailyLimit = BigDecimal("500.00"),
                dailyTransactionLimit = null,
            )
        }
        assertEquals("category must not be blank", exception.message)
    }

    @Test
    fun `throws validation exception for unknown category`() {
        val exception = assertFailsWith<ValidationException> {
            useCase.execute(
                name = "TEST",
                category = "UNKNOWN",
                maxPerPayment = null,
                daytimeDailyLimit = null,
                nighttimeDailyLimit = null,
                weekendDailyLimit = null,
                dailyTransactionLimit = null,
            )
        }
        assertEquals("unknown category: UNKNOWN", exception.message)
    }

    @Test
    fun `throws validation exception when VALUE_LIMIT maxPerPayment is null`() {
        val exception = assertFailsWith<ValidationException> {
            useCase.execute(
                name = "TEST",
                category = "VALUE_LIMIT",
                maxPerPayment = null,
                daytimeDailyLimit = BigDecimal("2000.00"),
                nighttimeDailyLimit = BigDecimal("500.00"),
                weekendDailyLimit = BigDecimal("500.00"),
                dailyTransactionLimit = null,
            )
        }
        assertEquals("maxPerPayment is required for VALUE_LIMIT", exception.message)
    }

    @Test
    fun `throws validation exception when VALUE_LIMIT maxPerPayment is zero`() {
        val exception = assertFailsWith<ValidationException> {
            useCase.execute(
                name = "TEST",
                category = "VALUE_LIMIT",
                maxPerPayment = BigDecimal.ZERO,
                daytimeDailyLimit = BigDecimal("2000.00"),
                nighttimeDailyLimit = BigDecimal("500.00"),
                weekendDailyLimit = BigDecimal("500.00"),
                dailyTransactionLimit = null,
            )
        }
        assertEquals("maxPerPayment must be greater than zero", exception.message)
    }

    @Test
    fun `throws validation exception when VALUE_LIMIT maxPerPayment is negative`() {
        val exception = assertFailsWith<ValidationException> {
            useCase.execute(
                name = "TEST",
                category = "VALUE_LIMIT",
                maxPerPayment = BigDecimal("-100.00"),
                daytimeDailyLimit = BigDecimal("2000.00"),
                nighttimeDailyLimit = BigDecimal("500.00"),
                weekendDailyLimit = BigDecimal("500.00"),
                dailyTransactionLimit = null,
            )
        }
        assertEquals("maxPerPayment must be greater than zero", exception.message)
    }

    @Test
    fun `throws validation exception when VALUE_LIMIT daytimeDailyLimit is null`() {
        val exception = assertFailsWith<ValidationException> {
            useCase.execute(
                name = "TEST",
                category = "VALUE_LIMIT",
                maxPerPayment = BigDecimal("500.00"),
                daytimeDailyLimit = null,
                nighttimeDailyLimit = BigDecimal("500.00"),
                weekendDailyLimit = BigDecimal("500.00"),
                dailyTransactionLimit = null,
            )
        }
        assertEquals("daytimeDailyLimit is required for VALUE_LIMIT", exception.message)
    }

    @Test
    fun `throws validation exception when VALUE_LIMIT amount scale is invalid`() {
        val exception = assertFailsWith<ValidationException> {
            useCase.execute(
                name = "TEST",
                category = "VALUE_LIMIT",
                maxPerPayment = BigDecimal("500.001"),
                daytimeDailyLimit = BigDecimal("2000.00"),
                nighttimeDailyLimit = BigDecimal("500.00"),
                weekendDailyLimit = BigDecimal("500.00"),
                dailyTransactionLimit = null,
            )
        }
        assertEquals("maxPerPayment must have at most 2 decimal places", exception.message)
    }

    @Test
    fun `throws validation exception when TX_COUNT_LIMIT dailyTransactionLimit is null`() {
        val exception = assertFailsWith<ValidationException> {
            useCase.execute(
                name = "TEST",
                category = "TX_COUNT_LIMIT",
                maxPerPayment = null,
                daytimeDailyLimit = null,
                nighttimeDailyLimit = null,
                weekendDailyLimit = null,
                dailyTransactionLimit = null,
            )
        }
        assertEquals("dailyTransactionLimit is required for TX_COUNT_LIMIT", exception.message)
    }

    @Test
    fun `throws validation exception when TX_COUNT_LIMIT dailyTransactionLimit is zero`() {
        val exception = assertFailsWith<ValidationException> {
            useCase.execute(
                name = "TEST",
                category = "TX_COUNT_LIMIT",
                maxPerPayment = null,
                daytimeDailyLimit = null,
                nighttimeDailyLimit = null,
                weekendDailyLimit = null,
                dailyTransactionLimit = 0,
            )
        }
        assertEquals("dailyTransactionLimit must be greater than zero", exception.message)
    }

    @Test
    fun `throws validation exception when TX_COUNT_LIMIT dailyTransactionLimit is negative`() {
        val exception = assertFailsWith<ValidationException> {
            useCase.execute(
                name = "TEST",
                category = "TX_COUNT_LIMIT",
                maxPerPayment = null,
                daytimeDailyLimit = null,
                nighttimeDailyLimit = null,
                weekendDailyLimit = null,
                dailyTransactionLimit = -1,
            )
        }
        assertEquals("dailyTransactionLimit must be greater than zero", exception.message)
    }
}
