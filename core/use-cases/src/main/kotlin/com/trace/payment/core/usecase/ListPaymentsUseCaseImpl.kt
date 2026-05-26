package com.trace.payment.core.usecase

import com.trace.payment.boundary.database.PaymentGatewaySpec
import com.trace.payment.boundary.database.WalletDAOSpec
import com.trace.payment.boundary.exceptions.NotFoundException
import com.trace.payment.boundary.exceptions.ValidationException
import com.trace.payment.boundary.input.ListPaymentsUseCaseSpec
import com.trace.payment.core.entities.PaginationResult
import com.trace.payment.core.entities.PaymentEntity
import java.time.Instant
import java.util.UUID

class ListPaymentsUseCaseImpl(
    private val walletDAO: WalletDAOSpec,
    private val paymentGateway: PaymentGatewaySpec,
) : ListPaymentsUseCaseSpec {

    override fun execute(
        walletId: UUID,
        startDate: Instant?,
        endDate: Instant?,
        cursor: String?,
        limit: Int,
    ): PaginationResult<PaymentEntity> {
        if (!walletDAO.existsById(walletId)) {
            throw NotFoundException("Wallet not found")
        }

        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw ValidationException("startDate must not be after endDate")
        }

        return paymentGateway.findApprovedByWalletId(walletId, startDate, endDate, cursor, limit)
    }
}
