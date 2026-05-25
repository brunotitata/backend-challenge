package com.trace.payment.adapters.database.gateway

import com.trace.payment.adapters.database.jooq.tables.LimitConsumptions.LIMIT_CONSUMPTIONS
import com.trace.payment.adapters.database.jooq.tables.Payments.PAYMENTS
import com.trace.payment.boundary.database.PaymentGatewaySpec
import com.trace.payment.core.entities.PaymentEntity
import com.trace.payment.core.entities.PeriodType
import org.jooq.DSLContext
import org.jooq.impl.DSL
import java.math.BigDecimal
import java.time.Instant
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
        checkLimit: (consumedAmount: BigDecimal) -> Boolean,
    ): PaymentEntity? {
        return dsl.transactionResult { configuration ->
            val tx = DSL.using(configuration)

            val consumedAmount = tx
                .select(LIMIT_CONSUMPTIONS.CONSUMED_AMOUNT)
                .from(LIMIT_CONSUMPTIONS)
                .where(LIMIT_CONSUMPTIONS.WALLET_ID.eq(walletId))
                .and(LIMIT_CONSUMPTIONS.POLICY_ID.eq(policyId))
                .and(LIMIT_CONSUMPTIONS.PERIOD_TYPE.eq(periodType.name))
                .and(LIMIT_CONSUMPTIONS.PERIOD_START.eq(periodStart.atOffset(ZoneOffset.UTC)))
                .forUpdate()
                .fetchOne { it.get(LIMIT_CONSUMPTIONS.CONSUMED_AMOUNT) } ?: BigDecimal.ZERO

            if (!checkLimit(consumedAmount)) {
                return@transactionResult null
            }

            val now = Instant.now()

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
                .set(PAYMENTS.CREATED_AT, now.atOffset(ZoneOffset.UTC))
                .set(PAYMENTS.UPDATED_AT, now.atOffset(ZoneOffset.UTC))
                .returning()
                .fetchOne() ?: error("Payment insert did not return a row")

            tx.insertInto(LIMIT_CONSUMPTIONS,
                LIMIT_CONSUMPTIONS.WALLET_ID,
                LIMIT_CONSUMPTIONS.POLICY_ID,
                LIMIT_CONSUMPTIONS.PERIOD_TYPE,
                LIMIT_CONSUMPTIONS.PERIOD_START,
                LIMIT_CONSUMPTIONS.CONSUMED_AMOUNT,
            )
                .values(walletId, policyId, periodType.name, periodStart.atOffset(ZoneOffset.UTC), amount)
                .onConflict(LIMIT_CONSUMPTIONS.WALLET_ID, LIMIT_CONSUMPTIONS.POLICY_ID, LIMIT_CONSUMPTIONS.PERIOD_TYPE, LIMIT_CONSUMPTIONS.PERIOD_START)
                .doUpdate()
                .set(LIMIT_CONSUMPTIONS.CONSUMED_AMOUNT, LIMIT_CONSUMPTIONS.CONSUMED_AMOUNT.plus(amount))
                .execute()

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
}
