package com.isw.paple.common.types

import com.isw.paple.common.states.WalletState
import net.corda.core.contracts.Amount
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import java.util.*

@CordaSerializable
data class Wallet(
    val walletId: String,
    val owner: Party,
    val balance: Amount<Currency>,
    val status: WalletStatus,
    val type: WalletType
)

fun Wallet.toState(issuedBy: Party): WalletState {
    return WalletState(owner, walletId, issuedBy, balance, status, type)
}