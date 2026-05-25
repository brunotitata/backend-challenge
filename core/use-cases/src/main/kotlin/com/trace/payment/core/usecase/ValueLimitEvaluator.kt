package com.trace.payment.core.usecase

import com.trace.payment.boundary.input.PolicyEvaluatorSpec
import com.trace.payment.core.entities.PolicyEntity

class ValueLimitEvaluator : PolicyEvaluatorSpec {

    override fun evaluate(policy: PolicyEntity, context: Map<String, Any?>): Boolean {
        return true
    }
}
