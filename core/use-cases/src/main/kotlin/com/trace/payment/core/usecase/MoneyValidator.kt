package com.trace.payment.core.usecase

import com.trace.payment.boundary.exceptions.ValidationException
import java.math.BigDecimal

object MoneyValidator {
    private const val MAX_PRECISION = 19
    private const val MAX_SCALE = 2

    fun requireValid(field: String, value: BigDecimal) {
        if (value.scale() > MAX_SCALE) {
            throw ValidationException("$field must have at most $MAX_SCALE decimal places")
        }

        if (value.precision() > MAX_PRECISION) {
            throw ValidationException("$field must fit NUMERIC($MAX_PRECISION, $MAX_SCALE)")
        }
    }
}
