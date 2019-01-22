package com.isw.paple.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.isw.paple.common.contracts.WalletContract
import com.isw.paple.common.states.WalletState
import com.isw.paple.common.types.Wallet
import com.isw.paple.common.types.WalletType
import com.isw.paple.common.types.toState
import com.isw.paple.common.utilities.getRecognisedIssuerStateByIssuerName
import com.isw.paple.common.utilities.getWalletStateByWalletId
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap


object CreateWalletFlow {
    @CordaSerializable
    data class AssembledTransaction(val tx: TransactionBuilder)

    class UnrecognizedIssuerException(unrecognisedIssuer: Party) : FlowException("Initiating party: $unrecognisedIssuer is an unrecognised Issuer")

    /**
     * Adds a new [Wallet] state to the ledger.
     * The state is always added as a "uni-lateral state" to the node calling this flow.
     */
    @InitiatingFlow
    @StartableByRPC
    class Initiator(private val wallet: Wallet) : FlowLogic<SignedTransaction>() {

        companion object {
            // TODO: fine tune progress tracker
            object FLOW_SESSION: ProgressTracker.Step("Initiation flow session with gateway party")
            object NOTARY_ID : ProgressTracker.Step("Getting Notary Identity")
            object GENERATING : ProgressTracker.Step("Generating tx")
            object VERIFYING: ProgressTracker.Step("Verifying tx")
            object SIGNING : ProgressTracker.Step("Signing tx")
            object VALIDATE_AND_SIGN : ProgressTracker.Step("Validating and signing transaction from gateway") {
                override fun childProgressTracker() = SignTransactionFlow.tracker()
            }
            object RECEIVE_FINALISED : ProgressTracker.Step("Receiving finalised transaction from gateway") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }
            object FINALISING : ProgressTracker.Step("Finalising transaction")

            fun tracker() = ProgressTracker(
                FLOW_SESSION,
                NOTARY_ID,
                GENERATING,
                VERIFYING,
                SIGNING,
                VALIDATE_AND_SIGN,
                RECEIVE_FINALISED,
                FINALISING
            )
        }

        override val progressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {

            logger.info("Checking if state with externalId: ${wallet.walletId} exists.")
            val result = getWalletStateByWalletId(walletId = wallet.walletId, services = serviceHub)
            if (result != null) {
                throw FlowException("Wallet ${wallet.walletId} already exists with linearId: (${result.state.data.linearId}).")
            }

            return when (wallet.type) {
                WalletType.GATEWAY_OWNED -> {
                    createGatewayOwnedWallet(wallet)
                }
                else -> {
                    createWallet(wallet)
                }
            }

        }

        @Suspendable
        private fun createGatewayOwnedWallet(wallet: Wallet) : SignedTransaction {
            //Gateway party for which this wallet is being created be aware of the proposed
            // wallet creation and sign its creation
            //Hence, a flow session with this gateway party should be started and the proposed wallet
            //object be sent to this gateway
            progressTracker.currentStep = FLOW_SESSION
            val gatewaySession = initiateFlow(wallet.owner)
            gatewaySession.send(wallet)

            // TODO: if the other flow throws an [IllegalStateException] try kicking off a flow to Add our self as a recognized issuer

            // After the gateway receives and validates the wallet to be created
            // The gateway builds a transaction and requests for the issuer's signature
            // Of course, the issuer must validate the authenticity of such transaction before appending its signature
            progressTracker.currentStep = VALIDATE_AND_SIGN
            val signTransactionFlow = object : SignTransactionFlow(gatewaySession, VALIDATE_AND_SIGN.childProgressTracker()) {
                override fun checkTransaction(stx: SignedTransaction) {
                    val ledgerTransaction = stx.toLedgerTransaction(serviceHub, false)
                    val outputState = ledgerTransaction.outputsOfType<WalletState>().single()

                    require(wallet.walletId == outputState.linearId.externalId) {"wallet Id doesn't match initial value"}
                    require(wallet.type == outputState.type) {"wallet type doesn't match initial value"}
                    require(gatewaySession.counterparty == outputState.owner) {"Gateway signing this transaction must be owner"}
                    require(wallet.amount == outputState.amount) {"wallet amount doesn't match initial value"}
                    require(wallet.status == outputState.status) {"wallet status doesn't match initial value"}
                }
            }
            val txId = subFlow(signTransactionFlow).id

            progressTracker.currentStep = RECEIVE_FINALISED
            return subFlow(ReceiveFinalityFlow(gatewaySession, expectedTxId = txId))
        }

