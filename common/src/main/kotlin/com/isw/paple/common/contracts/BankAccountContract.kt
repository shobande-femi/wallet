package com.isw.paple.common.contracts

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction

class BankAccountContract : Contract {

    companion object {
        const val CONTRACT_ID = "com.isw.paple.common.contracts.BankAccountContract"
    }

    interface Commands : CommandData
    class Add : Commands
    class Update : Commands

    // TODO: Implement Contract Code
    override fun verify(tx: LedgerTransaction) = Unit

}