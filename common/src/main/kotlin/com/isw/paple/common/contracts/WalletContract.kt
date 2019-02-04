package com.isw.paple.common.contracts

import com.isw.paple.common.states.WalletState
import com.isw.paple.common.types.WalletType
import net.corda.core.contracts.*
import net.corda.core.contracts.Requirements.using
import net.corda.core.transactions.LedgerTransaction
import net.corda.finance.contracts.asset.Cash
import java.security.PublicKey

class WalletContract : Contract {

    companion object {
        const val CONTRACT_ID = "com.isw.paple.common.contracts.WalletContract"
        val allowedCurrencies = listOf("NGN", "USD")
    }

    interface Commands : CommandData
    class Create : Commands
    class VerifyWallet : Commands
    class IssueFund : Commands
    class Transfer : Commands

    override fun verify(tx: LedgerTransaction) {
        val walletCommand = tx.commands.requireSingleCommand<Commands>()
        val signers = walletCommand.signers.toSet()

        when (walletCommand.value) {
            is Create -> verifyCreate(tx, signers)
            is VerifyWallet -> verifyWallet(tx, signers)
            is IssueFund -> verifyIssueFund(tx, signers)
            is Transfer -> verifyTransfer(tx, signers)
            else -> throw IllegalArgumentException("Unrecognised command.")
        }
    }

    private fun verifyCreate(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        //Shape constraints
        "Tx must have zero input states" using (tx.inputStates.isEmpty())
        "Tx must have one output state" using (tx.outputStates.size == 1)

        //Wallet specific constraints
        val walletState = tx.outputStates.single() as WalletState
        "On wallet creation, wallet balance must be zero" using (walletState.balance.quantity == 0L)

        // TODO: find better way of doing this
        "Currency not in list of allowed currencies" using (walletState.balance.token.currencyCode in allowedCurrencies)

        "Wallet must be unverified" using (!walletState.verified)

        when (walletState.type) {
            WalletType.GATEWAY_OWNED -> {
                "If wallet type is GatewayOwned, Issuer cannot be owner of wallet" using (walletState.owner != walletState.issuedBy)
                "There must be 2 signers" using (signers.size == 2)
            }
            else -> {
                "Other than GatewayOwned wallet, Owner and IssuedBy must be same" using (walletState.owner == walletState.issuedBy)
                "There must be 1 signer" using (signers.size == 1)
            }
        }

        "The creator of this wallet (i.e the issuer) must sign the transaction" using (signers.contains(walletState.issuedBy.owningKey))
        "Wallet owner must sign this transaction" using (signers.contains(walletState.owner.owningKey))

        //TODO: is it possible to fetch the list of recognised issuers from the vault?
        //if so, check that creator is a recognised issuer
    }

    private fun verifyIssueFund(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        //Shape constraints
        val inputWalletStates = tx.inputsOfType<WalletState>()
        val outputWalletStates = tx.outputsOfType<WalletState>()
        "Tx must contain a single input wallet state" using (inputWalletStates.size == 1)
        "Tx must contain a single output wallet state" using (outputWalletStates.size == 1)
        val inputWalletState = inputWalletStates.single()
        val outputWalletState = outputWalletStates.single()

        "wallets receiving funds must be verified" using (inputWalletState.verified)

        val balanceDiff = outputWalletState.balance.minus(inputWalletState.balance)
        "Only difference allowed between input and output wallet states is their balances" using (
                inputWalletState.withNewBalance(inputWalletState.balance.plus(balanceDiff)) == outputWalletState)

        val outputCashState = tx.outputsOfType<Cash.State>().single()
        "Difference in wallet balance must be equal to output cash state, and issuer of cash must be same as issuer of wallet" using (
                outputCashState.amount == Amount(balanceDiff.quantity, Issued(outputWalletState.issuedBy.ref(0), balanceDiff.token))
                )
        "Wallet owner must own cash states" using (outputCashState.owner == outputWalletState.owner)

    }

    private fun verifyTransfer(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        //TODO: contract implementation
        val inputWalletStates = tx.inputsOfType<WalletState>()
        val outputWalletStates = tx.outputsOfType<WalletState>()
        "Tx must contain a single input wallet state" using (inputWalletStates.size == 2)
        "Tx must contain a single output wallet state" using (outputWalletStates.size == 2)
        val inputWalletState = inputWalletStates.single()
        val outputWalletState = outputWalletStates.single()

        inputWalletStates.forEach {
            "Sender and recipient wallets must be verified" using (it.verified)
        }

    }

    private fun verifyWallet(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        //TODO: contract implementation
    }
}