package com.trace.payment.core.usecase

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class RequestHashUtilTest {

    @Test
    fun `same input produces same hash`() {
        val amount = BigDecimal("100.00")
        val occurredAt = Instant.parse("2024-08-26T10:00:00.00Z")
        val hash1 = RequestHashUtil.computeHash(amount, occurredAt)
        val hash2 = RequestHashUtil.computeHash(amount, occurredAt)
        assertEquals(hash1, hash2)
    }

    @Test
    fun `different amounts produce different hashes`() {
        val occurredAt = Instant.parse("2024-08-26T10:00:00.00Z")
        val hash1 = RequestHashUtil.computeHash(BigDecimal("100.00"), occurredAt)
        val hash2 = RequestHashUtil.computeHash(BigDecimal("200.00"), occurredAt)
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `different timestamps produce different hashes`() {
        val amount = BigDecimal("100.00")
        val hash1 = RequestHashUtil.computeHash(amount, Instant.parse("2024-08-26T10:00:00.00Z"))
        val hash2 = RequestHashUtil.computeHash(amount, Instant.parse("2024-08-26T11:00:00.00Z"))
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `hash is 64 character hex string`() {
        val hash = RequestHashUtil.computeHash(BigDecimal("100.00"), Instant.parse("2024-08-26T10:00:00.00Z"))
        assertEquals(64, hash.length)
        assert(hash.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `trailing zeros are normalized`() {
        val occurredAt = Instant.parse("2024-08-26T10:00:00.00Z")
        val hash1 = RequestHashUtil.computeHash(BigDecimal("100.00"), occurredAt)
        val hash2 = RequestHashUtil.computeHash(BigDecimal("100.0"), occurredAt)
        assertEquals(hash1, hash2)
    }
}
