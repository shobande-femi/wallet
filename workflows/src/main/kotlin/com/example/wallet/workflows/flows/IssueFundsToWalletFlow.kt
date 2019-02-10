package com.example.wallet.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.example.wallet.common.contracts.IssuanceContract
import com.example.wallet.common.contracts.WalletContract
import com.example.wallet.common.states.IssuanceState
import com.example.wallet.common.states.WalletState
import com.example.wallet.common.types.IssuanceStatus
import com.example.wallet.common.types.WalletType
import com.example.wallet.common.utilities.getActivatedRecognisedIssuer
import com.example.wallet.common.utilities.getWalletStateByWalletIdAndWalletType
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.ProgressTracker
import net.corda.finance.contracts.asset.Cash
import java.util.*

/**
 * Issues some Cash to Gateway Owned Wallets
 * Only the issuer is able to successfully initiate this flow as Gateways would not accept and Issuance unless it
 * originates from a recognised Issuer.
 * Before starting this flow, Gateways must can add a recognized Issuer by running the [AddRecognisedIssuerFlow]
 *
 */
object IssueFundsToWalletFlow {

    /**
     * Issues some Cash to Gateway Owned Wallets
     * Only the issuer is able to successfully initiate this flow as Gateways would not accept and Issuance unless it
     * originates from a recognised Issuer.
     * Gateways can add a recognized Issuer by running the [AddRecognisedIssuerFlow]
     *
     * This flow follows this process
     * 1. Fetch the gateway owned wallet by walletId
     * 2. Every wallet is denominated in a specific currency. Hence, check that the currency being issued matches the
     * destination wallet
     * 3. The transaction to be built tries to accomplish 3 major things
     *      i. Issue some Cash State, specifying the gateway as the owner
     *      ii. Increase the gateway's wallet balance (essentially funding it)
     *      iii. Create and Issuance Receipt
     * 4. Send the built transaction to the gateway to append it's signature
     *
     *
     * @param walletId Id of a wallet
     * @param amount quantity of token(currency in this case) to issue to the gateway
     */
    @InitiatingFlow
    @StartableByRPC
    class Initiator(private val walletId: String, private val amount: Amount<Currency>) : FlowLogic<SignedTransaction>() {

