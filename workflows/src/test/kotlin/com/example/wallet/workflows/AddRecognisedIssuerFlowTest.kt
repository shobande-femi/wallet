package com.example.wallet.workflows

import com.example.wallet.common.states.RecognisedIssuerState
import net.corda.core.node.services.queryBy
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.StartedMockNode
import org.junit.Test
import kotlin.test.assertEquals

class AddRecognisedIssuerFlowTest: FlowTestsBase() {

    @Test
    fun `gateways correctly add recognised issuers`() {
        testAddRecognisedIssuer(gatewayANode)
    }

    private fun testAddRecognisedIssuer(gatewayNode: StartedMockNode) {
        val gatewayParty = gatewayNode.info.singleIdentity()
        val currencyCode = zeroBalance.token.currencyCode
        gatewayNodeAddsRecognisedIssuer(issuerParty = issuer, currencyCode= currencyCode, gatewayNode = gatewayNode)
        //TODO: add recognised issuer twice

        gatewayNode.transaction {
            val recognisedIssuers = gatewayNode.services.vaultService.queryBy<RecognisedIssuerState>().states
            assertEquals(1, recognisedIssuers.size)
            val recognisedIssuer = recognisedIssuers.single().state.data

            assertEquals(issuer, recognisedIssuer.issuer)
            assertEquals(gatewayParty, recognisedIssuer.addedBy)
            assertEquals(currencyCode, recognisedIssuer.currencyCode)
        }
    }

}