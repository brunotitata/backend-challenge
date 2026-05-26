package com.trace.payment.adapters.database.gateway

import com.trace.payment.adapters.database.jooq.tables.OutboxEvents.OUTBOX_EVENTS
import com.trace.payment.boundary.common.OutboxEventBO
import com.trace.payment.boundary.common.TransactionContext
import com.trace.payment.boundary.database.OutboxGatewaySpec
import org.jooq.DSLContext
import org.jooq.impl.DSL
import java.time.ZoneOffset
import java.util.UUID

class JooqTransactionContext(
    val dsl: DSLContext,
) : TransactionContext

class OutboxGatewayImpl(
    private val dsl: DSLContext,
) : OutboxGatewaySpec {

    override fun save(event: OutboxEventBO) {
        insert(dsl, event)
    }

    override fun save(event: OutboxEventBO, tx: TransactionContext) {
        val jooqTx = tx as JooqTransactionContext
        insert(jooqTx.dsl, event)
    }

    private fun insert(ctx: DSLContext, event: OutboxEventBO) {
        ctx.insertInto(OUTBOX_EVENTS)
            .set(OUTBOX_EVENTS.ID, event.id)
            .set(OUTBOX_EVENTS.AGGREGATE_TYPE, event.aggregateType)
            .set(OUTBOX_EVENTS.AGGREGATE_ID, event.aggregateId)
            .set(OUTBOX_EVENTS.EVENT_TYPE, event.eventType)
            .set(OUTBOX_EVENTS.PAYLOAD, DSL.inline(event.payload).cast(OUTBOX_EVENTS.PAYLOAD.dataType))
            .set(OUTBOX_EVENTS.CREATED_AT, event.createdAt.atOffset(ZoneOffset.UTC))
            .set(OUTBOX_EVENTS.PROCESSED_AT, event.processedAt?.atOffset(ZoneOffset.UTC))
            .set(OUTBOX_EVENTS.RETRY_COUNT, event.retryCount)
            .execute()
    }

    override fun findUnprocessed(limit: Int): List<OutboxEventBO> {
        return dsl.selectFrom(OUTBOX_EVENTS)
            .where(OUTBOX_EVENTS.PROCESSED_AT.isNull)
            .orderBy(OUTBOX_EVENTS.CREATED_AT.asc())
            .limit(limit)
            .fetch { record ->
                OutboxEventBO(
                    id = record.get(OUTBOX_EVENTS.ID),
                    aggregateType = record.get(OUTBOX_EVENTS.AGGREGATE_TYPE),
                    aggregateId = record.get(OUTBOX_EVENTS.AGGREGATE_ID),
                    eventType = record.get(OUTBOX_EVENTS.EVENT_TYPE),
                    payload = record.get(OUTBOX_EVENTS.PAYLOAD).data(),
                    createdAt = record.get(OUTBOX_EVENTS.CREATED_AT).toInstant(),
                    processedAt = record.get(OUTBOX_EVENTS.PROCESSED_AT)?.toInstant(),
                    retryCount = record.get(OUTBOX_EVENTS.RETRY_COUNT),
                )
            }
    }

    override fun markAsProcessed(id: UUID) {
        dsl.update(OUTBOX_EVENTS)
            .set(OUTBOX_EVENTS.PROCESSED_AT, DSL.offsetDateTime(DSL.currentOffsetDateTime()))
            .where(OUTBOX_EVENTS.ID.eq(id))
            .execute()
    }

    override fun incrementRetry(id: UUID) {
        dsl.update(OUTBOX_EVENTS)
            .set(OUTBOX_EVENTS.RETRY_COUNT, OUTBOX_EVENTS.RETRY_COUNT.plus(1))
            .where(OUTBOX_EVENTS.ID.eq(id))
            .execute()
    }
}
