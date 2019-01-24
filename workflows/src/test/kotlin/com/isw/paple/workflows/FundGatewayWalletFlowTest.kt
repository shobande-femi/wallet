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
        val walletId = "f2^n2#9N21-c'2+@cm20?scw2"
        val balance = Amount(0, USD)
        val status = WalletStatus.UNKNOWN
        val type = WalletType.GATEWAY_OWNED
        val wallet = Wallet(walletId, gatewayParty, balance, status, type)

        gatewayNodeAddsRecognisedIssuer(issuer, gatewayNode)
        val createdWalletState = issuerNodeCreatesWallet(wallet).tx.outputsOfType<WalletState>().single()

        val fundAmount =Amount(10*100, USD)
        issuerNodeFundsGatewayWallet(walletId, fundAmount)

        for (node in listOf(issuerNode, gatewayNode)) {
            node.transaction {
                val walletStates = node.services.vaultService.queryBy<WalletState>().states
                val issuanceStates = node.services.vaultService.queryBy<IssuanceState>().states

                assertEquals(1, walletStates.size)
                assertEquals(1, issuanceStates.size)

                val walletState = walletStates.single().state.data
                val issuanceState = issuanceStates.single().state.data

                assertEquals(walletState, createdWalletState.withNewBalance(createdWalletState.balance.plus(fundAmount)))
                val balanceDiff = walletState.balance.minus(createdWalletState.balance)
                assertEquals(balanceDiff, issuanceState.amount)
                assertEquals(issuanceState.recipient, walletState.owner)

                if (node.info.singleIdentity() == walletState.owner) {
                    val cashStates = node.services.vaultService.queryBy<Cash.State>().states
                    assertEquals(1, cashStates.size)
                    val cashState = cashStates.single().state.data
                    assertEquals(walletState.owner, cashState.owner)
                    assertEquals(cashState.amount, Amount(balanceDiff.quantity, Issued(issuanceState.issuer.ref(0), balanceDiff.token)))
                }
            }
        }
    }
}