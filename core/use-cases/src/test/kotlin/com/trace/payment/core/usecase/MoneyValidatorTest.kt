package com.trace.payment.core.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import java.math.BigDecimal
import com.trace.payment.boundary.exceptions.ValidationException

class MoneyValidatorTest {

    @Test
    fun `accepts valid amount with two decimal places`() {
        MoneyValidator.requireValid("amount", BigDecimal("100.00"))
    }

    @Test
    fun `accepts valid amount with one decimal place`() {
        MoneyValidator.requireValid("amount", BigDecimal("100.5"))
    }

    @Test
    fun `accepts valid integer amount`() {
        MoneyValidator.requireValid("amount", BigDecimal("100"))
    }

    @Test
    fun `rejects amount with more than two decimal places`() {
        val exception = assertFailsWith<ValidationException> {
            MoneyValidator.requireValid("amount", BigDecimal("100.001"))
        }
        assertEquals("amount must have at most 2 decimal places", exception.message)
    }

    @Test
    fun `rejects amount with three decimal places`() {
        val exception = assertFailsWith<ValidationException> {
            MoneyValidator.requireValid("amount", BigDecimal("0.999"))
        }
        assertEquals("amount must have at most 2 decimal places", exception.message)
    }

    @Test
    fun `rejects amount exceeding numeric precision`() {
        val exception = assertFailsWith<ValidationException> {
            MoneyValidator.requireValid("amount", BigDecimal("999999999999999999.99"))
        }
        assertEquals("amount must fit NUMERIC(19, 2)", exception.message)
    }

    @Test
    fun `accepts amount at max precision boundary`() {
        MoneyValidator.requireValid("amount", BigDecimal("99999999999999999.99"))
    }

    @Test
    fun `rejects very large amount with many digits`() {
        val exception = assertFailsWith<ValidationException> {
            MoneyValidator.requireValid("amount", BigDecimal("12345678901234567890.00"))
        }
        assertEquals("amount must fit NUMERIC(19, 2)", exception.message)
    }

    @Test
    fun `uses field name in error message`() {
        val exception = assertFailsWith<ValidationException> {
            MoneyValidator.requireValid("daytimeDailyLimit", BigDecimal("100.001"))
        }
        assertEquals("daytimeDailyLimit must have at most 2 decimal places", exception.message)
    }

    @Test
    fun `accepts zero amount`() {
        MoneyValidator.requireValid("amount", BigDecimal.ZERO)
    }

    @Test
    fun `accepts negative amount regarding precision`() {
        MoneyValidator.requireValid("amount", BigDecimal("-100.00"))
    }
}
