package com.trace.payment.adapters.database.dao

import com.trace.payment.adapters.database.jooq.tables.Policies.POLICIES
import com.trace.payment.adapters.database.jooq.tables.WalletPolicies.WALLET_POLICIES
import com.trace.payment.adapters.database.jooq.tables.Wallets.WALLETS
import com.trace.payment.boundary.database.PolicyDAOSpec
import com.trace.payment.core.entities.PolicyEntity
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import java.time.ZoneOffset
import java.util.UUID

class PolicyDAOSpecImpl(
    private val dsl: DSLContext,
) : PolicyDAOSpec {

    override fun save(policy: PolicyEntity): PolicyEntity {
        val record = dsl
            .insertInto(POLICIES)
            .set(POLICIES.ID, policy.id)
            .set(POLICIES.NAME, policy.name)
            .set(POLICIES.CATEGORY, policy.category)
            .set(POLICIES.MAX_PER_PAYMENT, policy.maxPerPayment)
            .set(POLICIES.DAYTIME_DAILY_LIMIT, policy.daytimeDailyLimit)
            .set(POLICIES.NIGHTTIME_DAILY_LIMIT, policy.nighttimeDailyLimit)
            .set(POLICIES.WEEKEND_DAILY_LIMIT, policy.weekendDailyLimit)
            .set(POLICIES.DAILY_TRANSACTION_LIMIT, policy.dailyTransactionLimit)
            .set(POLICIES.CREATED_AT, policy.createdAt.atOffset(ZoneOffset.UTC))
            .set(POLICIES.UPDATED_AT, policy.updatedAt.atOffset(ZoneOffset.UTC))
            .returning()
            .fetchOne() ?: error("Policy insert did not return a row")

        return mapToPolicyEntity(record)
    }

    override fun findAll(): List<PolicyEntity> {
        return dsl
            .selectFrom(POLICIES)
            .orderBy(POLICIES.CREATED_AT.desc())
            .fetch { mapToPolicyEntity(it) }
    }

    override fun findByWalletId(walletId: UUID): List<PolicyEntity> {
        return dsl
            .select(POLICIES.fields().toList() + WALLET_POLICIES.ACTIVE)
            .from(POLICIES)
            .join(WALLET_POLICIES).on(WALLET_POLICIES.POLICY_ID.eq(POLICIES.ID))
            .where(WALLET_POLICIES.WALLET_ID.eq(walletId))
            .orderBy(WALLET_POLICIES.CREATED_AT.desc())
            .fetch { mapToPolicyEntity(it, it.get(WALLET_POLICIES.ACTIVE)) }
    }

    override fun findById(policyId: UUID): PolicyEntity? {
        return dsl
            .selectFrom(POLICIES)
            .where(POLICIES.ID.eq(policyId))
            .fetchOne { mapToPolicyEntity(it) }
    }

    override fun findActiveByWalletId(walletId: UUID): PolicyEntity? {
        return dsl
            .select(POLICIES.fields().toList())
            .from(WALLET_POLICIES)
            .join(POLICIES).on(POLICIES.ID.eq(WALLET_POLICIES.POLICY_ID))
            .where(WALLET_POLICIES.WALLET_ID.eq(walletId))
            .and(WALLET_POLICIES.ACTIVE.eq(true))
            .fetchOne { mapToPolicyEntity(it) }
    }

    override fun assignPolicy(walletId: UUID, policyId: UUID) {
        dsl.transaction { configuration ->
            val tx = DSL.using(configuration)

            tx.selectOne()
                .from(WALLETS)
                .where(WALLETS.ID.eq(walletId))
                .forUpdate()
                .fetchOne()

            tx.update(WALLET_POLICIES)
                .set(WALLET_POLICIES.ACTIVE, false)
                .where(WALLET_POLICIES.WALLET_ID.eq(walletId))
                .and(WALLET_POLICIES.ACTIVE.eq(true))
                .execute()

            tx.insertInto(WALLET_POLICIES)
                .set(WALLET_POLICIES.WALLET_ID, walletId)
                .set(WALLET_POLICIES.POLICY_ID, policyId)
                .set(WALLET_POLICIES.ACTIVE, true)
                .execute()
        }
    }

    private fun mapToPolicyEntity(record: Record, active: Boolean? = null): PolicyEntity {
        return PolicyEntity(
            id = record.get(POLICIES.ID),
            name = record.get(POLICIES.NAME),
            category = record.get(POLICIES.CATEGORY),
            maxPerPayment = record.get(POLICIES.MAX_PER_PAYMENT),
            daytimeDailyLimit = record.get(POLICIES.DAYTIME_DAILY_LIMIT),
            nighttimeDailyLimit = record.get(POLICIES.NIGHTTIME_DAILY_LIMIT),
            weekendDailyLimit = record.get(POLICIES.WEEKEND_DAILY_LIMIT),
            dailyTransactionLimit = record.get(POLICIES.DAILY_TRANSACTION_LIMIT),
            active = active,
            createdAt = record.get(POLICIES.CREATED_AT).toInstant(),
            updatedAt = record.get(POLICIES.UPDATED_AT).toInstant(),
        )
    }
}
