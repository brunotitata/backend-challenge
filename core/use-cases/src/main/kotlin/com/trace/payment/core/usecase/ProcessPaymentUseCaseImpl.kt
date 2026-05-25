package com.trace.payment.core.usecase

import com.trace.payment.boundary.database.IdempotencyRepositorySpec
import com.trace.payment.boundary.database.PaymentGatewaySpec
import com.trace.payment.boundary.database.WalletDAOSpec
import com.trace.payment.boundary.exceptions.ConflictException
import com.trace.payment.boundary.exceptions.NotFoundException
import com.trace.payment.boundary.exceptions.UnprocessableEntityException
import com.trace.payment.boundary.exceptions.ValidationException
import com.trace.payment.boundary.input.PolicyEvaluatorRegistrySpec
import com.trace.payment.boundary.input.PolicyResolverSpec
import com.trace.payment.boundary.input.ProcessPaymentUseCaseSpec
import com.trace.payment.core.entities.PeriodClassifier
import com.trace.payment.core.entities.PaymentEntity
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class ProcessPaymentUseCaseImpl(
    private val walletDAO: WalletDAOSpec,
    private val policyResolver: PolicyResolverSpec,
    private val policyEvaluatorRegistry: PolicyEvaluatorRegistrySpec,
    private val paymentGateway: PaymentGatewaySpec,
    private val idempotencyRepository: IdempotencyRepositorySpec,
) : ProcessPaymentUseCaseSpec {

    private val zone = ZoneId.of("America/Sao_Paulo")

    override fun execute(walletId: UUID, amount: BigDecimal, occurredAt: Instant, idempotencyKey: String): PaymentEntity {
        if (amount <= BigDecimal.ZERO) {
            throw ValidationException("amount must be greater than zero")
        }

        if (!walletDAO.existsById(walletId)) {
            throw NotFoundException("Wallet not found")
        }

        val existingKey = idempotencyRepository.findByWalletAndKey(walletId, idempotencyKey)
        if (existingKey != null) {
            val hash = RequestHashUtil.computeHash(amount, occurredAt)
            if (existingKey.requestHash != hash) {
                throw ConflictException("Idempotency key already used with different payload")
            }
            if (existingKey.responseStatus == 422) {
                throw UnprocessableEntityException("Payment rejected: limit exceeded")
            }
            return paymentGateway.findById(existingKey.paymentId!!)
                ?: throw NotFoundException("Original payment not found")
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

        val payment = paymentGateway.processPaymentInTransaction(
            walletId = walletId,
            policyId = policy.id,
            amount = amount,
            occurredAt = occurredAt,
            periodType = classification.periodType,
            periodStart = classification.periodStart,
            checkLimit = { consumedAmount ->
                val result = evaluator.evaluate(policy, amount, consumedAmount, classification.periodType)
                result.approved
            },
        )

        if (payment == null) {
            val hash = RequestHashUtil.computeHash(amount, occurredAt)
            idempotencyRepository.save(
                com.trace.payment.core.entities.IdempotencyRecord(
                    id = UUID.randomUUID(),
                    walletId = walletId,
                    idempotencyKey = idempotencyKey,
                    requestHash = hash,
                    paymentId = null,
                    responseStatus = 422,
                    responseBody = null,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                ),
            )
            throw UnprocessableEntityException("Payment rejected: limit exceeded")
        }

        val hash = RequestHashUtil.computeHash(amount, occurredAt)
        idempotencyRepository.save(
            com.trace.payment.core.entities.IdempotencyRecord(
                id = UUID.randomUUID(),
                walletId = walletId,
                idempotencyKey = idempotencyKey,
                requestHash = hash,
                paymentId = payment.id,
                responseStatus = 201,
                responseBody = null,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            ),
        )

        return payment
    }
}
