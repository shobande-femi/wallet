package com.isw.paple.common.contracts

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction

class RecognisedIssuerContract : Contract {

    companion object {
        const val CONTRACT_ID = "com.isw.paple.common.contracts.RecognisedIssuerContract"
    }

    interface Commands : CommandData
    class Add : Commands

    // TODO: Implement Contract Code
    override fun verify(tx: LedgerTransaction) {
    }

}