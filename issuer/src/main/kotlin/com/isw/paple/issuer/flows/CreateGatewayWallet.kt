package com.isw.paple.issuer.flows

import co.paralleluniverse.fibers.Suspendable
import com.isw.paple.common.states.WalletState
import com.isw.paple.common.types.Wallet
import com.isw.paple.common.utilities.getWalletStateByWalletId
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker

/**
 * Adds a new [Wallet] state to the ledger.
 * The state is always added as a "uni-lateral state" to the node calling this flow.
 */
@InitiatingFlow
@StartableByRPC
class CreateGatewayWallet(private val wallet: Wallet) : FlowLogic<SignedTransaction>() {

    companion object {
        // TODO: fine tune progress tracker
        object FLOW_SESSION: ProgressTracker.Step("Initiation flow session with gateway party")
        object VERIFYING_AND_SIGNING : ProgressTracker.Step("Verifying and signing transaction proposal") {
            override fun childProgressTracker() = SignTransactionFlow.tracker()
        }
        object RECEIVE_FINALISED : ProgressTracker.Step("Receiving finalised transaction") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(
            FLOW_SESSION,
            VERIFYING_AND_SIGNING,
            RECEIVE_FINALISED
        )
    }

    override val progressTracker: ProgressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {

        progressTracker.currentStep = FLOW_SESSION
        // initiate session with gateway party and send proposed wallet data
        val gatewaySession = initiateFlow(wallet.owner)
        gatewaySession.send(wallet)

        // Verify and sign the transaction.
        progressTracker.currentStep = VERIFYING_AND_SIGNING

        val signTransactionFlow = object : SignTransactionFlow(gatewaySession, VERIFYING_AND_SIGNING.childProgressTracker()) {
            override fun checkTransaction(stx: SignedTransaction) {

                val ledgerTransaction = stx.toLedgerTransaction(serviceHub, false)
                val outputState = ledgerTransaction.outputsOfType<WalletState>().single()
                val linearId = outputState.linearId

                //check if a state with same walletId exists in the vault
                logger.info("Checking for existence of state for $outputState.")
                val result = getWalletStateByWalletId(walletId = linearId.externalId.toString(), services = serviceHub)

                //if state already exists
                if (result != null) {
                    throw FlowException("Wallet ${linearId.externalId} already exists with linearId ($linearId).")
                }

                require(gatewaySession.counterparty == outputState.owner) {"Yes!"}
            }
        }
        val transactionId = subFlow(signTransactionFlow).id

        progressTracker.currentStep = RECEIVE_FINALISED
        return subFlow(ReceiveFinalityFlow(gatewaySession, expectedTxId = transactionId))

//        //construct state
//        val walletState = wallet.toState(createdBy = ourIdentity)
//
//        logger.info("Selecting Notary")
//        //select notary
//        progressTracker.currentStep = NOTARY_ID
//        val notary = serviceHub.networkMapCache.notaryIdentities.first()
//
//        logger.info("Building transaction")
//        //select command with list of required signers
//        val command = Command(WalletContract.Create(), listOf(ourIdentity.owningKey, wallet.owner.owningKey))
//        //attach contract to constructed state
//        val outputStateAndContract = StateAndContract(walletState, WalletContract.CONTRACT_ID)
//
//        //build transaction with various components
//        progressTracker.currentStep = TX_BUILDER
//        val unsignedTransaction = TransactionBuilder(notary = notary).withItems(command, outputStateAndContract)
//
//        logger.info("Verifying transaction")
//        progressTracker.currentStep = TX_VERIFICATION
//        unsignedTransaction.verify(serviceHub)
//
//        logger.info("Signing the transaction ourselves")
//        progressTracker.currentStep = TX_SIGNING
//        val partiallySignedTransaction = serviceHub.signInitialTransaction(unsignedTransaction)
//
//        logger.info("Starting flow sessions with counter parties")
//        progressTracker.currentStep = FLOW_SESSION
//        val counterpartySession = initiateFlow(wallet.owner)
//
//        logger.info("Gathering signatures form counterparties")
//        progressTracker.currentStep = GATHERING_SIGNATURES
//        val signedTransaction = subFlow(CollectSignaturesFlow(partiallySignedTransaction, listOf(counterpartySession)))
//
//        logger.info("finalising transaction")
//        progressTracker.currentStep = FINALISING
//        return subFlow(FinalityFlow(transaction = signedTransaction, sessions = listOf(counterpartySession)))

    }

}