        companion object {
            // TODO: fine tune progress tracker
            object NOTARY_ID : ProgressTracker.Step("Getting Notary Identity")
            object TX_BUILDER : ProgressTracker.Step("Creating transaction builder, assigning notary, and gathering other components")

            object TX_VERIFICATION : ProgressTracker.Step("Verifying transaction")
            object SIGNING : ProgressTracker.Step("Signing tx")
            object FLOW_SESSION: ProgressTracker.Step("Initiation flow session with counter party")
            object COLLECTING_SIGNATURES : ProgressTracker.Step("Collecting signatures from other parties") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }
            object FINALISING : ProgressTracker.Step("Finalising transaction") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                NOTARY_ID,
                TX_BUILDER,
                TX_VERIFICATION,
                SIGNING,
                FLOW_SESSION,
                COLLECTING_SIGNATURES,
                FINALISING
            )
        }

        override val progressTracker: ProgressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            logger.info("Starting IssueFundsToWalletFlow.Initiator")

            logger.info("Checking if wallet with $walletId exists")
            val inputWalletStateAndRef = getWalletStateByWalletIdAndWalletType(walletId = walletId, type = WalletType.GATEWAY_OWNED, services = serviceHub) ?: throw NonExistentWalletException(walletId)

            val txBuilder = assembleTx(inputWalletStateAndRef)

            logger.info("Verifying tx")
            progressTracker.currentStep = TX_VERIFICATION
            txBuilder.verify(serviceHub)

            logger.info("Signing tx")
            progressTracker.currentStep = SIGNING
            val partiallySignedTx = serviceHub.signInitialTransaction(txBuilder)

            logger.info("Initiating flow session")
            progressTracker.currentStep = FLOW_SESSION
            val gatewaySession = initiateFlow(inputWalletStateAndRef.state.data.owner)

            logger.info("Collecting signatures from counter parties")
            progressTracker.currentStep = COLLECTING_SIGNATURES
            val signedTx = subFlow(CollectSignaturesFlow(partiallySignedTx, listOf(gatewaySession)))

            logger.info("Finalising tx")
            progressTracker.currentStep = FINALISING
            return subFlow(FinalityFlow(transaction = signedTx, sessions = listOf(gatewaySession)))
        }

        private fun validate(inputWalletState: WalletState) {
            //every wallet is denominated in a specific currency, ensure the currency being issued matches the wallet denomiation
            if (amount.token != inputWalletState.balance.token) {
                throw UnacceptableCurrencyException(walletId, amount.token)
            }

            //TODO: funding limits and balance limits

        }

        private fun assembleTx(inputWalletStateAndRef: StateAndRef<WalletState>): TransactionBuilder {
            val inputWalletState = inputWalletStateAndRef.state.data

            validate(inputWalletState)

            logger.info("Selecting notary identity")
            progressTracker.currentStep = NOTARY_ID
            val notary = serviceHub.networkMapCache.notaryIdentities.first()

            logger.info("Constructing tx")
            val issueFundCommand = Command(WalletContract.IssueFund(), listOf(ourIdentity.owningKey, inputWalletState.owner.owningKey))
            val outputWalletState = inputWalletState.withNewBalance(inputWalletState.balance.plus(amount))
            val outputWalletStateAndContract = StateAndContract(outputWalletState, WalletContract.CONTRACT_ID)

            val issueCashCommand = Command(Cash.Commands.Issue(), listOf(ourIdentity.owningKey))
            val issuerAndToken = Issued(PartyAndReference(ourIdentity, OpaqueBytes.of(0)), amount.token)
            val outputCashState = Cash.State(amount = Amount(amount.quantity, issuerAndToken), owner = inputWalletState.owner)
            val outputCashStateAndContract = StateAndContract(outputCashState, Cash.PROGRAM_ID)

            val createIssuanceCommand = Command(IssuanceContract.Create(), listOf(ourIdentity.owningKey, inputWalletState.owner.owningKey))
            val outputIssuanceState = IssuanceState(ourIdentity, inputWalletState.owner, amount, IssuanceStatus.UNKNOWN)
            val outputIssuanceStateAndContract = StateAndContract(outputIssuanceState, IssuanceContract.CONTRACT_ID)

            //TODO: update the lastUpdated field on wallet state
            progressTracker.currentStep = TX_BUILDER
            return TransactionBuilder(notary = notary)
                .withItems(
                    issueFundCommand,
                    inputWalletStateAndRef,
                    outputWalletStateAndContract,

                    issueCashCommand,
                    outputCashStateAndContract,

                    createIssuanceCommand,
                    outputIssuanceStateAndContract
                )
        }

    }

    /**
     * The gateway for whom funds are to be issued, receives the request to append it's signature to the
     * transaction proposal.
     * This gateway may perform extra validations over the transaction, then accept or decline to append it's signature
     *
     * @param issuerSession The session which is providing the transaction to sign
     */
    @InitiatedBy(Initiator::class)
    class Responder(private val issuerSession: FlowSession) : FlowLogic<SignedTransaction>() {

        companion object {
            object VALIDATE_AND_SIGN : ProgressTracker.Step("Validating and signing transaction from gateway") {
                override fun childProgressTracker() = SignTransactionFlow.tracker()
            }
            object RECEIVE_FINALISED : ProgressTracker.Step("Receiving finalised transaction from gateway") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(VALIDATE_AND_SIGN, RECEIVE_FINALISED)
        }

        override val progressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            logger.info("Starting IssueFundsToWalletFlow.Responder")
            progressTracker.currentStep = VALIDATE_AND_SIGN
            val signTxFlow = object : SignTransactionFlow(issuerSession, VALIDATE_AND_SIGN.childProgressTracker()) {
                @Suspendable
                override fun checkTransaction(stx: SignedTransaction) {
                    logger.info("Validating tx before appending signature")
                    val ledgerTx = stx.toLedgerTransaction(serviceHub, false)

                    require(ledgerTx.commandsOfType<WalletContract.IssueFund>().size == 1) {
                        "Transaction must involve fund issuance to a wallet"
                    }
                    require(ledgerTx.commandsOfType<Cash.Commands.Issue>().size == 1) {
                        "Transaction must involve cash issuance"
                    }
                    require(ledgerTx.commandsOfType<IssuanceContract.Create>().size == 1) {
                        "Transaction must produce an issuance receipt"
                    }
                    require(ledgerTx.outputsOfType<WalletState>().single().owner == ourIdentity) {
                        "Gateway signing this transaction must be wallet owner"
                    }
                    val outputCashState = ledgerTx.outputsOfType<Cash.State>().single()
                    val activatedRecognisedIssuerStateAndRef = getActivatedRecognisedIssuer(
                        outputCashState.amount.token.product.currencyCode,
                        serviceHub
                    ) ?: throw NoActivatedRecognisedIssuerException()
                    require(outputCashState.amount.token.issuer.party == activatedRecognisedIssuerStateAndRef.state.data.issuer) {
                        "Can only accept cash from an activated recognised issuer"
                    }
                }
            }
            logger.info("Appending signature")
            val txId = subFlow(signTxFlow).id

            logger.info("Receiving finalised tx")
            progressTracker.currentStep = RECEIVE_FINALISED
            return subFlow(ReceiveFinalityFlow(issuerSession, expectedTxId = txId))
        }

    }

    class NoActivatedRecognisedIssuerException : FlowException("Cannot find an activated recognised issuer in vault")
    class NonExistentWalletException(walletId: String) : FlowException("Wallet with id: $walletId doesn't exist")
    class UnacceptableCurrencyException(walletId: String, currency: Currency) : FlowException("Wallet $walletId be funded with $currency")

}