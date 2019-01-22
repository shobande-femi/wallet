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
        val walletId = "f2^n2#9N21-c'2+@cm20?scw2"
        val amount = Amount(0, USD)
        val status = WalletStatus.UNKNOWN
        val type = WalletType.GATEWAY_OWNED

        val wallet = Wallet(walletId, gatewayParty, amount, status, type)

        //TODO: try creating a wallet before gateway node adds recognised issuer
        gatewayNodeAddsRecognisedIssuer(issuer, gatewayNode)
        issuerNodeCreatesWallet(wallet)
        //TODO: add same wallet twice

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
        val walletId = "f2^n2#9N21-c'2+@cm20?scw2"
        val amount = Amount(0, USD)
        val status = WalletStatus.UNKNOWN

        val wallet = Wallet(walletId, issuer, amount, status, walletType)

        issuerNodeCreatesWallet(wallet)
        //TODO: add same wallet twice

        issuerNode.transaction {
            val walletStates = issuerNode.services.vaultService.queryBy<WalletState>().states
            assertEquals(1, walletStates.size)
            val walletState = walletStates.single().state.data

            assertEquals(walletState.amount, amount)
            assertEquals(walletState.createdBy, issuer)
            assertEquals(walletState.owner, issuer)
            assertEquals(walletState.status, status)
            assertEquals(walletState.verified, false)
            assertEquals(walletState.type, walletType)
        }
    }

}