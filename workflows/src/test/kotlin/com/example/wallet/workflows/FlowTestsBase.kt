package com.example.wallet.workflows

import com.example.wallet.common.types.Transfer
import com.example.wallet.common.types.Wallet
import com.example.wallet.workflows.flows.*
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
    protected val senderWalletId = ")#(NC@*N@8fn2921"
    protected val recipientWalletId = "P-12id0-Mc;2Nc29jv2"
    protected val zeroBalance = Amount(0, USD)
    protected val fundAmount = Amount(10*100, USD)
    protected val transferAmount = Amount(3*100, USD)

    @Before
    fun setup() {
        network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = cordappsForPackages(
            "com.example.wallet.workflows",
            "com.example.wallet.common",
            "net.corda.finance"
            )
        ))

        notaryNode = network.defaultNotaryNode
        issuerNode = network.createPartyNode(CordaX500Name("Issuer", "Victoria Island", "NG"))
        gatewayANode = network.createPartyNode(CordaX500Name("GatewayA", "Victoria Island", "NG"))
        gatewayBNode = network.createPartyNode(CordaX500Name("GatewayB", "Victoria Island", "NG"))

        //notary = network.defaultNotaryIdentity
        issuer = issuerNode.info.singleIdentity()

        val responseFlows = listOf(
            CreateWalletFlow.Responder::class.java,
            IssueFundsToWalletFlow.Responder::class.java,
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

    fun gatewayNodeAddsRecognisedIssuer(issuerParty: Party, currencyCode: String, gatewayNode: StartedMockNode): SignedTransaction {
        val flow = AddRecognisedIssuerFlow.Initiator(issuerParty, currencyCode)
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

    fun issuerNodeVerifiesWallet(walletId: String) : SignedTransaction {
        val flow = VerifyWalletFlow.Initiator(walletId)
        val future = issuerNode.startFlow(flow)
        network.runNetwork()
        return future.get()
    }

    fun issuerNodeFundsGatewayWallet(walletId: String, amount: Amount<Currency>): SignedTransaction {
        val flow = IssueFundsToWalletFlow.Initiator(walletId, amount)
        val future = issuerNode.startFlow(flow)
        network.runNetwork()
        return future.get()
    }

    fun gatewayNodeTransfersToWallet(transfer: Transfer, gatewayNode: StartedMockNode) : SignedTransaction {
        val flow = WalletToWalletTransferFlow.Initiator(transfer)
        val future = gatewayNode.startFlow(flow)
        network.runNetwork()
        return future.get()
    }
}