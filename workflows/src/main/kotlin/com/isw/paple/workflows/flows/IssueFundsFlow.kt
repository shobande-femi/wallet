package com.isw.paple.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.isw.paple.common.contracts.IssuanceContract
import com.isw.paple.common.contracts.WalletContract
import com.isw.paple.common.states.IssuanceState
import com.isw.paple.common.types.IssuanceStatus
import com.isw.paple.common.utilities.getWalletStateByWalletId
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.ProgressTracker
import net.corda.finance.contracts.asset.Cash
import java.util.*

object IssueFundsFlow {

    class NonExistentWalletException(walletId: String) : FlowException("Wallet with id: $walletId doesn't exist")
    class UnacceptableCurrencyException(walletId: String, currency: Currency) : FlowException("Wallet $walletId be funded with $currency")

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
            logger.info("Starting IssueFundsFlow.Initiator")

            logger.info("Checking if wallet with $walletId exists")
            val inputWalletStateAndRef = getWalletStateByWalletId(walletId = walletId, services = serviceHub) ?: throw NonExistentWalletException(walletId)

            val inputWalletState = inputWalletStateAndRef.state.data
            if (amount.token != inputWalletState.balance.token) {
                throw UnacceptableCurrencyException(walletId, amount.token)
            }

            //TODO: funding limits and balance limits, also check if wallet verified

            logger.info("Selecting notary identity")
            progressTracker.currentStep = NOTARY_ID
            val notary = serviceHub.networkMapCache.notaryIdentities.first()

            logger.info("Constructing tx")
            val fundCommand = Command(WalletContract.IssueFunds(), listOf(ourIdentity.owningKey, inputWalletState.owner.owningKey))
            val outputWalletState = inputWalletState.withNewBalance(inputWalletState.balance.plus(amount))
            val outputWalletStateAndContract = StateAndContract(outputWalletState, WalletContract.CONTRACT_ID)

            val issueCashCommand = Command(Cash.Commands.Issue(), listOf(ourIdentity.owningKey))
            val issuerAndToken = Issued(PartyAndReference(ourIdentity, OpaqueBytes.of(0)), amount.token)
            val outputCashState = Cash.State(amount = Amount(amount.quantity, issuerAndToken), owner = inputWalletState.owner)
            //TODO include onlyFromParties, to ensure gateways only accept cash issued from recognised issuers
            val outputCashStateAndContract = StateAndContract(outputCashState, Cash.PROGRAM_ID)

            val createIssuanceCommand = Command(IssuanceContract.Create(), listOf(ourIdentity.owningKey, inputWalletState.owner.owningKey))
            val outputIssuanceState = IssuanceState(ourIdentity, inputWalletState.owner, amount, IssuanceStatus.UNKNOWN)
            val outputIssuanceStateAndContract = StateAndContract(outputIssuanceState, IssuanceContract.CONTRACT_ID)

            //TODO: update the lastUpdated field on wallet state
            progressTracker.currentStep = TX_BUILDER
            val txBuilder = TransactionBuilder(notary = notary)
                .withItems(
                    fundCommand,
                    inputWalletStateAndRef,
                    outputWalletStateAndContract,

                    issueCashCommand,
                    outputCashStateAndContract,

                    createIssuanceCommand,
                    outputIssuanceStateAndContract
                )

            logger.info("Verifying tx")
            progressTracker.currentStep = TX_VERIFICATION
            txBuilder.verify(serviceHub)

            logger.info("Signing tx")
            progressTracker.currentStep = SIGNING
            val partiallySignedTx = serviceHub.signInitialTransaction(txBuilder)

            logger.info("Input Wallet state is $inputWalletState")
            logger.info("Output Wallet State is $outputWalletState")
            logger.info("Output Cash State is $outputCashState")


            logger.info("Initiating flow session")
            progressTracker.currentStep = FLOW_SESSION
            val gatewaySession = initiateFlow(inputWalletState.owner)

            logger.info("Collecting signatures from counter parties")
            progressTracker.currentStep = COLLECTING_SIGNATURES
            val signedTx = subFlow(CollectSignaturesFlow(partiallySignedTx, listOf(gatewaySession)))

            logger.info("Finalising tx")
            progressTracker.currentStep = FINALISING
            return subFlow(FinalityFlow(transaction = signedTx, sessions = listOf(gatewaySession)))
        }

    }

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
            logger.info("Starting IssueFundsFlow.Responder")
            progressTracker.currentStep = VALIDATE_AND_SIGN

            val signTxFlow = object : SignTransactionFlow(issuerSession, VALIDATE_AND_SIGN.childProgressTracker()) {
                @Suspendable
                override fun checkTransaction(stx: SignedTransaction) {
                    logger.info("Validating tx before appending signature")
//                    val ledgerTx = stx.toLedgerTransaction(serviceHub, false)
//                    val inputWalletState = ledgerTx.inputsOfType<WalletState>().single()
//                    val outputWalletState = ledgerTx.outputsOfType<WalletState>().single()
//                    val outputCashState = ledgerTx.outputsOfType<Cash.State>().single()
//
//                    require(outputWalletState.owner == ourIdentity) {"Gateway signing this transaction must be owner"}
//                    require(outputWalletState.type == WalletType.GATEWAY_OWNED) {"Wallet type must be gateway owned"}
//                    require(outputWalletState.balance > inputWalletState.balance)
//
//                    require(outputCashState.amount == outputWalletState.balance.minus(inputWalletState.balance)) {"difference in wallet balance must equal output cash state"}
//                    require(outputCashState.owner == ourIdentity) {"gateway party must own the out cash states"}
//                    require(outputCashState.exitKeys.contains(ourIdentity.owningKey)) {"gateway identity must be one of exit kets"}
                }
            }
            logger.info("Appending signature")
            val txId = subFlow(signTxFlow).id
//            return waitForLedgerCommit(txId)

            logger.info("Receiving finalised tx")
            progressTracker.currentStep = RECEIVE_FINALISED
            return subFlow(ReceiveFinalityFlow(issuerSession, expectedTxId = txId))
        }

    }
}