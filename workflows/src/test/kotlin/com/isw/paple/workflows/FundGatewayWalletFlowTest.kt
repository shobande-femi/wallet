package com.isw.paple.workflows

import com.isw.paple.common.states.WalletState
import com.isw.paple.common.types.Wallet
import com.isw.paple.common.types.WalletStatus
import com.isw.paple.common.types.WalletType
import net.corda.core.contracts.Amount
import net.corda.core.node.services.queryBy
import net.corda.finance.USD
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
        issuerNodeCreatesWallet(wallet)
        val fundAmount =Amount(10, USD)
        issuerNodeFundsGatewayWallet(walletId, fundAmount)

        for (node in listOf(issuerNode, gatewayNode)) {
            node.transaction {
                val walletStates = node.services.vaultService.queryBy<WalletState>().states
                assertEquals(1, walletStates.size)
                val walletState = walletStates.single().state.data

                assertEquals(walletState.balance, balance.plus(fundAmount))
                assertEquals(walletState.createdBy, issuer)
                assertEquals(walletState.owner, gatewayParty)
                assertEquals(walletState.status, status)
                assertEquals(walletState.verified, false)
                assertEquals(walletState.type, type)
            }
        }
    }
}