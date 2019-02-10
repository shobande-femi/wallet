package com.example.wallet.common.schemas

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

object TransferReceiptStateSchema

object TransferReceiptStateSchemaV1 : MappedSchema(
    schemaFamily = TransferReceiptStateSchema.javaClass,
    version = 1,
    mappedTypes = listOf(PersistentTransferState::class.java)
) {

    @Entity
    @Table(name = "transfer_states")
    class PersistentTransferState(
        @Column(name = "sender")
        var sender: String,
        @Column(name = "recipient")
        var recipient: String,
        @Column(name = "sender_wallet_id")
        var senderWalletId: String,
        @Column(name = "recipient_wallet_id")
        var recipientWalletId: String,
        @Column(name = "amount")
        var amount: String,
        @Column(name = "currency")
        var currency: String,
        @Column(name = "type")
        var type: String,
        @Column(name = "status")
        var status: String,
        @Column(name = "linear_id")
        var linearId: String,
        @Column(name = "created_at")
        var createdAt: Long
    ) : PersistentState() {
        @Suppress("UNUSED")
        constructor() : this(
            sender = "",
            recipient = "",
            senderWalletId = "",
            recipientWalletId = "",
            amount = "",
            currency = "",
            type = "",
            status = "",
            linearId = "",
            createdAt = 0L
        )
    }
}