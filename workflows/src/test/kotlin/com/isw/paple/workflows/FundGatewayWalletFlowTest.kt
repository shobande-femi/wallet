package com.isw.paple.workflows

import com.isw.paple.common.states.IssuanceState
import com.isw.paple.common.states.WalletState
import com.isw.paple.common.types.Wallet
import com.isw.paple.common.types.WalletStatus
import com.isw.paple.common.types.WalletType
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Issued
import net.corda.core.node.services.queryBy
import net.corda.finance.USD
import net.corda.finance.contracts.asset.Cash
import net.corda.testing.core.singleIdentity
import org.junit.Test
import kotlin.test.assertEquals

class FundGatewayWalletFlowTest: FlowTestsBase() {
    @Test
    fun `fund gateway owned wallet`() {
        //TODO: refactor wallet creation into a function
        val gatewayNode = gatewayANode
        val gatewayParty = gatewayNode.info.singleIdentity()
        val status = WalletStatus.UNKNOWN
        val type = WalletType.GATEWAY_OWNED
        val wallet = Wallet(walletId, gatewayParty, zeroBalance, status, type)

        gatewayNodeAddsRecognisedIssuer(issuer, gatewayNode)
        val createdWalletState = issuerNodeCreatesWallet(wallet).tx.outputsOfType<WalletState>().single()

        val fundAmount =Amount(10*100, USD)
        issuerNodeFundsGatewayWallet(walletId, fundAmount)

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