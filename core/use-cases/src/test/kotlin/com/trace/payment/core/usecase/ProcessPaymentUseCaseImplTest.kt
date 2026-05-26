package com.trace.payment.core.usecase

import com.trace.payment.boundary.database.PaymentGatewaySpec
import com.trace.payment.boundary.database.TransactionResult
import com.trace.payment.boundary.database.WalletDAOSpec
import com.trace.payment.boundary.exceptions.ConflictException
import com.trace.payment.boundary.exceptions.NotFoundException
import com.trace.payment.boundary.exceptions.UnprocessableEntityException
import com.trace.payment.boundary.exceptions.ValidationException
import com.trace.payment.boundary.input.EvaluationResult
import com.trace.payment.boundary.input.PolicyEvaluatorRegistrySpec
import com.trace.payment.boundary.input.PolicyEvaluatorSpec
import com.trace.payment.boundary.input.PolicyResolverSpec
import com.trace.payment.core.entities.PaymentEntity
import com.trace.payment.core.entities.PeriodType
import com.trace.payment.core.entities.PolicyEntity
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class ProcessPaymentUseCaseImplTest {

    private val walletId = UUID.randomUUID()
    private val policyId = UUID.randomUUID()
    private val paymentId = UUID.randomUUID()
    private val now = Instant.parse("2024-08-26T10:00:00Z")

    private val valueLimitPolicy = PolicyEntity(
        id = policyId,
        name = "DEFAULT_VALUE_LIMIT",
        category = "VALUE_LIMIT",
        maxPerPayment = BigDecimal("1000.00"),
        daytimeDailyLimit = BigDecimal("4000.00"),
        nighttimeDailyLimit = BigDecimal("1000.00"),
        weekendDailyLimit = BigDecimal("1000.00"),
        dailyTransactionLimit = null,
        createdAt = now,
        updatedAt = now,
    )

    private val txCountPolicy = PolicyEntity(
        id = policyId,
        name = "TX_LIMIT",
        category = "TX_COUNT_LIMIT",
        maxPerPayment = null,
        daytimeDailyLimit = null,
        nighttimeDailyLimit = null,
        weekendDailyLimit = null,
        dailyTransactionLimit = 5,
        createdAt = now,
        updatedAt = now,
    )

    private fun createUseCase(
        walletExists: Boolean = true,
        resolvedPolicy: PolicyEntity? = valueLimitPolicy,
        evaluator: PolicyEvaluatorSpec? = object : PolicyEvaluatorSpec {
            override fun evaluate(
                policy: PolicyEntity,
                amount: BigDecimal,
                consumedAmount: BigDecimal,
                periodType: PeriodType,
                transactionCount: Int,
            ): EvaluationResult = EvaluationResult(true)
        },
        transactionResult: TransactionResult = TransactionResult.Approved(
            PaymentEntity(
                id = paymentId,
                walletId = walletId,
                policyId = policyId,
                amount = BigDecimal("500.00"),
                occurredAt = now,
                periodType = PeriodType.DAYTIME,
                periodStart = now,
                status = "APPROVED",
                createdAt = now,
                updatedAt = now,
            )
        ),
    ): ProcessPaymentUseCaseImpl {
        val walletDAO = object : WalletDAOSpec {
            override fun save(wallet: com.trace.payment.core.entities.WalletEntity) = wallet
            override fun findActivePolicyName(walletId: UUID): String? = null
            override fun existsById(walletId: UUID): Boolean = walletExists
        }

        val policyResolver = object : PolicyResolverSpec {
            override fun resolve(walletId: UUID): PolicyEntity? = resolvedPolicy
        }

        val registry = object : PolicyEvaluatorRegistrySpec {
            override fun register(category: String, evaluator: PolicyEvaluatorSpec) {}
            override fun get(category: String): PolicyEvaluatorSpec? = evaluator
        }

        val gateway = object : PaymentGatewaySpec {
            override fun processPaymentInTransaction(
                walletId: UUID,
                policyId: UUID,
                amount: BigDecimal,
                occurredAt: Instant,
                periodType: PeriodType,
                periodStart: Instant,
                idempotencyKey: String,
                requestHash: String,
                requestId: String?,
                checkLimit: (consumedAmount: BigDecimal, transactionCount: Int) -> Boolean,
            ): TransactionResult = transactionResult

            override fun findById(paymentId: UUID): PaymentEntity? = null

            override fun findApprovedByWalletId(
                walletId: UUID,
                startDate: Instant?,
                endDate: Instant?,
                cursor: String?,
                limit: Int,
            ) = throw UnsupportedOperationException()
        }

        return ProcessPaymentUseCaseImpl(walletDAO, policyResolver, registry, gateway)
    }

    @Test
    fun `approves payment within limits`() {
        val useCase = createUseCase()
        val result = useCase.execute(walletId, BigDecimal("500.00"), now, "idem-1", null)
        assertEquals(paymentId, result.id)
        assertEquals("APPROVED", result.status)
    }

    @Test
    fun `rejects amount equal to zero`() {
        val useCase = createUseCase()
        val exception = assertFailsWith<ValidationException> {
            useCase.execute(walletId, BigDecimal.ZERO, now, "idem-1", null)
        }
        assertEquals("amount must be greater than zero", exception.message)
    }

    @Test
    fun `rejects negative amount`() {
        val useCase = createUseCase()
        val exception = assertFailsWith<ValidationException> {
            useCase.execute(walletId, BigDecimal("-100.00"), now, "idem-1", null)
        }
        assertEquals("amount must be greater than zero", exception.message)
    }

    @Test
    fun `rejects amount with more than two decimal places`() {
        val useCase = createUseCase()
        val exception = assertFailsWith<ValidationException> {
            useCase.execute(walletId, BigDecimal("100.001"), now, "idem-1", null)
        }
        assertEquals("amount must have at most 2 decimal places", exception.message)
    }

    @Test
    fun `throws not found when wallet does not exist`() {
        val useCase = createUseCase(walletExists = false)
        val exception = assertFailsWith<NotFoundException> {
            useCase.execute(walletId, BigDecimal("100.00"), now, "idem-1", null)
        }
        assertEquals("Wallet not found", exception.message)
    }

    @Test
    fun `throws unprocessable when wallet has no active policy`() {
        val useCase = createUseCase(resolvedPolicy = null)
        val exception = assertFailsWith<UnprocessableEntityException> {
            useCase.execute(walletId, BigDecimal("100.00"), now, "idem-1", null)
        }
        assertEquals("Wallet has no active policy", exception.message)
    }

    @Test
    fun `rejects amount above maxPerPayment`() {
        val useCase = createUseCase()
        val exception = assertFailsWith<ValidationException> {
            useCase.execute(walletId, BigDecimal("1500.00"), now, "idem-1", null)
        }
        assertEquals("amount exceeds maxPerPayment of 1000.00", exception.message)
    }

    @Test
    fun `accepts amount exactly at maxPerPayment`() {
        val approvedPayment = PaymentEntity(
            id = paymentId,
            walletId = walletId,
            policyId = policyId,
            amount = BigDecimal("1000.00"),
            occurredAt = now,
            periodType = PeriodType.DAYTIME,
            periodStart = now,
            status = "APPROVED",
            createdAt = now,
            updatedAt = now,
        )
        val useCase = createUseCase(transactionResult = TransactionResult.Approved(approvedPayment))
        val result = useCase.execute(walletId, BigDecimal("1000.00"), now, "idem-max", null)
        assertEquals(BigDecimal("1000.00"), result.amount)
    }

    @Test
    fun `throws when no evaluator found for policy category`() {
        val useCase = createUseCase(
            resolvedPolicy = valueLimitPolicy.copy(category = "UNKNOWN"),
            evaluator = null,
        )
        val exception = assertFailsWith<UnprocessableEntityException> {
            useCase.execute(walletId, BigDecimal("100.00"), now, "idem-1", null)
        }
        assertEquals("No evaluator found for policy category: UNKNOWN", exception.message)
    }

    @Test
    fun `throws unprocessable when gateway returns Rejected`() {
        val useCase = createUseCase(transactionResult = TransactionResult.Rejected)
        val exception = assertFailsWith<UnprocessableEntityException> {
            useCase.execute(walletId, BigDecimal("100.00"), now, "idem-1", null)
        }
        assertEquals("Payment rejected: limit exceeded", exception.message)
    }

    @Test
    fun `throws conflict when gateway returns Conflict`() {
        val useCase = createUseCase(transactionResult = TransactionResult.Conflict)
        val exception = assertFailsWith<ConflictException> {
            useCase.execute(walletId, BigDecimal("100.00"), now, "idem-1", null)
        }
        assertEquals("Idempotency key already used with different payload", exception.message)
    }

    @Test
    fun `returns payment on idempotent replay with approved status`() {
        val replayPayment = PaymentEntity(
            id = paymentId,
            walletId = walletId,
            policyId = policyId,
            amount = BigDecimal("100.00"),
            occurredAt = now,
            periodType = PeriodType.DAYTIME,
            periodStart = now,
            status = "APPROVED",
            createdAt = now,
            updatedAt = now,
        )
        val useCase = createUseCase(transactionResult = TransactionResult.IdempotentReplay(201, replayPayment))
        val result = useCase.execute(walletId, BigDecimal("100.00"), now, "idem-replay", null)
        assertEquals(paymentId, result.id)
    }

    @Test
    fun `throws unprocessable on idempotent replay with 422 status`() {
        val useCase = createUseCase(transactionResult = TransactionResult.IdempotentReplay(422, null))
        val exception = assertFailsWith<UnprocessableEntityException> {
            useCase.execute(walletId, BigDecimal("100.00"), now, "idem-replay-422", null)
        }
        assertEquals("Payment rejected: limit exceeded", exception.message)
    }

    @Test
    fun `throws not found on idempotent replay without payment`() {
        val useCase = createUseCase(transactionResult = TransactionResult.IdempotentReplay(201, null))
        val exception = assertFailsWith<NotFoundException> {
            useCase.execute(walletId, BigDecimal("100.00"), now, "idem-replay-empty", null)
        }
        assertEquals("Original payment not found", exception.message)
    }

    @Test
    fun `uses start of day as period for TX_COUNT_LIMIT policy`() {
        var capturedPeriodType: PeriodType? = null
        var capturedPeriodStart: Instant? = null

        val gateway = object : PaymentGatewaySpec {
            override fun processPaymentInTransaction(
                walletId: UUID,
                policyId: UUID,
                amount: BigDecimal,
                occurredAt: Instant,
                periodType: PeriodType,
                periodStart: Instant,
                idempotencyKey: String,
                requestHash: String,
                requestId: String?,
                checkLimit: (consumedAmount: BigDecimal, transactionCount: Int) -> Boolean,
            ): TransactionResult {
                capturedPeriodType = periodType
                capturedPeriodStart = periodStart
                return TransactionResult.Rejected
            }

            override fun findById(paymentId: UUID): PaymentEntity? = null
            override fun findApprovedByWalletId(walletId: UUID, startDate: Instant?, endDate: Instant?, cursor: String?, limit: Int) =
                throw UnsupportedOperationException()
        }

        val walletDAO = object : WalletDAOSpec {
            override fun save(wallet: com.trace.payment.core.entities.WalletEntity) = wallet
            override fun findActivePolicyName(walletId: UUID): String? = null
            override fun existsById(walletId: UUID): Boolean = true
        }

        val policyResolver = object : PolicyResolverSpec {
            override fun resolve(walletId: UUID): PolicyEntity? = txCountPolicy
        }

        val registry = object : PolicyEvaluatorRegistrySpec {
            override fun register(category: String, evaluator: PolicyEvaluatorSpec) {}
            override fun get(category: String): PolicyEvaluatorSpec? = object : PolicyEvaluatorSpec {
                override fun evaluate(policy: PolicyEntity, amount: BigDecimal, consumedAmount: BigDecimal, periodType: PeriodType, transactionCount: Int) =
                    EvaluationResult(false)
            }
        }

        val useCase = ProcessPaymentUseCaseImpl(walletDAO, policyResolver, registry, gateway)

        assertFailsWith<UnprocessableEntityException> {
            useCase.execute(walletId, BigDecimal("10.00"), now, "idem-tx", null)
        }

        assertEquals(PeriodType.DAYTIME, capturedPeriodType)
        assertNotNull(capturedPeriodStart)
    }

    @Test
    fun `evaluator receives correct periodType from classification`() {
        var receivedPeriodType: PeriodType? = null

        val gateway = object : PaymentGatewaySpec {
            override fun processPaymentInTransaction(
                walletId: UUID,
                policyId: UUID,
                amount: BigDecimal,
                occurredAt: Instant,
                periodType: PeriodType,
                periodStart: Instant,
                idempotencyKey: String,
                requestHash: String,
                requestId: String?,
                checkLimit: (consumedAmount: BigDecimal, transactionCount: Int) -> Boolean,
            ): TransactionResult {
                checkLimit(BigDecimal.ZERO, 0)
                return TransactionResult.Rejected
            }

            override fun findById(paymentId: UUID): PaymentEntity? = null
            override fun findApprovedByWalletId(walletId: UUID, startDate: Instant?, endDate: Instant?, cursor: String?, limit: Int) =
                throw UnsupportedOperationException()
        }

        val walletDAO = object : WalletDAOSpec {
            override fun save(wallet: com.trace.payment.core.entities.WalletEntity) = wallet
            override fun findActivePolicyName(walletId: UUID): String? = null
            override fun existsById(walletId: UUID): Boolean = true
        }

        val policyResolver = object : PolicyResolverSpec {
            override fun resolve(walletId: UUID): PolicyEntity? = valueLimitPolicy
        }

        val registry = object : PolicyEvaluatorRegistrySpec {
            override fun register(category: String, evaluator: PolicyEvaluatorSpec) {}
            override fun get(category: String): PolicyEvaluatorSpec? = object : PolicyEvaluatorSpec {
                override fun evaluate(policy: PolicyEntity, amount: BigDecimal, consumedAmount: BigDecimal, periodType: PeriodType, transactionCount: Int): EvaluationResult {
                    receivedPeriodType = periodType
                    return EvaluationResult(false)
                }
            }
        }

        val useCase = ProcessPaymentUseCaseImpl(walletDAO, policyResolver, registry, gateway)
        assertFailsWith<UnprocessableEntityException> {
            useCase.execute(walletId, BigDecimal("100.00"), now, "idem-eval", null)
        }

        assertEquals(PeriodType.DAYTIME, receivedPeriodType)
    }
}
