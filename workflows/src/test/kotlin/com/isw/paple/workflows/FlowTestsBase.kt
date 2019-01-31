package com.isw.paple.workflows

import com.isw.paple.common.types.Wallet
import com.isw.paple.workflows.flows.AddRecognisedIssuerFlow
import com.isw.paple.workflows.flows.CreateWalletFlow
import com.isw.paple.workflows.flows.FundGatewayWalletFlow
import com.isw.paple.workflows.flows.WalletToWalletTransferFlow
import net.corda.core.contracts.Amount
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.finance.USD
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.internal.cordappsForPackages
import org.junit.After
import org.junit.Before
import java.util.*

abstract class FlowTestsBase {
    private lateinit var network: MockNetwork

    private lateinit var notaryNode: StartedMockNode
    protected lateinit var issuerNode: StartedMockNode
    protected lateinit var gatewayANode: StartedMockNode
    protected lateinit var gatewayBNode: StartedMockNode

    //protected lateinit var notary: Party
    protected lateinit var issuer: Party

    protected val walletId = "f2^n2#9N21-c'2+@cm20?scw2"
    protected val zeroBalance = Amount(0, USD)
    protected val fundAmount = Amount(10*100, USD)
    protected val transferAmount = Amount(3*100, USD)

    @Before
    fun setup() {
        network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = cordappsForPackages(
            "com.isw.paple.workflows",
            "com.isw.paple.common",
            "net.corda.finance"
            )
        ))

        notaryNode = network.defaultNotaryNode
        issuerNode = network.createPartyNode(CordaX500Name("ISW", "Victoria Island", "NG"))
        gatewayANode = network.createPartyNode(CordaX500Name("GatewayA", "Lagos", "NG"))
        gatewayBNode = network.createPartyNode(CordaX500Name("GatewayB", "Port Harcourt", "NG"))

        //notary = network.defaultNotaryIdentity
        issuer = issuerNode.info.singleIdentity()

        val responseFlows = listOf(
            CreateWalletFlow.Responder::class.java,
            FundGatewayWalletFlow.Responder::class.java,
            WalletToWalletTransferFlow.Responder::class.java
        )
        listOf(issuerNode, gatewayANode, gatewayBNode).forEach {
            for (flow in responseFlows) {
                it.registerInitiatedFlow(flow)
            }
        }

        network.runNetwork()
    }

    @After
    fun tearDown() = network.stopNodes()

    fun gatewayNodeAddsRecognisedIssuer(issuerParty: Party, gatewayNode: StartedMockNode): SignedTransaction {
        val flow = AddRecognisedIssuerFlow.Initiator(issuerParty)
        val future = gatewayNode.startFlow(flow)
        network.runNetwork()
        return future.get()
    }

    fun issuerNodeCreatesWallet(wallet: Wallet): SignedTransaction {
        val flow = CreateWalletFlow.Initiator(wallet)
        val future = issuerNode.startFlow(flow)
        network.runNetwork()
        return future.get()
    }

    fun issuerNodeFundsGatewayWallet(walletId: String, amount: Amount<Currency>): SignedTransaction {
        val flow = FundGatewayWalletFlow.Initiator(walletId, amount)
        val future = issuerNode.startFlow(flow)
        network.runNetwork()
        return future.get()
    }
}