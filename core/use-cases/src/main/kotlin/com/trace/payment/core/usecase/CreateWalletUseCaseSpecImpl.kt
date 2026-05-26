package com.trace.payment.core.usecase

import com.trace.payment.boundary.common.OutboxEventBO
import com.trace.payment.boundary.database.OutboxGatewaySpec
import com.trace.payment.boundary.database.TransactionManagerSpec
import com.trace.payment.boundary.database.WalletDAOSpec
import com.trace.payment.boundary.input.CreateWalletUseCaseSpec
import com.trace.payment.boundary.exceptions.ValidationException
import com.trace.payment.core.entities.WalletEntity
import com.trace.payment.core.usecase.events.WalletCreatedEvent
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID

class CreateWalletUseCaseSpecImpl(
    private val walletDAO: WalletDAOSpec,
    private val outboxGateway: OutboxGatewaySpec,
    private val transactionManager: TransactionManagerSpec,
) : CreateWalletUseCaseSpec {

    override fun execute(ownerName: String): WalletEntity {

        if (ownerName.isBlank()) {
            throw ValidationException("ownerName must not be blank")
        }

        val wallet = WalletEntity(
            id = UUID.randomUUID(),
            ownerName = ownerName.trim(),
            createdAt = Instant.now(),
        )

        return transactionManager.runInTransaction { tx ->
            val savedWallet = walletDAO.save(wallet, tx)
            val payload = Json.encodeToString(WalletCreatedEvent.serializer(), WalletCreatedEvent(savedWallet.id.toString(), savedWallet.ownerName))
            outboxGateway.save(
                OutboxEventBO(
                    aggregateType = "wallet",
                    aggregateId = savedWallet.id.toString(),
                    eventType = "WALLET_CREATED",
                    payload = payload,
                ),
                tx,
            )
            savedWallet
        }
    }

}
