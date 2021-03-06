package com.example.wallet.workflows

import com.example.wallet.common.states.IssuanceState
import com.example.wallet.common.states.WalletState
import com.example.wallet.common.types.Wallet
import com.example.wallet.common.types.WalletStatus
import com.example.wallet.common.types.WalletType
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Issued
import net.corda.core.node.services.queryBy
import net.corda.finance.contracts.asset.Cash
import net.corda.testing.core.singleIdentity
import org.junit.Test
import kotlin.test.assertEquals

class IssueFundsToWalletFlowTest: FlowTestsBase() {
    @Test
    fun `fund gateway owned wallet`() {
        //TODO: refactor wallet creation into a function
        val gatewayNode = gatewayANode
        val gatewayParty = gatewayNode.info.singleIdentity()
        val status = WalletStatus.UNKNOWN
        val type = WalletType.GATEWAY_OWNED
        val wallet = Wallet(walletId, gatewayParty, zeroBalance, status, type)

        gatewayNodeAddsRecognisedIssuer(issuer, zeroBalance.token.currencyCode, gatewayNode)
        issuerNodeCreatesWallet(wallet)
        val createdWalletState = issuerNodeVerifiesWallet(walletId).tx.outputsOfType<WalletState>().single()

        issuerNodeFundsGatewayWallet(createdWalletState.linearId.externalId.toString(), fundAmount)

        for (node in listOf(issuerNode, gatewayNode)) {
            node.transaction {
                val outputWalletStates = node.services.vaultService.queryBy<WalletState>().states
                val outputIssuanceStates = node.services.vaultService.queryBy<IssuanceState>().states

                assertEquals(1, outputWalletStates.size)
                assertEquals(1, outputIssuanceStates.size)

                val outputWalletState = outputWalletStates.single().state.data
                val outputIssuanceState = outputIssuanceStates.single().state.data

                val balanceDiff = outputWalletState.balance.minus(createdWalletState.balance)
                assertEquals(balanceDiff, fundAmount)
                assertEquals(balanceDiff, outputIssuanceState.amount)

                assertEquals(outputWalletState, createdWalletState.withNewBalance(createdWalletState.balance.plus(fundAmount)))

                assertEquals(outputIssuanceState.recipient, outputWalletState.owner)

                if (node.info.singleIdentity() == outputWalletState.owner) {
                    val outputCashStates = node.services.vaultService.queryBy<Cash.State>().states
                    assertEquals(1, outputCashStates.size)
                    val outputCashState = outputCashStates.single().state.data

                    assertEquals(outputWalletState.owner, outputCashState.owner)
                    assertEquals(outputCashState.amount, Amount(balanceDiff.quantity, Issued(outputWalletState.issuedBy.ref(0), balanceDiff.token)))
                }
            }
        }
    }
}