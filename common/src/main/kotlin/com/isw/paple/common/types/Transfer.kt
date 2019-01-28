package com.isw.paple.common.types

import com.isw.paple.common.states.TransferReceiptState
import net.corda.core.contracts.Amount
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import java.util.*


@CordaSerializable
data class Transfer(
    val senderWalletId: String,
    val recipientWalletId: String,
    val recipient: Party,
    val amount: Amount<Currency>,
    val type: TransferType
)

fun Transfer.toState(sender: Party, status: TransferStatus): TransferReceiptState {
    return TransferReceiptState(sender, recipient, senderWalletId, recipientWalletId, amount, type, status)
}