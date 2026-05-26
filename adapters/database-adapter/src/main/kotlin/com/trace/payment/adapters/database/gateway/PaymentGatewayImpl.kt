package com.trace.payment.adapters.database.gateway

import com.trace.payment.adapters.database.jooq.tables.LimitConsumptions.LIMIT_CONSUMPTIONS
import com.trace.payment.adapters.database.jooq.tables.PaymentAuditEvents.PAYMENT_AUDIT_EVENTS
import com.trace.payment.adapters.database.jooq.tables.PaymentIdempotencyKeys.PAYMENT_IDEMPOTENCY_KEYS
import com.trace.payment.adapters.database.jooq.tables.Payments.PAYMENTS
import com.trace.payment.adapters.database.jooq.tables.Wallets.WALLETS
import com.trace.payment.boundary.database.PaymentGatewaySpec
import com.trace.payment.boundary.database.TransactionResult
import com.trace.payment.core.entities.Cursor
import com.trace.payment.core.entities.PaginationResult
import com.trace.payment.core.entities.PaymentEntity
import com.trace.payment.core.entities.PeriodType
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class PaymentGatewayImpl(
    private val dsl: DSLContext,
) : PaymentGatewaySpec {

    private val logger = LoggerFactory.getLogger(PaymentGatewayImpl::class.java)

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
        return dsl.transactionResult { configuration ->
            val tx = DSL.using(configuration)
            val now = OffsetDateTime.now()

            tx.selectOne()
                .from(WALLETS)
                .where(WALLETS.ID.eq(walletId))
                .forUpdate()
                .fetchOne()

            val idempotencyInserted = tx.insertInto(
                PAYMENT_IDEMPOTENCY_KEYS,
                PAYMENT_IDEMPOTENCY_KEYS.ID,
                PAYMENT_IDEMPOTENCY_KEYS.WALLET_ID,
                PAYMENT_IDEMPOTENCY_KEYS.IDEMPOTENCY_KEY,
                PAYMENT_IDEMPOTENCY_KEYS.REQUEST_HASH,
                PAYMENT_IDEMPOTENCY_KEYS.PAYMENT_ID,
                PAYMENT_IDEMPOTENCY_KEYS.RESPONSE_STATUS,
                PAYMENT_IDEMPOTENCY_KEYS.RESPONSE_BODY,
                PAYMENT_IDEMPOTENCY_KEYS.CREATED_AT,
                PAYMENT_IDEMPOTENCY_KEYS.UPDATED_AT,
            )
                .values(
                    UUID.randomUUID(),
                    walletId,
                    idempotencyKey,
                    requestHash,
                    null,
                    0,
                    null,
                    now,
                    now,
                )
                .onConflict(
                    PAYMENT_IDEMPOTENCY_KEYS.WALLET_ID,
                    PAYMENT_IDEMPOTENCY_KEYS.IDEMPOTENCY_KEY,
                )
                .doNothing()
                .execute() == 1

            if (!idempotencyInserted) {
                val existing = tx.selectFrom(PAYMENT_IDEMPOTENCY_KEYS)
                    .where(PAYMENT_IDEMPOTENCY_KEYS.WALLET_ID.eq(walletId))
                    .and(PAYMENT_IDEMPOTENCY_KEYS.IDEMPOTENCY_KEY.eq(idempotencyKey))
                    .fetchOne()
                    ?: error("Idempotency key not found after ON CONFLICT")

                return@transactionResult if (existing.get(PAYMENT_IDEMPOTENCY_KEYS.REQUEST_HASH) == requestHash) {
                    val paymentId = existing.get(PAYMENT_IDEMPOTENCY_KEYS.PAYMENT_ID)
                    val status = existing.get(PAYMENT_IDEMPOTENCY_KEYS.RESPONSE_STATUS) ?: 422
                    val payment = paymentId?.let { findByIdInternal(tx, it) }
                    TransactionResult.IdempotentReplay(status, payment)
                } else {
                    TransactionResult.Conflict
                }
            }

            tx.insertInto(
                LIMIT_CONSUMPTIONS,
                LIMIT_CONSUMPTIONS.WALLET_ID,
                LIMIT_CONSUMPTIONS.POLICY_ID,
                LIMIT_CONSUMPTIONS.PERIOD_TYPE,
                LIMIT_CONSUMPTIONS.PERIOD_START,
                LIMIT_CONSUMPTIONS.CONSUMED_AMOUNT,
                LIMIT_CONSUMPTIONS.TRANSACTION_COUNT,
            )
                .values(walletId, policyId, periodType.name, periodStart.atOffset(ZoneOffset.UTC), BigDecimal.ZERO, 0)
                .onConflict(
                    LIMIT_CONSUMPTIONS.WALLET_ID,
                    LIMIT_CONSUMPTIONS.POLICY_ID,
                    LIMIT_CONSUMPTIONS.PERIOD_TYPE,
                    LIMIT_CONSUMPTIONS.PERIOD_START,
                )
                .doNothing()
                .execute()

            val consumptionRecord = tx
                .select(LIMIT_CONSUMPTIONS.CONSUMED_AMOUNT, LIMIT_CONSUMPTIONS.TRANSACTION_COUNT)
                .from(LIMIT_CONSUMPTIONS)
                .where(LIMIT_CONSUMPTIONS.WALLET_ID.eq(walletId))
                .and(LIMIT_CONSUMPTIONS.POLICY_ID.eq(policyId))
                .and(LIMIT_CONSUMPTIONS.PERIOD_TYPE.eq(periodType.name))
                .and(LIMIT_CONSUMPTIONS.PERIOD_START.eq(periodStart.atOffset(ZoneOffset.UTC)))
                .forUpdate()
                .fetchOne()

            val consumedAmount = consumptionRecord?.get(LIMIT_CONSUMPTIONS.CONSUMED_AMOUNT) ?: BigDecimal.ZERO
            val transactionCount = consumptionRecord?.get(LIMIT_CONSUMPTIONS.TRANSACTION_COUNT) ?: 0

            if (!checkLimit(consumedAmount, transactionCount)) {
                tx.update(PAYMENT_IDEMPOTENCY_KEYS)
                    .set(PAYMENT_IDEMPOTENCY_KEYS.RESPONSE_STATUS, 422)
                    .set(PAYMENT_IDEMPOTENCY_KEYS.UPDATED_AT, OffsetDateTime.now())
                    .where(PAYMENT_IDEMPOTENCY_KEYS.WALLET_ID.eq(walletId))
                    .and(PAYMENT_IDEMPOTENCY_KEYS.IDEMPOTENCY_KEY.eq(idempotencyKey))
                    .execute()

                tx.insertInto(PAYMENT_AUDIT_EVENTS)
                    .set(PAYMENT_AUDIT_EVENTS.WALLET_ID, walletId)
                    .set(PAYMENT_AUDIT_EVENTS.PAYMENT_ID, null as UUID?)
                    .set(PAYMENT_AUDIT_EVENTS.POLICY_ID, policyId)
                    .set(PAYMENT_AUDIT_EVENTS.IDEMPOTENCY_KEY, idempotencyKey)
                    .set(PAYMENT_AUDIT_EVENTS.REQUEST_ID, requestId)
                    .set(PAYMENT_AUDIT_EVENTS.AMOUNT, amount)
                    .set(PAYMENT_AUDIT_EVENTS.STATUS, "REJECTED")
                    .set(PAYMENT_AUDIT_EVENTS.REASON, "LIMIT_EXCEEDED")
                    .set(PAYMENT_AUDIT_EVENTS.OCCURRED_AT, now)
                    .set(PAYMENT_AUDIT_EVENTS.CREATED_AT, now)
                    .execute()

                logger.info("Payment rejected: walletId={}, amount={}, reason=LIMIT_EXCEEDED", walletId, amount)

                return@transactionResult TransactionResult.Rejected
            }

            val paymentRecord = tx
                .insertInto(PAYMENTS)
                .set(PAYMENTS.ID, UUID.randomUUID())
                .set(PAYMENTS.WALLET_ID, walletId)
                .set(PAYMENTS.POLICY_ID, policyId)
                .set(PAYMENTS.AMOUNT, amount)
                .set(PAYMENTS.OCCURRED_AT, occurredAt.atOffset(ZoneOffset.UTC))
                .set(PAYMENTS.PERIOD_TYPE, periodType.name)
                .set(PAYMENTS.PERIOD_START, periodStart.atOffset(ZoneOffset.UTC))
                .set(PAYMENTS.STATUS, "APPROVED")
                .set(PAYMENTS.CREATED_AT, now)
                .set(PAYMENTS.UPDATED_AT, now)
                .returning()
                .fetchOne() ?: error("Payment insert did not return a row")

            tx.insertInto(
                LIMIT_CONSUMPTIONS,
                LIMIT_CONSUMPTIONS.WALLET_ID,
                LIMIT_CONSUMPTIONS.POLICY_ID,
                LIMIT_CONSUMPTIONS.PERIOD_TYPE,
                LIMIT_CONSUMPTIONS.PERIOD_START,
                LIMIT_CONSUMPTIONS.CONSUMED_AMOUNT,
                LIMIT_CONSUMPTIONS.TRANSACTION_COUNT,
            )
                .values(walletId, policyId, periodType.name, periodStart.atOffset(ZoneOffset.UTC), amount, 1)
                .onConflict(
                    LIMIT_CONSUMPTIONS.WALLET_ID,
                    LIMIT_CONSUMPTIONS.POLICY_ID,
                    LIMIT_CONSUMPTIONS.PERIOD_TYPE,
                    LIMIT_CONSUMPTIONS.PERIOD_START,
                )
                .doUpdate()
                .set(LIMIT_CONSUMPTIONS.CONSUMED_AMOUNT, LIMIT_CONSUMPTIONS.CONSUMED_AMOUNT.plus(amount))
                .set(LIMIT_CONSUMPTIONS.TRANSACTION_COUNT, LIMIT_CONSUMPTIONS.TRANSACTION_COUNT.plus(1))
                .execute()

            tx.update(PAYMENT_IDEMPOTENCY_KEYS)
                .set(PAYMENT_IDEMPOTENCY_KEYS.PAYMENT_ID, paymentRecord.get(PAYMENTS.ID))
                .set(PAYMENT_IDEMPOTENCY_KEYS.RESPONSE_STATUS, 201)
                .set(PAYMENT_IDEMPOTENCY_KEYS.UPDATED_AT, OffsetDateTime.now())
                .where(PAYMENT_IDEMPOTENCY_KEYS.WALLET_ID.eq(walletId))
                .and(PAYMENT_IDEMPOTENCY_KEYS.IDEMPOTENCY_KEY.eq(idempotencyKey))
                .execute()

            tx.insertInto(PAYMENT_AUDIT_EVENTS)
                .set(PAYMENT_AUDIT_EVENTS.WALLET_ID, walletId)
                .set(PAYMENT_AUDIT_EVENTS.PAYMENT_ID, paymentRecord.get(PAYMENTS.ID))
                .set(PAYMENT_AUDIT_EVENTS.POLICY_ID, policyId)
                .set(PAYMENT_AUDIT_EVENTS.IDEMPOTENCY_KEY, idempotencyKey)
                .set(PAYMENT_AUDIT_EVENTS.REQUEST_ID, requestId)
                .set(PAYMENT_AUDIT_EVENTS.AMOUNT, amount)
                .set(PAYMENT_AUDIT_EVENTS.STATUS, "APPROVED")
                .set(PAYMENT_AUDIT_EVENTS.REASON, null as String?)
                .set(PAYMENT_AUDIT_EVENTS.OCCURRED_AT, now)
                .set(PAYMENT_AUDIT_EVENTS.CREATED_AT, now)
                .execute()

            logger.info("Payment approved: walletId={}, paymentId={}, amount={}", walletId, paymentRecord.get(PAYMENTS.ID), amount)

            TransactionResult.Approved(
                PaymentEntity(
                    id = paymentRecord.get(PAYMENTS.ID),
                    walletId = paymentRecord.get(PAYMENTS.WALLET_ID),
                    policyId = paymentRecord.get(PAYMENTS.POLICY_ID),
                    amount = paymentRecord.get(PAYMENTS.AMOUNT),
                    occurredAt = paymentRecord.get(PAYMENTS.OCCURRED_AT).toInstant(),
                    periodType = PeriodType.valueOf(paymentRecord.get(PAYMENTS.PERIOD_TYPE)),
                    periodStart = paymentRecord.get(PAYMENTS.PERIOD_START).toInstant(),
                    status = paymentRecord.get(PAYMENTS.STATUS),
                    createdAt = paymentRecord.get(PAYMENTS.CREATED_AT).toInstant(),
                    updatedAt = paymentRecord.get(PAYMENTS.UPDATED_AT).toInstant(),
                ),
            )
        }
    }

    override fun findById(paymentId: UUID): PaymentEntity? {
        return dsl
            .selectFrom(PAYMENTS)
            .where(PAYMENTS.ID.eq(paymentId))
            .fetchOne { record ->
                PaymentEntity(
                    id = record.get(PAYMENTS.ID),
                    walletId = record.get(PAYMENTS.WALLET_ID),
                    policyId = record.get(PAYMENTS.POLICY_ID),
                    amount = record.get(PAYMENTS.AMOUNT),
                    occurredAt = record.get(PAYMENTS.OCCURRED_AT).toInstant(),
                    periodType = PeriodType.valueOf(record.get(PAYMENTS.PERIOD_TYPE)),
                    periodStart = record.get(PAYMENTS.PERIOD_START).toInstant(),
                    status = record.get(PAYMENTS.STATUS),
                    createdAt = record.get(PAYMENTS.CREATED_AT).toInstant(),
                    updatedAt = record.get(PAYMENTS.UPDATED_AT).toInstant(),
                )
            }
    }

    override fun findApprovedByWalletId(
        walletId: UUID,
        startDate: Instant?,
        endDate: Instant?,
        cursor: String?,
        limit: Int,
    ): PaginationResult<PaymentEntity> {
        val baseConditions = mutableListOf<Condition>(
            PAYMENTS.WALLET_ID.eq(walletId),
            PAYMENTS.STATUS.eq("APPROVED"),
        )

        if (startDate != null) {
            baseConditions.add(PAYMENTS.OCCURRED_AT.ge(startDate.atOffset(ZoneOffset.UTC)))
        }
        if (endDate != null) {
            baseConditions.add(PAYMENTS.OCCURRED_AT.le(endDate.atOffset(ZoneOffset.UTC)))
        }

        val conditions = baseConditions.toMutableList()
        val parsedCursor = cursor?.let { Cursor.decode(it) }

        if (parsedCursor != null) {
            if (parsedCursor.direction == Cursor.Direction.FWD) {
                conditions.add(
                    PAYMENTS.OCCURRED_AT.gt(parsedCursor.occurredAt.atOffset(ZoneOffset.UTC))
                        .or(
                            PAYMENTS.OCCURRED_AT.eq(parsedCursor.occurredAt.atOffset(ZoneOffset.UTC))
                                .and(PAYMENTS.ID.gt(parsedCursor.id)),
                        ),
                )
            } else {
                conditions.add(
                    PAYMENTS.OCCURRED_AT.lt(parsedCursor.occurredAt.atOffset(ZoneOffset.UTC))
                        .or(
                            PAYMENTS.OCCURRED_AT.eq(parsedCursor.occurredAt.atOffset(ZoneOffset.UTC))
                                .and(PAYMENTS.ID.lt(parsedCursor.id)),
                        ),
                )
            }
        }

        val isBackward = parsedCursor?.direction == Cursor.Direction.BWD
        val orderField = if (isBackward) {
            PAYMENTS.OCCURRED_AT.desc()
        } else {
            PAYMENTS.OCCURRED_AT.asc()
        }
        val orderId = if (isBackward) {
            PAYMENTS.ID.desc()
        } else {
            PAYMENTS.ID.asc()
        }

        val items = dsl
            .selectFrom(PAYMENTS)
            .where(conditions)
            .orderBy(orderField, orderId)
            .limit(limit + 1)
            .fetch { record ->
                PaymentEntity(
                    id = record.get(PAYMENTS.ID),
                    walletId = record.get(PAYMENTS.WALLET_ID),
                    policyId = record.get(PAYMENTS.POLICY_ID),
                    amount = record.get(PAYMENTS.AMOUNT),
                    occurredAt = record.get(PAYMENTS.OCCURRED_AT).toInstant(),
                    periodType = PeriodType.valueOf(record.get(PAYMENTS.PERIOD_TYPE)),
                    periodStart = record.get(PAYMENTS.PERIOD_START).toInstant(),
                    status = record.get(PAYMENTS.STATUS),
                    createdAt = record.get(PAYMENTS.CREATED_AT).toInstant(),
                    updatedAt = record.get(PAYMENTS.UPDATED_AT).toInstant(),
                )
            }

        if (isBackward) {
            items.reverse()
        }

        val hasNext = items.size > limit
        val pageItems = if (hasNext) items.take(limit) else items

        val nextCursor = if (hasNext) {
            val last = pageItems.last()
            Cursor(last.occurredAt, last.id, Cursor.Direction.FWD).encode()
        } else {
            null
        }

        val previousCursor = if (pageItems.isNotEmpty() && cursor != null) {
            val first = pageItems.first()
            Cursor(first.occurredAt, first.id, Cursor.Direction.BWD).encode()
        } else {
            null
        }

        val total = dsl
            .selectCount()
            .from(PAYMENTS)
            .where(baseConditions)
            .fetchOne { it.get(0) as Int }
            ?: 0

        return PaginationResult(
            items = pageItems,
            nextCursor = nextCursor,
            previousCursor = previousCursor,
            total = total,
        )
    }

    private fun findByIdInternal(tx: DSLContext, paymentId: UUID): PaymentEntity? {
        return tx
            .selectFrom(PAYMENTS)
            .where(PAYMENTS.ID.eq(paymentId))
            .fetchOne { record ->
                PaymentEntity(
                    id = record.get(PAYMENTS.ID),
                    walletId = record.get(PAYMENTS.WALLET_ID),
                    policyId = record.get(PAYMENTS.POLICY_ID),
                    amount = record.get(PAYMENTS.AMOUNT),
                    occurredAt = record.get(PAYMENTS.OCCURRED_AT).toInstant(),
                    periodType = PeriodType.valueOf(record.get(PAYMENTS.PERIOD_TYPE)),
                    periodStart = record.get(PAYMENTS.PERIOD_START).toInstant(),
                    status = record.get(PAYMENTS.STATUS),
                    createdAt = record.get(PAYMENTS.CREATED_AT).toInstant(),
                    updatedAt = record.get(PAYMENTS.UPDATED_AT).toInstant(),
                )
            }
    }
}
