package com.isw.paple.workflows

import com.isw.paple.common.states.TransferReceiptState
import com.isw.paple.common.states.WalletState
import com.isw.paple.common.types.*
import com.isw.paple.common.utilities.getWalletStateByWalletId
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Issued
import net.corda.core.node.services.queryBy
import net.corda.finance.contracts.asset.Cash
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.StartedMockNode
import org.junit.Test
import kotlin.test.assertEquals

class WalletToWalletTransferFlowTest : FlowTestsBase() {

    @Test
    fun `transfer from gateway to gateway`() {
        val recipientNode = gatewayBNode
        gatewayNodeAddsRecognisedIssuer(issuer, recipientNode)
        transferFromGateway(
            senderNode = gatewayANode,
            recipientNode = recipientNode,
            recipientWalletType = WalletType.GATEWAY_OWNED,
            transferType = TransferType.GATEWAY_TO_GATEWAY
        )
    }

    @Test
    fun `transfer from gateway to regular user`() {
        transferFromGateway(
            senderNode = gatewayANode,
            recipientNode = issuerNode,
            recipientWalletType = WalletType.REGULAR_USER_OWNED,
            transferType = TransferType.GATEWAY_TO_REGULAR_USER
        )
    }

    @Test
    fun `transfer from gateway to issuer`() {
        transferFromGateway(
            senderNode = gatewayANode,
            recipientNode = issuerNode,
            recipientWalletType = WalletType.ISSUER_OWNED,
            transferType = TransferType.GATEWAY_TO_ISSUER
        )
    }

    @Test
    fun `transfer from gateway to liquidity provider`() {
        transferFromGateway(
            senderNode = gatewayANode,
            recipientNode = issuerNode,
            recipientWalletType = WalletType.LIQUIDITY_PROVIDER_OWNED,
            transferType = TransferType.GATEWAY_TO_LIQUIDITY_PROVIDER
        )
    }

    private fun transferFromGateway(senderNode: StartedMockNode, recipientNode: StartedMockNode, recipientWalletType: WalletType, transferType: TransferType) {
        gatewayNodeAddsRecognisedIssuer(issuer, senderNode)

        val walletStatus = WalletStatus.UNKNOWN

        val senderWallet = Wallet(senderWalletId, senderNode.info.singleIdentity(), zeroBalance, walletStatus, WalletType.GATEWAY_OWNED)
        val recipientWallet = Wallet(recipientWalletId, recipientNode.info.singleIdentity(), zeroBalance, walletStatus, recipientWalletType)
        val createdSenderWalletState = issuerNodeCreatesWallet(senderWallet).tx.outputsOfType<WalletState>().single()
        val createdRecipientWalletState = issuerNodeCreatesWallet(recipientWallet).tx.outputsOfType<WalletState>().single()

        issuerNodeFundsGatewayWallet(createdSenderWalletState.linearId.externalId.toString(), fundAmount)
        val fundedSenderWalletState = senderNode.transaction {
            val fundedSenderWalletStateAndRef = getWalletStateByWalletId(createdSenderWalletState.linearId.externalId.toString(), senderNode.services)
            fundedSenderWalletStateAndRef!!.state.data
        }

        val transfer = Transfer(
            createdSenderWalletState.linearId.externalId.toString(),
            createdRecipientWalletState.linearId.externalId.toString(),
            createdRecipientWalletState.owner,
            transferAmount,
            transferType
        )
        gatewayNodeTransfersToWallet(transfer, senderNode)

        senderNode.transaction {
            val senderWalletStateAndRef = getWalletStateByWalletId(fundedSenderWalletState.linearId.externalId.toString(), senderNode.services)
            val senderWalletState =  senderWalletStateAndRef!!.state.data

            val outputCashStates = senderNode.services.vaultService.queryBy<Cash.State>().states
            assertEquals(1, outputCashStates.size)
            val outputCashState = outputCashStates.single().state.data

            val outputTransferReceiptStates = senderNode.services.vaultService.queryBy<TransferReceiptState>().states
            assertEquals(1, outputTransferReceiptStates.size)
            val outputTransferReceiptState = outputTransferReceiptStates.single().state.data

            print("Created Sender Wallet State: $createdSenderWalletState")
            print("Funded Sender Wallet State: $fundedSenderWalletState")
            print("Current Sender wallet state: $senderWalletState")
            val change = fundedSenderWalletState.balance.minus(transferAmount)
            assertEquals(senderWalletState, fundedSenderWalletState.withNewBalance(fundedSenderWalletState.balance.minus(transferAmount)))
            assertEquals(outputCashState.amount, Amount(change.quantity, Issued(senderWalletState.issuedBy.ref(0), change.token)))

            assertEquals(outputTransferReceiptState.amount, transferAmount)
            assertEquals(outputTransferReceiptState.sender, fundedSenderWalletState.owner)
            assertEquals(outputTransferReceiptState.senderWalletId, fundedSenderWalletState.linearId.externalId)
            assertEquals(outputTransferReceiptState.status.name, walletStatus.name)
            assertEquals(outputTransferReceiptState.type.name, transfer.type.name)
            assertEquals(outputTransferReceiptState.recipient, transfer.recipient)
            assertEquals(outputTransferReceiptState.recipientWalletId, transfer.recipientWalletId)
        }

        recipientNode.transaction {
            val recipientWalletStateAndRef = getWalletStateByWalletId(createdRecipientWalletState.linearId.externalId.toString(), recipientNode.services)
            val recipientWalletState =  recipientWalletStateAndRef!!.state.data

            val outputCashStates = recipientNode.services.vaultService.queryBy<Cash.State>().states
            assertEquals(1, outputCashStates.size)
            val outputCashState = outputCashStates.single().state.data

            val outputTransferReceiptStates = recipientNode.services.vaultService.queryBy<TransferReceiptState>().states
            assertEquals(1, outputTransferReceiptStates.size)
            val outputTransferReceiptState = outputTransferReceiptStates.single().state.data

            print("Created Recipient Wallet State: $createdRecipientWalletState")
            print("Current Recipient wallet state: $recipientWalletState")
            val amountReceived = recipientWalletState.balance.minus(createdRecipientWalletState.balance)
            assertEquals(amountReceived, transferAmount)
            assertEquals(recipientWalletState, createdRecipientWalletState.withNewBalance(createdRecipientWalletState.balance.plus(transferAmount)))
            assertEquals(outputCashState.amount, Amount(amountReceived.quantity, Issued(recipientWalletState.issuedBy.ref(0), amountReceived.token)))

            assertEquals(outputTransferReceiptState.amount, transferAmount)
            assertEquals(outputTransferReceiptState.recipient, recipientWalletState.owner)
            assertEquals(outputTransferReceiptState.recipientWalletId, recipientWalletState.linearId.externalId)
            assertEquals(outputTransferReceiptState.status.name, walletStatus.name)
            assertEquals(outputTransferReceiptState.type.name, transfer.type.name)
            assertEquals(outputTransferReceiptState.recipient, transfer.recipient)
            assertEquals(outputTransferReceiptState.recipientWalletId, transfer.recipientWalletId)
        }
    }
}