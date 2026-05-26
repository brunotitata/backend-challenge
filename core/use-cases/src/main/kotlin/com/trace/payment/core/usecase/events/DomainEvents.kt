package com.trace.payment.core.usecase.events

import kotlinx.serialization.Serializable

@Serializable
data class WalletCreatedEvent(
    val id: String,
    val ownerName: String,
)

@Serializable
data class PolicyCreatedEvent(
    val id: String,
    val name: String,
    val category: String,
    val maxPerPayment: String? = null,
    val daytimeDailyLimit: String? = null,
    val nighttimeDailyLimit: String? = null,
    val weekendDailyLimit: String? = null,
    val dailyTransactionLimit: Int? = null,
)

@Serializable
data class PolicyAssignedEvent(
    val walletId: String,
    val policyId: String,
)

@Serializable
data class PaymentApprovedEvent(
    val id: String,
    val walletId: String,
    val policyId: String,
    val amount: String,
    val status: String,
    val occurredAt: String,
)

@Serializable
data class PaymentRejectedEvent(
    val walletId: String,
    val policyId: String,
    val amount: String,
    val idempotencyKey: String,
    val reason: String,
)
