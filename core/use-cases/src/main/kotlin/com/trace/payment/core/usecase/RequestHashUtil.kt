package com.trace.payment.core.usecase

import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Instant

object RequestHashUtil {
    fun computeHash(amount: BigDecimal, occurredAt: Instant): String {
        val input = "${amount.stripTrailingZeros().toPlainString()}|${occurredAt.toString()}"
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
