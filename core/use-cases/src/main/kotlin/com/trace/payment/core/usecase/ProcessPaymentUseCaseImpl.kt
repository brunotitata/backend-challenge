package com.trace.payment.core.usecase

import com.trace.payment.boundary.common.OutboxEventBO
import com.trace.payment.boundary.database.OutboxGatewaySpec
import com.trace.payment.boundary.database.PaymentGatewaySpec
import com.trace.payment.boundary.database.TransactionManagerSpec
import com.trace.payment.boundary.database.TransactionResult
import com.trace.payment.boundary.database.WalletDAOSpec
import com.trace.payment.boundary.exceptions.ConflictException
import com.trace.payment.boundary.exceptions.NotFoundException
import com.trace.payment.boundary.exceptions.UnprocessableEntityException
import com.trace.payment.boundary.exceptions.ValidationException
import com.trace.payment.boundary.input.PolicyEvaluatorRegistrySpec
import com.trace.payment.boundary.input.PolicyResolverSpec
import com.trace.payment.boundary.input.ProcessPaymentUseCaseSpec
import com.trace.payment.core.entities.PeriodClassifier
import com.trace.payment.core.entities.PeriodType
import com.trace.payment.core.entities.PaymentEntity
import com.trace.payment.core.usecase.events.PaymentApprovedEvent
import com.trace.payment.core.usecase.events.PaymentRejectedEvent
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class ProcessPaymentUseCaseImpl(
    private val walletDAO: WalletDAOSpec,
    private val policyResolver: PolicyResolverSpec,
    private val policyEvaluatorRegistry: PolicyEvaluatorRegistrySpec,
    private val paymentGateway: PaymentGatewaySpec,
    private val outboxGateway: OutboxGatewaySpec,
    private val transactionManager: TransactionManagerSpec,
) : ProcessPaymentUseCaseSpec {

    private val zone = ZoneId.of("America/Sao_Paulo")

    override fun execute(
        walletId: UUID,
        amount: BigDecimal,
        occurredAt: Instant,
        idempotencyKey: String,
        requestId: String?,
    ): PaymentEntity {
        MoneyValidator.requireValid("amount", amount)

        if (amount <= BigDecimal.ZERO) {
            throw ValidationException("amount must be greater than zero")
        }

        if (!walletDAO.existsById(walletId)) {
            throw NotFoundException("Wallet not found")
        }

        val policy = policyResolver.resolve(walletId)
            ?: throw UnprocessableEntityException("Wallet has no active policy")

        val maxPerPayment = policy.maxPerPayment
        if (maxPerPayment != null && amount > maxPerPayment) {
            throw ValidationException("amount exceeds maxPerPayment of $maxPerPayment")
        }

        val evaluator = policyEvaluatorRegistry.get(policy.category)
            ?: throw UnprocessableEntityException("No evaluator found for policy category: ${policy.category}")

        val classification = PeriodClassifier.classify(occurredAt, zone)
        val hash = RequestHashUtil.computeHash(amount, occurredAt)

        val (limitPeriodType, limitPeriodStart) = if (policy.category == "TX_COUNT_LIMIT") {
            val startOfDay = occurredAt.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
            PeriodType.DAYTIME to startOfDay
        } else {
            classification.periodType to classification.periodStart
        }

        return transactionManager.runInTransaction { tx ->
            val result = paymentGateway.processPayment(
                walletId = walletId,
                policyId = policy.id,
                amount = amount,
                occurredAt = occurredAt,
                periodType = limitPeriodType,
                periodStart = limitPeriodStart,
                idempotencyKey = idempotencyKey,
                requestHash = hash,
                requestId = requestId,
                checkLimit = { consumedAmount, transactionCount ->
                    val evaluation = evaluator.evaluate(policy, amount, consumedAmount, classification.periodType, transactionCount)
                    evaluation.approved
                },
                tx = tx,
            )

            when (result) {
                is TransactionResult.Approved -> {
                    val payload = Json.encodeToString(
                        PaymentApprovedEvent.serializer(),
                        PaymentApprovedEvent(
                            id = result.payment.id.toString(),
                            walletId = result.payment.walletId.toString(),
                            policyId = result.payment.policyId.toString(),
                            amount = result.payment.amount.toString(),
                            status = result.payment.status,
                            occurredAt = result.payment.occurredAt.toString(),
                        ),
                    )
                    outboxGateway.save(
                        OutboxEventBO(
                            aggregateType = "payment",
                            aggregateId = result.payment.id.toString(),
                            eventType = "PAYMENT_APPROVED",
                            payload = payload,
                        ),
                        tx,
                    )
                    result.payment
                }

                is TransactionResult.Rejected -> {
                    val payload = Json.encodeToString(
                        PaymentRejectedEvent.serializer(),
                        PaymentRejectedEvent(
                            walletId = walletId.toString(),
                            policyId = policy.id.toString(),
                            amount = amount.toString(),
                            idempotencyKey = idempotencyKey,
                            reason = "LIMIT_EXCEEDED",
                        ),
                    )
                    outboxGateway.save(
                        OutboxEventBO(
                            aggregateType = "payment",
                            aggregateId = walletId.toString(),
                            eventType = "PAYMENT_REJECTED",
                            payload = payload,
                        ),
                        tx,
                    )
                    throw UnprocessableEntityException("Payment rejected: limit exceeded")
                }

                is TransactionResult.Conflict -> throw ConflictException("Idempotency key already used with different payload")

                is TransactionResult.IdempotentReplay -> {
                    if (result.statusCode == 422) {
                        throw UnprocessableEntityException("Payment rejected: limit exceeded")
                    }
                    result.payment ?: throw NotFoundException("Original payment not found")
                }
            }
        }
    }
}
