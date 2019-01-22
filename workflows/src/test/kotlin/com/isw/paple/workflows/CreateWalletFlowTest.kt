package com.isw.paple.workflows

import com.isw.paple.common.states.WalletState
import com.isw.paple.common.types.Wallet
import com.isw.paple.common.types.WalletStatus
import com.isw.paple.common.types.WalletType
import net.corda.core.contracts.Amount
import net.corda.core.node.services.queryBy
import net.corda.finance.USD
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.StartedMockNode
import org.junit.Test
import kotlin.test.assertEquals

class CreateWalletFlowTest: FlowTestsBase() {
    @Test
    fun `issuer creates gateway wallet`() {
        testCreateGatewayWallet(issuerNode, gatewayANode)
    }

    private fun testCreateGatewayWallet(issuerNode: StartedMockNode, gatewayNode: StartedMockNode) {
        val gatewayParty = gatewayNode.info.singleIdentity()
        val walletId = "snkcieirw0348"
        val amount = Amount(0, USD)
        val status = WalletStatus.UNKNOWN
        val type = WalletType.GATEWAY_OWNED

        gatewayNodeAddsRecognisedIssuer(issuer, gatewayNode)

        val wallet = Wallet(walletId, gatewayParty, amount, status, type)
        issuerNodeCreatesGatewayWallet(wallet = wallet)
        for (node in listOf(issuerNode, gatewayNode)) {
            node.transaction {
                val walletStates = node.services.vaultService.queryBy<WalletState>().states
                assertEquals(1, walletStates.size)
                val walletState = walletStates.single().state.data

                assertEquals(walletState.amount, amount)
                assertEquals(walletState.createdBy, issuer)
                assertEquals(walletState.owner, gatewayParty)
                assertEquals(walletState.status, status)
                assertEquals(walletState.verified, false)
                assertEquals(walletState.type, type)
            }
        }
    }

}