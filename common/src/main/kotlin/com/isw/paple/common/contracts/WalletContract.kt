package com.isw.paple.common.contracts

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction

class WalletContract : Contract {

    companion object {
        @JvmStatic
        val CONTRACT_ID = "com.isw.paple.common.contracts.WalletContract"
    }

    interface Commands : CommandData
    class Add : Commands
    class Update : Commands

    // TODO: Implement Contract Code
    override fun verify(tx: LedgerTransaction) = Unit

}