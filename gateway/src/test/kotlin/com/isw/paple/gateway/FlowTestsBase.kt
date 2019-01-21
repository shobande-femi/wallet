package com.isw.paple.gateway

import com.isw.paple.common.types.Wallet
import com.isw.paple.gateway.flows.AddRecognisedIssuer
import com.isw.paple.gateway.flows.CreateGatewayWalletResponder
import com.isw.paple.issuer.flows.CreateGatewayWallet
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before

abstract class FlowTestsBase {
    private lateinit var mockNet: MockNetwork

    protected lateinit var notaryNode: StartedMockNode
    protected lateinit var issuerNode: StartedMockNode
    protected lateinit var gatewayANode: StartedMockNode
    protected lateinit var gatewayBNode: StartedMockNode

//    protected lateinit var notaryParty: Party
    protected lateinit var issuer: Party
//    protected lateinit var gatewayA: Party
//    protected lateinit var gatewayB: Party

    @Before
    fun setup() {
        mockNet = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
            TestCordapp.findCordapp("com.isw.paple.gateway"),
            TestCordapp.findCordapp("com.isw.paple.issuer"),
            TestCordapp.findCordapp("com.isw.paple.common"))))
//        mockNet = MockNetwork(MockNetworkParameters(cordappsForAllNodes = cordappsForPackages(
//            "com.isw.paple.gateway",
//            "com.isw.paple.issuer",
//            "com.isw.paple.common",
//            javaClass.packageName
//            )
//        ))

        notaryNode = mockNet.defaultNotaryNode
        issuerNode = mockNet.createPartyNode(CordaX500Name("ISW", "Victoria Island", "NG"))
        gatewayANode = mockNet.createPartyNode(CordaX500Name("GatewayA", "Lagos", "NG"))
        gatewayBNode = mockNet.createPartyNode(CordaX500Name("GatewayB", "Port Harcourt", "NG"))

//        notaryParty = mockNet.defaultNotaryIdentity
        issuer = issuerNode.info.singleIdentity()

        val responseFlows = listOf(CreateGatewayWalletResponder::class.java)
        listOf(gatewayANode, gatewayBNode).forEach {
            for (flow in responseFlows) {
                it.registerInitiatedFlow(flow)
            }
        }

        mockNet.runNetwork()
    }

    @After
    fun tearDown() = mockNet.stopNodes()

    fun gatewayNodeAddsRecognisedIssuer(issuerParty: Party, gatewayNode: StartedMockNode): SignedTransaction {
        val flow = AddRecognisedIssuer(issuerParty)
        val future = gatewayNode.startFlow(flow)
        mockNet.runNetwork()
        return future.get()
    }

    fun issuerNodeCreatesGatewayWallet(wallet: Wallet): SignedTransaction {
        val flow = CreateGatewayWallet(wallet)
        val future = issuerNode.startFlow(flow)
        mockNet.runNetwork()
        return future.get()
    }
}