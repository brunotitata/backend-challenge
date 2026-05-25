package com.trace.payment.adapters.database.dao

import com.trace.payment.adapters.database.jooq.tables.PaymentIdempotencyKeys.PAYMENT_IDEMPOTENCY_KEYS
import com.trace.payment.boundary.database.IdempotencyRepositorySpec
import com.trace.payment.core.entities.IdempotencyRecord
import org.jooq.DSLContext
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class IdempotencyRepositoryImpl(
    private val dsl: DSLContext,
) : IdempotencyRepositorySpec {

    override fun findByWalletAndKey(walletId: UUID, idempotencyKey: String): IdempotencyRecord? {
        return dsl
            .selectFrom(PAYMENT_IDEMPOTENCY_KEYS)
            .where(PAYMENT_IDEMPOTENCY_KEYS.WALLET_ID.eq(walletId))
            .and(PAYMENT_IDEMPOTENCY_KEYS.IDEMPOTENCY_KEY.eq(idempotencyKey))
            .fetchOne { record ->
                IdempotencyRecord(
                    id = record.get(PAYMENT_IDEMPOTENCY_KEYS.ID),
                    walletId = record.get(PAYMENT_IDEMPOTENCY_KEYS.WALLET_ID),
                    idempotencyKey = record.get(PAYMENT_IDEMPOTENCY_KEYS.IDEMPOTENCY_KEY),
                    requestHash = record.get(PAYMENT_IDEMPOTENCY_KEYS.REQUEST_HASH),
                    paymentId = record.get(PAYMENT_IDEMPOTENCY_KEYS.PAYMENT_ID),
                    responseStatus = record.get(PAYMENT_IDEMPOTENCY_KEYS.RESPONSE_STATUS),
                    responseBody = record.get(PAYMENT_IDEMPOTENCY_KEYS.RESPONSE_BODY),
                    createdAt = record.get(PAYMENT_IDEMPOTENCY_KEYS.CREATED_AT).toInstant(),
                    updatedAt = record.get(PAYMENT_IDEMPOTENCY_KEYS.UPDATED_AT).toInstant(),
                )
            }
    }

    override fun save(record: IdempotencyRecord): IdempotencyRecord {
        val now = Instant.now()
        val saved = dsl
            .insertInto(
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
                record.id,
                record.walletId,
                record.idempotencyKey,
                record.requestHash,
                record.paymentId,
                record.responseStatus,
                record.responseBody,
                now.atOffset(ZoneOffset.UTC),
                now.atOffset(ZoneOffset.UTC),
            )
            .returning()
            .fetchOne() ?: error("Idempotency key insert did not return a row")

        return IdempotencyRecord(
            id = saved.get(PAYMENT_IDEMPOTENCY_KEYS.ID),
            walletId = saved.get(PAYMENT_IDEMPOTENCY_KEYS.WALLET_ID),
            idempotencyKey = saved.get(PAYMENT_IDEMPOTENCY_KEYS.IDEMPOTENCY_KEY),
            requestHash = saved.get(PAYMENT_IDEMPOTENCY_KEYS.REQUEST_HASH),
            paymentId = saved.get(PAYMENT_IDEMPOTENCY_KEYS.PAYMENT_ID),
            responseStatus = saved.get(PAYMENT_IDEMPOTENCY_KEYS.RESPONSE_STATUS),
            responseBody = saved.get(PAYMENT_IDEMPOTENCY_KEYS.RESPONSE_BODY),
            createdAt = saved.get(PAYMENT_IDEMPOTENCY_KEYS.CREATED_AT).toInstant(),
            updatedAt = saved.get(PAYMENT_IDEMPOTENCY_KEYS.UPDATED_AT).toInstant(),
        )
    }
}
