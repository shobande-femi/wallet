package com.isw.paple.common.states

import com.isw.paple.common.contracts.TransferReceiptContract
import com.isw.paple.common.schemas.TransferReceiptStateSchemaV1
import com.isw.paple.common.types.TransferStatus
import com.isw.paple.common.types.TransferType
import net.corda.core.contracts.Amount
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import java.time.Instant
import java.util.*


@BelongsToContract(TransferReceiptContract::class)
data class TransferReceiptState (
    val sender: Party,
    val recipient: Party,
    val senderWalletId: String,
    val recipientWalletId: String,
    val amount: Amount<Currency>,
    val type: TransferType,
    val status: TransferStatus,
    override val participants: List<AbstractParty>,
    override val linearId: UniqueIdentifier,
    val createdAt: Instant = Instant.now()
) : LinearState, QueryableState {


    constructor(
        sender: Party,
        recipient: Party,
        senderWalletId: String,
        recipientWalletId: String,
        amount: Amount<Currency>,
        type: TransferType,
        status: TransferStatus
    ) : this(sender, recipient, senderWalletId, recipientWalletId, amount, type, status, setOf(sender, recipient).toList(), UniqueIdentifier())


    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is TransferReceiptStateSchemaV1 -> TransferReceiptStateSchemaV1.PersistentTransferState(
                sender = sender.toString(),
                recipient = recipient.toString(),
                senderWalletId = senderWalletId,
                recipientWalletId = recipientWalletId,
                amount = amount.quantity.toString(),
                currency = amount.token.currencyCode,
                type = type.name,
                status = status.name,
                linearId = linearId.id.toString(),
                createdAt = createdAt.toEpochMilli()
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(TransferReceiptStateSchemaV1)

}