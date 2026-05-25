package com.trace.payment.adapters.database.gateway

import com.trace.payment.adapters.database.jooq.tables.LimitConsumptions.LIMIT_CONSUMPTIONS
import com.trace.payment.adapters.database.jooq.tables.PaymentIdempotencyKeys.PAYMENT_IDEMPOTENCY_KEYS
import com.trace.payment.adapters.database.jooq.tables.Payments.PAYMENTS
import com.trace.payment.boundary.database.PaymentGatewaySpec
import com.trace.payment.boundary.database.TransactionResult
import com.trace.payment.core.entities.PaymentEntity
import com.trace.payment.core.entities.PeriodType
import org.jooq.DSLContext
import org.jooq.impl.DSL
import java.math.BigDecimal
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class PaymentGatewayImpl(
    private val dsl: DSLContext,
) : PaymentGatewaySpec {

    override fun processPaymentInTransaction(
        walletId: UUID,
        policyId: UUID,
        amount: BigDecimal,
        occurredAt: Instant,
        periodType: PeriodType,
        periodStart: Instant,
        idempotencyKey: String,
        requestHash: String,
        checkLimit: (consumedAmount: BigDecimal) -> Boolean,
    ): TransactionResult {
        return dsl.transactionResult { configuration ->
            val tx = DSL.using(configuration)
            val now = OffsetDateTime.now()

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
            )
                .values(walletId, policyId, periodType.name, periodStart.atOffset(ZoneOffset.UTC), BigDecimal.ZERO)
                .onConflict(
                    LIMIT_CONSUMPTIONS.WALLET_ID,
                    LIMIT_CONSUMPTIONS.POLICY_ID,
                    LIMIT_CONSUMPTIONS.PERIOD_TYPE,
                    LIMIT_CONSUMPTIONS.PERIOD_START,
                )
                .doNothing()
                .execute()

            val consumedAmount = tx
                .select(LIMIT_CONSUMPTIONS.CONSUMED_AMOUNT)
                .from(LIMIT_CONSUMPTIONS)
                .where(LIMIT_CONSUMPTIONS.WALLET_ID.eq(walletId))
                .and(LIMIT_CONSUMPTIONS.POLICY_ID.eq(policyId))
                .and(LIMIT_CONSUMPTIONS.PERIOD_TYPE.eq(periodType.name))
                .and(LIMIT_CONSUMPTIONS.PERIOD_START.eq(periodStart.atOffset(ZoneOffset.UTC)))
                .forUpdate()
                .fetchOne { it.get(LIMIT_CONSUMPTIONS.CONSUMED_AMOUNT) }
                ?: BigDecimal.ZERO

            if (!checkLimit(consumedAmount)) {
                tx.update(PAYMENT_IDEMPOTENCY_KEYS)
                    .set(PAYMENT_IDEMPOTENCY_KEYS.RESPONSE_STATUS, 422)
                    .set(PAYMENT_IDEMPOTENCY_KEYS.UPDATED_AT, OffsetDateTime.now())
                    .where(PAYMENT_IDEMPOTENCY_KEYS.WALLET_ID.eq(walletId))
                    .and(PAYMENT_IDEMPOTENCY_KEYS.IDEMPOTENCY_KEY.eq(idempotencyKey))
                    .execute()
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
            )
                .values(walletId, policyId, periodType.name, periodStart.atOffset(ZoneOffset.UTC), amount)
                .onConflict(
                    LIMIT_CONSUMPTIONS.WALLET_ID,
                    LIMIT_CONSUMPTIONS.POLICY_ID,
                    LIMIT_CONSUMPTIONS.PERIOD_TYPE,
                    LIMIT_CONSUMPTIONS.PERIOD_START,
                )
                .doUpdate()
                .set(LIMIT_CONSUMPTIONS.CONSUMED_AMOUNT, LIMIT_CONSUMPTIONS.CONSUMED_AMOUNT.plus(amount))
                .execute()

            tx.update(PAYMENT_IDEMPOTENCY_KEYS)
                .set(PAYMENT_IDEMPOTENCY_KEYS.PAYMENT_ID, paymentRecord.get(PAYMENTS.ID))
                .set(PAYMENT_IDEMPOTENCY_KEYS.RESPONSE_STATUS, 201)
                .set(PAYMENT_IDEMPOTENCY_KEYS.UPDATED_AT, OffsetDateTime.now())
                .where(PAYMENT_IDEMPOTENCY_KEYS.WALLET_ID.eq(walletId))
                .and(PAYMENT_IDEMPOTENCY_KEYS.IDEMPOTENCY_KEY.eq(idempotencyKey))
                .execute()

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
