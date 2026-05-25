package com.trace.payment.core.usecase

import com.trace.payment.boundary.input.PolicyEvaluatorRegistrySpec
import com.trace.payment.boundary.input.PolicyEvaluatorSpec

class PolicyEvaluatorRegistryImpl : PolicyEvaluatorRegistrySpec {

    private val evaluators = mutableMapOf<String, PolicyEvaluatorSpec>()

    override fun register(category: String, evaluator: PolicyEvaluatorSpec) {
        evaluators[category] = evaluator
    }

    override fun get(category: String): PolicyEvaluatorSpec? {
        return evaluators[category]
    }
}
