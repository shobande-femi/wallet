package com.isw.paple.common.contracts

import com.isw.paple.common.states.WalletState
import com.isw.paple.common.types.WalletType
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

class WalletContract : Contract {

    companion object {
        const val CONTRACT_ID = "com.isw.paple.common.contracts.WalletContract"
        val allowedCurrencies = listOf("NGN", "USD")
    }

    interface Commands : CommandData
    class Create : Commands
//    class Update : Commands

    // TODO: Implement Contract Code
    override fun verify(tx: LedgerTransaction) {
        val walletCommand = tx.commands.requireSingleCommand<Commands>()
        val setOfSigners = walletCommand.signers.toSet()

        when (walletCommand.value) {
            is Create -> verifyCreate(tx, setOfSigners)
//            is Update -> verifyUpdate(tx, setOfSigners)
            else -> throw IllegalArgumentException("Unrecognised command.")
        }
    }

    private fun verifyCreate(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        //Shape constraints
        "Transaction must have zero input states" using (tx.inputStates.isEmpty())
        "Tx must have one output state" using (tx.outputStates.size == 1)

        //Wallet specific constraints
        val walletState = tx.outputStates.single() as WalletState
        "Wallet creation and funding must be done separately. Hence, amount must be zero" using (walletState.amount.quantity == 0L)
        "Only NGN and USD are supported" using (walletState.amount.token.toString() in allowedCurrencies)
        if (walletState.type == WalletType.GATEWAY_OWNED) {
            "If wallet type is GatewayOwned, Issuer cannot be owner of wallet" using (walletState.owner != walletState.createdBy)
        }

        //signer constraints
        "There must be 2 signers" using (signers.size == 2)
        "The creator of this wallet (i.e the issuer) must sign the transaction" using (signers.contains(walletState.createdBy.owningKey))
        "Wallet owner must sign this transaction" using (signers.contains(walletState.owner.owningKey))
    }

}