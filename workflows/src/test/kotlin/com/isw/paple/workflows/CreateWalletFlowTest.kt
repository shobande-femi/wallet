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

class CreateWalletFlowTest: FlowTestsBase() {
    @Test
    fun `create gateway owned wallet`() {
        val gatewayNode = gatewayANode
        val gatewayParty = gatewayNode.info.singleIdentity()
        val status = WalletStatus.UNKNOWN
        val type = WalletType.GATEWAY_OWNED

        val wallet = Wallet(walletId, gatewayParty, zeroBalance, status, type)

        //TODO: try creating a wallet before gateway node adds recognised issuer
        gatewayNodeAddsRecognisedIssuer(issuer, zeroBalance.token.currencyCode, gatewayNode)
        issuerNodeCreatesWallet(wallet)
        //TODO: add same wallet twice

        for (node in listOf(issuerNode, gatewayNode)) {
            node.transaction {
                val walletStates = node.services.vaultService.queryBy<WalletState>().states
                assertEquals(1, walletStates.size)
                val walletState = walletStates.single().state.data

                assertEquals(walletState.linearId.externalId, walletId)
                assertEquals(walletState.balance, zeroBalance)
                assertEquals(walletState.issuedBy, issuer)
                assertEquals(walletState.owner, gatewayParty)
                assertEquals(walletState.status, status)
                assertEquals(walletState.verified, false)
                assertEquals(walletState.type, type)
            }
        }
    }

    @Test
    fun `create issuer owned wallet`() {
        createWalletTest(WalletType.ISSUER_OWNED)
    }

    @Test
    fun `create regular user owned wallet`() {
        createWalletTest(WalletType.REGULAR_USER_OWNED)
    }

    @Test
    fun `create liquidity provider owned wallet`() {
        createWalletTest(WalletType.LIQUIDITY_PROVIDER_OWNED)
    }

    private fun createWalletTest(walletType: WalletType) {
        val status = WalletStatus.UNKNOWN

        val wallet = Wallet(walletId, issuer, zeroBalance, status, walletType)

        issuerNodeCreatesWallet(wallet)
        //TODO: add same wallet twice

        issuerNode.transaction {
            val walletStates = issuerNode.services.vaultService.queryBy<WalletState>().states
            assertEquals(1, walletStates.size)
            val walletState = walletStates.single().state.data

            assertEquals(walletState.linearId.externalId, walletId)
            assertEquals(walletState.balance, zeroBalance)
            assertEquals(walletState.issuedBy, issuer)
            assertEquals(walletState.owner, issuer)
            assertEquals(walletState.status, status)
            assertEquals(walletState.verified, false)
            assertEquals(walletState.type, walletType)
        }
    }

}