        @Suspendable
        private fun createWallet(wallet: Wallet) : SignedTransaction {
            // Only gateway wallet kinds require a signature other than the issuer's when creating them
            // Issuer owned wallets may be for collecting transaction fees, or whatever reason the issuer deems fit
            // Subsequently, issuer owned wallets should be broadcasted to network participants

            val unsignedTx = assembleTx(wallet, ourIdentity)
            progressTracker.currentStep = SIGNING
            val signedTx = serviceHub.signInitialTransaction(unsignedTx.tx)

            progressTracker.currentStep = FINALISING
            return subFlow(FinalityFlow(transaction = signedTx, sessions = listOf()))
        }

        @Suspendable
        private fun assembleTx(wallet: Wallet, issuerParty: Party): AssembledTransaction {
            progressTracker.currentStep = NOTARY_ID
            val notary = serviceHub.networkMapCache.notaryIdentities.first()

            progressTracker.currentStep = GENERATING
            val walletState = wallet.toState(createdBy = issuerParty)

            val command = Command(WalletContract.Create(), listOf(issuerParty.owningKey))
            val outputStateAndContract = StateAndContract(walletState, WalletContract.CONTRACT_ID)
            val unsignedTx = TransactionBuilder(notary = notary).withItems(command, outputStateAndContract)

            progressTracker.currentStep = VERIFYING
            unsignedTx.verify(serviceHub)

            return AssembledTransaction(unsignedTx)
        }
    }



    @InitiatedBy(Initiator::class)
    class Responder(private val issuerSession: FlowSession) : FlowLogic<SignedTransaction>() {

        companion object {
            // TODO: fine tune progress tracker
            object RECEIVING : ProgressTracker.Step("Receiving wallet info")
            object VALIDATING : ProgressTracker.Step("Validating wallet info")
            object NOTARY_ID : ProgressTracker.Step("Getting Notary Identity")
            object GENERATING : ProgressTracker.Step("Generating tx")
            object SIGNING : ProgressTracker.Step("Signing tx")
            object COLLECTING_SIGNATURES : ProgressTracker.Step("Collecting signatures from other parties") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }
            object FINALISING : ProgressTracker.Step("Finalising tx") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                RECEIVING,
                VALIDATING,
                NOTARY_ID,
                GENERATING,
                SIGNING,
                COLLECTING_SIGNATURES,
                FINALISING
            )
        }

        override val progressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            //there is a prerecorded list of issuers that this gateway recognises
            //the [AddRecognisedIssuers] flow is used to add more issuers
            //we need to check that the party initiating this session is a recognised issuer
            getRecognisedIssuerStateByIssuerName(issuerSession.counterparty.toString(), serviceHub) ?: throw UnrecognizedIssuerException(issuerSession.counterparty)

            progressTracker.currentStep = RECEIVING
            val wallet = receiveAndValidateWalletInfo()

            // Put together a proposed transaction that performs wallet creation.
            progressTracker.currentStep = SIGNING
            val unsignedTx = assembleSharedTx(wallet, issuerSession.counterparty)
            val partiallySignedTx = serviceHub.signInitialTransaction(unsignedTx.tx)

            progressTracker.currentStep = COLLECTING_SIGNATURES
            val signedTx = subFlow(CollectSignaturesFlow(partiallySignedTx, listOf(issuerSession)))

            progressTracker.currentStep = FINALISING
            return subFlow(FinalityFlow(transaction = signedTx, sessions = listOf(issuerSession)))
        }

        @Suspendable
        private fun receiveAndValidateWalletInfo(): Wallet {
            return issuerSession.receive<Wallet>().unwrap {
                progressTracker.currentStep = VALIDATING
                require(it.owner == ourIdentity) {"We must own this wallet before signing it's creation"}
                require(it.amount.quantity == 0L) {"Wallet funding and creation cannot occur simultaneously"}
                require(it.type == WalletType.GATEWAY_OWNED) {"Wallet type must be workflows owned"}
                it
            }
        }

        @Suspendable
        private fun assembleSharedTx(wallet: Wallet, issuerParty: Party): AssembledTransaction {

            progressTracker.currentStep = NOTARY_ID
            val notary = serviceHub.networkMapCache.notaryIdentities.first()

            progressTracker.currentStep = GENERATING
            val walletState = wallet.toState(createdBy = issuerParty)

            val command = Command(WalletContract.Create(), listOf(issuerParty.owningKey, ourIdentity.owningKey))
            val outputStateAndContract = StateAndContract(walletState, WalletContract.CONTRACT_ID)
            val unsignedTx = TransactionBuilder(notary = notary).withItems(command, outputStateAndContract)

            return AssembledTransaction(unsignedTx)
        }
    }
}