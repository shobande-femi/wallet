package com.example.wallet.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.example.wallet.common.contracts.WalletContract
import com.example.wallet.common.states.WalletState
import com.example.wallet.common.types.Wallet
import com.example.wallet.common.types.WalletType
import com.example.wallet.common.types.toState
import com.example.wallet.common.utilities.getActivatedRecognisedIssuerByIssuerName
import com.example.wallet.common.utilities.getWalletStateByWalletId
import com.example.wallet.workflows.flows.CreateWalletFlow.Responder
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

/**
 * Flow Pair for the creation of new wallets
 *
 * The [Responder] flow is only needed for Gateway owned Wallets
 */
object CreateWalletFlow {
    @CordaSerializable
    data class AssembledTransaction(val tx: TransactionBuilder)

    class WalletExistsException(walletId: String) : FlowException("Wallet $walletId already exists")
    class UnrecognizedIssuerException(unrecognisedIssuer: Party) : FlowException("Initiating party: $unrecognisedIssuer is an unrecognised Issuer")
    class UnspecifiedRecipient : FlowException("If wallet type is Gateway Owned, a recipient party other than our self must be provided")
    class WeAreNotOwnerException: FlowException("Other than gateway wallet kinds, issuing party must be wallet owner")
    /**
     * Adds a new [Wallet] state to the ledger.
     *
     * The flow ensures a [WalletState] with the proposed wallet id doesn't already exist.
     * All wallet types except Gateway Owned wallets are really owned by the issuer, hence, no other party (but the issuer)
     * need to sign the creation of these wallets.
     * On the other hand, Gateways must be aware and append their signature to the creation of a gateway owned wallet,
     * where they are the wallet owner
     *
     * @param wallet the information of the wallet to be created
     */
    @InitiatingFlow
    @StartableByRPC
    class Initiator(private val wallet: Wallet) : FlowLogic<SignedTransaction>() {

        companion object {
            // TODO: fine tune progress tracker
            object FLOW_SESSION: ProgressTracker.Step("Initiation flow session with counter party")
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

            val preExistingWalletStateAndRef = getWalletStateByWalletId(walletId = wallet.walletId, services = serviceHub)
            if (preExistingWalletStateAndRef != null) {
                throw WalletExistsException(wallet.walletId)
            }

            return when (wallet.type) {
                WalletType.GATEWAY_OWNED -> {
                    if (wallet.owner == ourIdentity) throw UnspecifiedRecipient()
                    createGatewayOwnedWallet(wallet)
                }
                else -> {
                    if (wallet.owner != ourIdentity) throw WeAreNotOwnerException()
                    createWallet()
                }
            }

        }

        @Suspendable
        private fun createGatewayOwnedWallet(wallet: Wallet) : SignedTransaction {
            //Gateway party for which this wallet is being created must be aware of the proposed
            // wallet creation and sign its creation
            //Hence, a flow session with this gateway party should be started and the proposed wallet
            //object be sent to this gateway
            progressTracker.currentStep = FLOW_SESSION
            val gatewaySession = initiateFlow(wallet.owner)
            gatewaySession.send(wallet)

            // After the gateway receives and validates the wallet to be created
            // The gateway builds a transaction and requests for the issuer's signature
            // Of course, the issuer must validate the authenticity of such transaction before appending its signature
            progressTracker.currentStep = VALIDATE_AND_SIGN
            val signTransactionFlow = object : SignTransactionFlow(gatewaySession, VALIDATE_AND_SIGN.childProgressTracker()) {
                @Suspendable
                override fun checkTransaction(stx: SignedTransaction) {
                    val ledgerTransaction = stx.toLedgerTransaction(serviceHub, false)
                    val outputState = ledgerTransaction.outputsOfType<WalletState>().single()

                    require(outputState.issuedBy == ourIdentity) {"If we sign its creation, we must be the issuer"}
                    require(wallet.walletId == outputState.linearId.externalId) {"wallet Id doesn't match initial value"}
                    require(wallet.type == outputState.type) {"wallet type doesn't match initial value"}
                    require(gatewaySession.counterparty == outputState.owner) {"Gateway signing this transaction must be owner"}
                    require(wallet.balance == outputState.balance) {"wallet balance doesn't match initial value"}
                    require(wallet.status == outputState.status) {"wallet status doesn't match initial value"}
                }
            }
            //take not of the id of the transaction we signed
            //this is necessary so we do not record a finalised transaction that we did not sign
            val txId = subFlow(signTransactionFlow).id

            progressTracker.currentStep = RECEIVE_FINALISED
            return subFlow(ReceiveFinalityFlow(gatewaySession, expectedTxId = txId))
        }

