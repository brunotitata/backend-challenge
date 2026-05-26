package com.trace.payment.adapters.database.dao

import com.trace.payment.adapters.database.jooq.tables.Policies.POLICIES
import com.trace.payment.adapters.database.jooq.tables.WalletPolicies.WALLET_POLICIES
import com.trace.payment.adapters.database.jooq.tables.Wallets.WALLETS
import com.trace.payment.boundary.database.WalletDAOSpec
import com.trace.payment.core.entities.WalletEntity
import org.jooq.DSLContext
import org.jooq.impl.DSL
import java.math.BigDecimal
import java.time.ZoneOffset
import java.util.UUID
import com.trace.payment.adapters.database.gateway.JooqTransactionContext
import com.trace.payment.boundary.common.TransactionContext

class WalletDAOSpecImpl(
    private val dsl: DSLContext,
) : WalletDAOSpec {

    override fun save(wallet: WalletEntity, tx: TransactionContext): WalletEntity {
        val jooqTx = tx as JooqTransactionContext
        ensureDefaultPolicy(jooqTx.dsl)
        val savedWallet = insertWallet(jooqTx.dsl, wallet)
        insertDefaultActivePolicy(jooqTx.dsl, savedWallet.id)
        return savedWallet
    }

    override fun findActivePolicyName(walletId: UUID): String? {
        return dsl
            .select(POLICIES.NAME)
            .from(WALLET_POLICIES)
            .join(POLICIES).on(POLICIES.ID.eq(WALLET_POLICIES.POLICY_ID))
            .where(WALLET_POLICIES.WALLET_ID.eq(walletId))
            .and(WALLET_POLICIES.ACTIVE.eq(true))
            .fetchOne(POLICIES.NAME)
    }

    override fun existsById(walletId: UUID): Boolean {
        return dsl.fetchExists(
            dsl.selectOne()
                .from(WALLETS)
                .where(WALLETS.ID.eq(walletId)),
        )
    }

    private fun insertWallet(tx: DSLContext, wallet: WalletEntity): WalletEntity {
        val record = tx
            .insertInto(WALLETS)
            .set(WALLETS.ID, wallet.id)
            .set(WALLETS.OWNER_NAME, wallet.ownerName)
            .set(WALLETS.CREATED_AT, wallet.createdAt.atOffset(ZoneOffset.UTC))
            .returning(WALLETS.ID, WALLETS.CREATED_AT)
            .fetchOne() ?: error("Wallet insert did not return a row")

        return WalletEntity(
            id = record.get(WALLETS.ID),
            ownerName = wallet.ownerName,
            createdAt = record.get(WALLETS.CREATED_AT).toInstant(),
        )
    }

    private fun insertDefaultActivePolicy(tx: DSLContext, walletId: UUID) {
        val affectedRows = tx
            .insertInto(WALLET_POLICIES, WALLET_POLICIES.WALLET_ID, WALLET_POLICIES.POLICY_ID, WALLET_POLICIES.ACTIVE)
            .select(
                tx.select(
                    DSL.`val`(walletId),
                    POLICIES.ID,
                    DSL.`val`(true),
                )
                    .from(POLICIES)
                    .where(POLICIES.NAME.eq("DEFAULT_VALUE_LIMIT")),
            )
            .execute()

        check(affectedRows == 1) {
            "Default policy DEFAULT_VALUE_LIMIT not found"
        }
    }

    private fun ensureDefaultPolicy(tx: DSLContext) {
        tx.insertInto(POLICIES)
            .columns(
                POLICIES.NAME,
                POLICIES.CATEGORY,
                POLICIES.MAX_PER_PAYMENT,
                POLICIES.DAYTIME_DAILY_LIMIT,
                POLICIES.NIGHTTIME_DAILY_LIMIT,
                POLICIES.WEEKEND_DAILY_LIMIT,
            )
            .select(
                tx.select(
                    DSL.`val`("DEFAULT_VALUE_LIMIT"),
                    DSL.`val`("VALUE_LIMIT"),
                    DSL.`val`(BigDecimal("1000.00")),
                    DSL.`val`(BigDecimal("4000.00")),
                    DSL.`val`(BigDecimal("1000.00")),
                    DSL.`val`(BigDecimal("1000.00")),
                ),
            )
            .onConflict(POLICIES.NAME)
            .doNothing()
            .execute()
    }
}
