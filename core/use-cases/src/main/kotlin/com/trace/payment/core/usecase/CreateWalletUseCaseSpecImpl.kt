package com.trace.payment.core.usecase

import com.trace.payment.boundary.database.WalletDAOSpec
import com.trace.payment.boundary.input.CreateWalletUseCaseSpec
import com.trace.payment.boundary.exceptions.ValidationException
import com.trace.payment.core.entities.WalletEntity
import java.time.Instant
import java.util.UUID

class CreateWalletUseCaseSpecImpl(
    private val walletDAO: WalletDAOSpec,
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

        return walletDAO.save(wallet)
    }

}
