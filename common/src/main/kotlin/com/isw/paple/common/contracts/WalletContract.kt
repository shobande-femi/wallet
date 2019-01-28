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
    class IssueFunds : Commands
    class TransferFunds : Commands

    override fun verify(tx: LedgerTransaction) {
        val walletCommand = tx.commands.requireSingleCommand<Commands>()
        val signers = walletCommand.signers.toSet()

        when (walletCommand.value) {
            is Create -> verifyCreate(tx, signers)
            is IssueFunds -> verifyIssueFunds(tx, signers)
            is TransferFunds -> verifyTransferFunds(tx, signers)
            else -> throw IllegalArgumentException("Unrecognised command.")
        }
    }

    private fun verifyCreate(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        //Shape constraints
        "Tx must have zero input states" using (tx.inputStates.isEmpty())
        "Tx must have one output state" using (tx.outputStates.size == 1)

        //Wallet specific constraints
        val walletState = tx.outputStates.single() as WalletState
        "On wallet creation, wallet balance must be zero" using (walletState.balance.quantity == 0L)

        // TODO: find better way of doing this
        "Only NGN and USD are supported" using (walletState.balance.token.toString() in allowedCurrencies)

        "Wallet must be unverified" using (!walletState.verified)

        when (walletState.type) {
            WalletType.GATEWAY_OWNED -> {
                "If wallet type is GatewayOwned, Issuer cannot be owner of wallet" using (walletState.owner != walletState.issuedBy)
                "There must be 2 signers" using (signers.size == 2)
            }
            else -> {
                "Other than GatewayOwned wallet, Owner and IssuedBy must be same" using (walletState.owner == walletState.issuedBy)
                "There must be 1 signer" using (signers.size == 1)
            }
        }

        "The creator of this wallet (i.e the issuer) must sign the transaction" using (signers.contains(walletState.issuedBy.owningKey))
        "Wallet owner must sign this transaction" using (signers.contains(walletState.owner.owningKey))
    }

    private fun verifyIssueFunds(tx: LedgerTransaction, signers: Set<PublicKey>) {
        //TODO: contract implementation
    }

    private fun verifyTransferFunds(tx: LedgerTransaction, signers: Set<PublicKey>) {
        //TODO: contract implementation
    }
}