package com.trace.payment.boundary.input

import com.trace.payment.core.entities.WalletEntity

interface CreateWalletUseCaseSpec {
    fun execute(ownerName: String): WalletEntity
}
