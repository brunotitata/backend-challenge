package com.trace.payment.core.usecase

import com.trace.payment.boundary.common.TransactionContext
import com.trace.payment.boundary.database.PaymentGatewaySpec
import com.trace.payment.boundary.database.WalletDAOSpec
import com.trace.payment.boundary.exceptions.NotFoundException
import com.trace.payment.boundary.exceptions.ValidationException
import com.trace.payment.core.entities.PaginationResult
import com.trace.payment.core.entities.PaymentEntity
import com.trace.payment.core.entities.PeriodType
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import com.trace.payment.core.entities.WalletEntity

class ListPaymentsUseCaseImplTest {

    private val walletId = UUID.randomUUID()
    private val now = Instant.parse("2024-08-26T10:00:00Z")

    private val payment = PaymentEntity(
        id = UUID.randomUUID(),
        walletId = walletId,
        policyId = UUID.randomUUID(),
        amount = BigDecimal("100.00"),
        occurredAt = now,
        periodType = PeriodType.DAYTIME,
        periodStart = now,
        status = "APPROVED",
        createdAt = now,
        updatedAt = now,
    )

    private fun createUseCase(
        walletExists: Boolean = true,
        paginationResult: PaginationResult<PaymentEntity> = PaginationResult(
            items = listOf(payment),
            nextCursor = null,
            previousCursor = null,
            total = 1,
        ),
    ): ListPaymentsUseCaseImpl {
        val walletDAO = object : WalletDAOSpec {
            override fun save(wallet: WalletEntity, tx: TransactionContext) = wallet
            override fun findActivePolicyName(walletId: UUID): String? = null
            override fun existsById(walletId: UUID): Boolean = walletExists
        }

        val gateway = object : PaymentGatewaySpec {
            override fun processPayment(
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
                tx: TransactionContext,
            ) = throw UnsupportedOperationException()

            override fun findById(paymentId: UUID): PaymentEntity? = null

            override fun findApprovedByWalletId(
                walletId: UUID,
                startDate: Instant?,
                endDate: Instant?,
                cursor: String?,
                limit: Int,
            ): PaginationResult<PaymentEntity> = paginationResult
        }

        return ListPaymentsUseCaseImpl(walletDAO, gateway)
    }

    @Test
    fun `returns payments for existing wallet`() {
        val useCase = createUseCase()
        val result = useCase.execute(walletId, null, null, null, 20)
        assertEquals(1, result.total)
        assertEquals(payment.id, result.items[0].id)
    }

    @Test
    fun `throws not found for non-existent wallet`() {
        val useCase = createUseCase(walletExists = false)
        val exception = assertFailsWith<NotFoundException> {
            useCase.execute(walletId, null, null, null, 20)
        }
        assertEquals("Wallet not found", exception.message)
    }

    @Test
    fun `throws validation when startDate is after endDate`() {
        val useCase = createUseCase()
        val start = Instant.parse("2024-08-27T00:00:00Z")
        val end = Instant.parse("2024-08-26T00:00:00Z")
        val exception = assertFailsWith<ValidationException> {
            useCase.execute(walletId, start, end, null, 20)
        }
        assertEquals("startDate must not be after endDate", exception.message)
    }

    @Test
    fun `passes cursor and limit to gateway`() {
        var passedCursor: String? = null
        var passedLimit: Int = 0

        val gateway = object : PaymentGatewaySpec {
            override fun processPayment(
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
                tx: TransactionContext,
            ) = throw UnsupportedOperationException()

            override fun findById(paymentId: UUID): PaymentEntity? = null

            override fun findApprovedByWalletId(
                walletId: UUID,
                startDate: Instant?,
                endDate: Instant?,
                cursor: String?,
                limit: Int,
            ): PaginationResult<PaymentEntity> {
                passedCursor = cursor
                passedLimit = limit
                return PaginationResult(emptyList(), null, null, 0)
            }
        }

        val walletDAO = object : WalletDAOSpec {
            override fun save(wallet: WalletEntity, tx: TransactionContext) = wallet
            override fun findActivePolicyName(walletId: UUID): String? = null
            override fun existsById(walletId: UUID): Boolean = true
        }

        val useCase = ListPaymentsUseCaseImpl(walletDAO, gateway)
        useCase.execute(walletId, null, null, "cursor123", 50)

        assertEquals("cursor123", passedCursor)
        assertEquals(50, passedLimit)
    }

    @Test
    fun `accepts equal startDate and endDate`() {
        val useCase = createUseCase()
        val date = Instant.parse("2024-08-26T00:00:00Z")
        val result = useCase.execute(walletId, date, date, null, 20)
        assertEquals(1, result.total)
    }
}
