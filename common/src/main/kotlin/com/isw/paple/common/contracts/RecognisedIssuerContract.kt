package com.isw.paple.common.contracts

import com.isw.paple.common.states.RecognisedIssuerState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

class RecognisedIssuerContract : Contract {

    companion object {
        const val CONTRACT_ID = "com.isw.paple.common.contracts.RecognisedIssuerContract"
        val allowedCurrencies = listOf("NGN", "USD")
    }

    interface Commands : CommandData
    class Add : Commands
    class Activate : Commands
    class Deactivate: Commands

    override fun verify(tx: LedgerTransaction) {
        val recognisedIssuerCommand = tx.commands.requireSingleCommand<Commands>()
        val signers = recognisedIssuerCommand.signers.toSet()

        when (recognisedIssuerCommand.value) {
            is Add -> verifyAdd(tx, signers)
            is Activate -> verifyActivate(tx, signers)
            is Deactivate -> verifyDeactivate(tx, signers)
            else -> throw IllegalArgumentException("Unrecognised command.")
        }
    }


    private fun verifyAdd(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        //Shape constraints
        "Tx must have zero input states" using (tx.inputStates.isEmpty())
        "Tx must have one output state" using (tx.outputStates.size == 1)

        val recognisedIssuerState = tx.outputStates.single() as RecognisedIssuerState
        "Currency not in list of allowed currencies" using (recognisedIssuerState.currencyCode in allowedCurrencies)
        "On adding recognised issuer, activated field must be true" using (recognisedIssuerState.activated)

        "The party adding this recognised issuer must sign the transaction" using (signers.contains(recognisedIssuerState.addedBy.owningKey))
    }

    private fun verifyActivate(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        //TODO contract implementation
    }

    private fun verifyDeactivate(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        //TODO contract implementation
    }

}