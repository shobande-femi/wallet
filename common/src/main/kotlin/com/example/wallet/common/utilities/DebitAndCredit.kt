package com.example.wallet.common.utilities

import com.example.wallet.common.states.WalletState
import net.corda.core.contracts.Amount
import net.corda.core.flows.FlowException
import java.util.*

class UnmatchedCurrencyException(
    senderWalletId: String,
    recipientWalletId: String) : FlowException("Cannot directly move funds from $senderWalletId to $recipientWalletId as currencies do not match")
class UnacceptableCurrencyException(senderWalletId: String, currency: Currency) : FlowException("Sender Wallet $senderWalletId is not denominated in $currency")

fun moveFunds(
    senderWalletState: WalletState,
    recipientWalletState: WalletState, amount: Amount<Currency>): Pair<WalletState, WalletState> {

    if (senderWalletState.balance.token != recipientWalletState.balance.token) {
        throw UnmatchedCurrencyException(senderWalletState.linearId.externalId!!,
            recipientWalletState.linearId.externalId!!
        )
    }
    if (amount.token != senderWalletState.balance.token) {
        throw UnacceptableCurrencyException(senderWalletState.linearId.externalId!!, amount.token)
    }

    val outputSenderWalletState =  senderWalletState.withNewBalance(senderWalletState.balance.minus(amount))
    val outputRecipientWalletState = recipientWalletState.withNewBalance(recipientWalletState.balance.plus(amount))

    return Pair(outputSenderWalletState, outputRecipientWalletState)
}