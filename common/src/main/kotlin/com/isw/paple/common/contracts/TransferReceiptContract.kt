package com.isw.paple.common.contracts

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

class TransferReceiptContract : Contract {

    companion object {
        const val CONTRACT_ID = "com.isw.paple.common.contracts.TransferReceiptContract"
    }

    interface Commands: CommandData
    class Create: Commands

    override fun verify(tx: LedgerTransaction) {
        val walletCommand = tx.commands.requireSingleCommand<TransferReceiptContract.Commands>()
        val signers = walletCommand.signers.toSet()

        when (walletCommand.value) {
            is TransferReceiptContract.Create -> verifyCreate(tx, signers)
            else -> throw IllegalArgumentException("Unrecognised command.")
        }
    }

    private fun verifyCreate(tx: LedgerTransaction, signers: Set<PublicKey>) {
        //TODO: contract implementation
    }
}