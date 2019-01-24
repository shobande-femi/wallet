package com.isw.paple.common.contracts

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction

class IssuanceContract : Contract {

    companion object {
        const val CONTRACT_ID = "com.isw.paple.common.contracts.IssuanceContract"
    }

    interface Commands: CommandData

    override fun verify(tx: LedgerTransaction) {

    }
}