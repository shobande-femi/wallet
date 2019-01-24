package com.isw.paple.common.contracts

import com.isw.paple.common.states.IssuanceState
import com.isw.paple.common.states.WalletState
import com.isw.paple.common.types.WalletType
import net.corda.core.contracts.*
import net.corda.core.contracts.Requirements.using
import net.corda.core.transactions.LedgerTransaction
import net.corda.finance.contracts.asset.Cash
import java.security.PublicKey

class IssuanceContract : Contract {

    companion object {
        const val CONTRACT_ID = "com.isw.paple.common.contracts.IssuanceContract"
        val ISSUANCE_LIMIT = mapOf("USD" to 100*100, "NGN" to 50000*100)
    }

    interface Commands: CommandData
    class Create: Commands

    override fun verify(tx: LedgerTransaction) {
        val walletCommand = tx.commands.requireSingleCommand<Commands>()
        val signers = walletCommand.signers.toSet()

        when (walletCommand.value) {
            is Create -> verifyCreate(tx, signers)
            else -> throw IllegalArgumentException("Unrecognised command.")
        }
    }

    private fun verifyCreate(tx: LedgerTransaction, signers: Set<PublicKey>) {
        "Tx must have 1 input state" using (tx.inputStates.size == 1)
        "Tx must have 3 output state" using (tx.outputStates.size == 3)

        val inputWalletState = tx.inputStates.single() as WalletState
        val outputWalletState = tx.outputsOfType<WalletState>().single()
        val outputCashState = tx.outputsOfType<Cash.State>().single()
        val outputIssuanceState = tx.outputsOfType<IssuanceState>().single()

        //TODO: flow for verifying wallets must be written before enabling this contract condition
//        "Wallet to be funded must be verified" using (inputWalletState.verified)

        "Self issuance not allowed" using (inputWalletState.owner != inputWalletState.createdBy)
        "Can only issue to gateway owned wallets" using (inputWalletState.type == WalletType.GATEWAY_OWNED)
        "Only difference allowed between input and output wallet states is balance" using (inputWalletState.withNewBalance(outputWalletState.balance) == outputWalletState)
        "output wallet balance must be greater than input wallet balance" using (outputWalletState.balance > inputWalletState.balance)

        val balanceDiff = outputWalletState.balance.minus(inputWalletState.balance)
        "amount exceeds issuance limit" using (balanceDiff.quantity <= ISSUANCE_LIMIT.getValue(balanceDiff.token.currencyCode))
        "Difference between output and input balances must be same as amount issued " using (outputIssuanceState.amount == balanceDiff)
        "Recipient of issuance must be same as wallet owner" using (outputIssuanceState.recipient == inputWalletState.owner)

        "Owner of cash must be same as wallet owner" using (outputCashState.owner == inputWalletState.owner)
        "output cash state must be same as amount issued" using (outputCashState.amount == Amount(balanceDiff.quantity, Issued(outputIssuanceState.issuer.ref(0), balanceDiff.token)))

        "The creator of this wallet (i.e the issuer) must sign the transaction" using (signers.contains(inputWalletState.createdBy.owningKey))
        "Wallet owner must sign this transaction" using (signers.contains(inputWalletState.owner.owningKey))
    }
}