        @Suspendable
        private fun createWallet() : SignedTransaction {
            // Only gateway wallet kinds require a signature other than the issuer's when creating them
            // Issuer owned wallets may be for collecting transaction fees, or whatever reason the issuer deems fit
            // Subsequently, issuer owned wallets should be broadcasted to network participants

            val txBuilder = assembleTx()
            progressTracker.currentStep = SIGNING
            val signedTx = serviceHub.signInitialTransaction(txBuilder.tx)

            progressTracker.currentStep = FINALISING
            return subFlow(FinalityFlow(transaction = signedTx, sessions = listOf()))
        }

        @Suspendable
        private fun assembleTx(): AssembledTransaction {
            progressTracker.currentStep = NOTARY_ID
            val notary = serviceHub.networkMapCache.notaryIdentities.first()

            progressTracker.currentStep = GENERATING
            val walletState = wallet.toState(issuedBy = ourIdentity)

            val command = Command(WalletContract.Create(), listOf(ourIdentity.owningKey))
            val outputStateAndContract = StateAndContract(walletState, WalletContract.CONTRACT_ID)
            val txBuilder = TransactionBuilder(notary = notary).withItems(command, outputStateAndContract)

            progressTracker.currentStep = VERIFYING
            txBuilder.verify(serviceHub)

            return AssembledTransaction(txBuilder)
        }
    }


    /**
     * In here, the Gateway receives the information of the proposed wallet to be created, validated the validity of
     * this information, ensures that the initiator of the creation request is a recognised Issuer.
     *
     * This means, prior to this transaction, this Gateway must have added the initiator as a Recognised Issuer by starting
     * the AddRecognisedIssuerFlow.
     * The initiator must not just exist as a recognised issuer, it must be an activated recognised issuer before this gateway
     * responds favorable
     *
     * After all verification, this gateway assembles the wallet creation transaction, a requests the signature of all
     * counterparties involved in this transaction (in this case, the initiator/issuer)
     *
     * @param issuerSession the initiator of this flow
     */
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
            progressTracker.currentStep = RECEIVING
            val wallet = receiveAndValidateWalletInfo()

            //there is a prerecorded list of issuers that this gateway recognises
            //the [AddRecognisedIssuers] flow is used to add more issuers
            //we need to check that the party initiating this session is an activated recognised issuer
            getActivatedRecognisedIssuerByIssuerName(
                wallet.balance.token.currencyCode,
                issuerSession.counterparty.toString(),
                serviceHub
            ) ?: throw UnrecognizedIssuerException(issuerSession.counterparty)

            // Put together a proposed transaction that performs wallet creation.
            progressTracker.currentStep = SIGNING
            val txBuilder = assembleSharedTx(wallet, issuerSession.counterparty)
            val partiallySignedTx = serviceHub.signInitialTransaction(txBuilder.tx)

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
                require(it.balance.quantity == 0L) {"On wallet creation, wallet balance must be zero"}
                require(it.type == WalletType.GATEWAY_OWNED) {"Wallet type must be gateway owned"}
                it
            }
        }

        @Suspendable
        private fun assembleSharedTx(wallet: Wallet, issuerParty: Party): AssembledTransaction {

            progressTracker.currentStep = NOTARY_ID
            val notary = serviceHub.networkMapCache.notaryIdentities.first()

            progressTracker.currentStep = GENERATING
            val walletState = wallet.toState(issuedBy = issuerParty)

            val command = Command(WalletContract.Create(), listOf(issuerParty.owningKey, ourIdentity.owningKey))
            val outputStateAndContract = StateAndContract(walletState, WalletContract.CONTRACT_ID)
            val txBuilder = TransactionBuilder(notary = notary).withItems(command, outputStateAndContract)

            return AssembledTransaction(txBuilder)
        }
    }
}