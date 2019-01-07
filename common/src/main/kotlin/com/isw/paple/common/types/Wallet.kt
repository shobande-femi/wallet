package com.isw.paple.common.types

import com.isw.paple.common.states.WalletState
import net.corda.core.contracts.Amount
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import java.util.*

@CordaSerializable
data class Wallet(
    val walletId: String,
    val createdBy: Party,
    val amount: Amount<Currency>,
    val status: WalletStatus,
    val type: WalletType
)

fun Wallet.toState(owner: Party): WalletState {
    return WalletState(owner, walletId, createdBy, amount, status, type)
}