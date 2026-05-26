package com.trace.payment.core.usecase

import com.trace.payment.boundary.database.WalletDAOSpec
import com.trace.payment.boundary.exceptions.ValidationException
import com.trace.payment.core.entities.WalletEntity
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CreateWalletUseCaseSpecImplTest {

    private val savedWallets = mutableListOf<WalletEntity>()

    private val walletDAO = object : WalletDAOSpec {
        override fun save(wallet: WalletEntity): WalletEntity {
            savedWallets.add(wallet)
            return wallet
        }

        override fun findActivePolicyName(walletId: UUID): String? = null
        override fun existsById(walletId: UUID): Boolean = false
    }

    private val useCase = CreateWalletUseCaseSpecImpl(walletDAO)

    @Test
    fun `creates wallet with trimmed ownerName`() {
        val result = useCase.execute("  Maria Silva  ")
        assertEquals("Maria Silva", result.ownerName)
        assertEquals(1, savedWallets.size)
        assertEquals("Maria Silva", savedWallets[0].ownerName)
    }

    @Test
    fun `generates UUID for new wallet`() {
        val result = useCase.execute("João")
        assertEquals(36, result.id.toString().length)
    }

    @Test
    fun `sets createdAt timestamp`() {
        val before = Instant.now()
        val result = useCase.execute("João")
        val after = Instant.now()
        assert(result.createdAt.isAfter(before) || result.createdAt == before)
        assert(result.createdAt.isBefore(after) || result.createdAt == after)
    }

    @Test
    fun `throws validation exception for blank ownerName`() {
        val exception = assertFailsWith<ValidationException> {
            useCase.execute("   ")
        }
        assertEquals("ownerName must not be blank", exception.message)
    }

    @Test
    fun `throws validation exception for empty ownerName`() {
        val exception = assertFailsWith<ValidationException> {
            useCase.execute("")
        }
        assertEquals("ownerName must not be blank", exception.message)
    }
}
