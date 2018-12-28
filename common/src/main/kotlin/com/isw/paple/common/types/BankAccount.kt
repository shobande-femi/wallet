package com.isw.paple.common.types

import com.isw.paple.common.states.BankAccountState
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import java.util.*

@CordaSerializable
data class BankAccount(
        val country: String,
        val accountId: String,
        val accountName: String,
        val accountNumber: AccountNumber,
        val currency: Currency,
        val type: BankAccountType = BankAccountType.GATEWAY_FLOAT_ACCOUNT // Defaulted to collateral for now.
)

fun BankAccount.toState(owner: Party): BankAccountState {
    return BankAccountState(owner, country, accountId, accountName, accountNumber, currency, type)
}