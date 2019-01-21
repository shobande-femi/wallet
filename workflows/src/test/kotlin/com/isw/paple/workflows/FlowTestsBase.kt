package com.isw.paple.workflows

import com.isw.paple.common.types.Wallet
import com.isw.paple.workflows.flows.AddRecognisedIssuer
import com.isw.paple.workflows.flows.CreateGatewayWalletFlow
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
    private lateinit var network: MockNetwork

    private lateinit var notaryNode: StartedMockNode
    protected lateinit var issuerNode: StartedMockNode
    protected lateinit var gatewayANode: StartedMockNode
    private lateinit var gatewayBNode: StartedMockNode

//    protected lateinit var notary: Party
    protected lateinit var issuer: Party
//    protected lateinit var gatewayA: Party
//    protected lateinit var gatewayB: Party

    @Before
    fun setup() {
        network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
            TestCordapp.findCordapp("com.isw.paple.workflows"),
            TestCordapp.findCordapp("com.isw.paple.common")
        )))
//        network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = cordappsForPackages(
//            "com.isw.paple.workflows",
//            "com.isw.paple.issuer",
//            "com.isw.paple.common",
//            javaClass.packageName
//            )
//        ))

        notaryNode = network.defaultNotaryNode
        issuerNode = network.createPartyNode(CordaX500Name("ISW", "Victoria Island", "NG"))
        gatewayANode = network.createPartyNode(CordaX500Name("GatewayA", "Lagos", "NG"))
        gatewayBNode = network.createPartyNode(CordaX500Name("GatewayB", "Port Harcourt", "NG"))

//        notary = network.defaultNotaryIdentity
        issuer = issuerNode.info.singleIdentity()
//        gatewayA = gatewayANode.info.singleIdentity()
//        gatewayB = gatewayBNode.info.singleIdentity()

        val responseFlows = listOf(CreateGatewayWalletFlow.Responder::class.java)
        listOf(gatewayANode, gatewayBNode).forEach {
            for (flow in responseFlows) {
                it.registerInitiatedFlow(flow)
            }
        }

        network.runNetwork()
    }

    @After
    fun tearDown() = network.stopNodes()

    fun gatewayNodeAddsRecognisedIssuer(issuerParty: Party, gatewayNode: StartedMockNode): SignedTransaction {
        val flow = AddRecognisedIssuer.Initiator(issuerParty)
        val future = gatewayNode.startFlow(flow)
        network.runNetwork()
        return future.get()
    }

    fun issuerNodeCreatesGatewayWallet(wallet: Wallet): SignedTransaction {
        val flow = CreateGatewayWalletFlow.Initiator(wallet)
        val future = issuerNode.startFlow(flow)
        network.runNetwork()
        return future.get()
    }
}