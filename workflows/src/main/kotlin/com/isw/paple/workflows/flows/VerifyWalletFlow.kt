package com.isw.paple.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.isw.paple.common.contracts.WalletContract
import com.isw.paple.common.utilities.getWalletStateByWalletId
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

object VerifyWalletFlow {

    class NonExistentWalletException(walletId: String) : FlowException("Wallet with id: $walletId doesn't exist")
    class WalletAlreadyVerifiedException(walletId: String): FlowException("Wallet $walletId is already verified")

    @InitiatingFlow
    @StartableByRPC
    class Initiator(private val walletId: String) : FlowLogic<SignedTransaction>() {

        companion object {
            object NOTARY_ID : ProgressTracker.Step("Getting Notary Identity")
            object TX_BUILDER : ProgressTracker.Step("Creating transaction builder, assigning notary, and gathering other components")

            object TX_VERIFICATION : ProgressTracker.Step("Verifying transaction")
            object TX_SIGNING : ProgressTracker.Step("Signing a transaction")
            object FINALISING : ProgressTracker.Step("Finalising transaction") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                NOTARY_ID,
                TX_BUILDER,
                TX_VERIFICATION,
                TX_SIGNING,
                FINALISING
            )
        }

        override val progressTracker: ProgressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {

            val inputWalletStateAndRef = getWalletStateByWalletId(walletId = walletId, services = serviceHub) ?: throw NonExistentWalletException(walletId)
            if (inputWalletStateAndRef.state.data.verified) {
                throw WalletAlreadyVerifiedException(walletId)
            }

            val outputWalletState = inputWalletStateAndRef.state.data.verifyWallet()
            val outputWalletStateAndContract = StateAndContract(outputWalletState, WalletContract.CONTRACT_ID)
            val verifyWalletCommand = Command(
                WalletContract.VerifyWallet(),
                listOf(inputWalletStateAndRef.state.data.issuedBy.owningKey)
            )


            progressTracker.currentStep = NOTARY_ID
            val notary = serviceHub.networkMapCache.notaryIdentities.first()

            val txBuilder = TransactionBuilder(notary = notary).withItems(
                inputWalletStateAndRef,
                outputWalletStateAndContract,
                verifyWalletCommand
            )

            progressTracker.currentStep = TX_VERIFICATION
            txBuilder.verify(serviceHub)

            progressTracker.currentStep = TX_SIGNING
            val signedTransaction = serviceHub.signInitialTransaction(txBuilder)

            val participantSessions = (inputWalletStateAndRef.state.data.participants)
                .toSet()
                .filter { it != ourIdentity }
                .map {
                    initiateFlow(it as Party)
                }

            progressTracker.currentStep = FINALISING
            return subFlow(FinalityFlow(signedTransaction, participantSessions))
        }

    }

    @InitiatedBy(Initiator::class)
    class Responder(private val counterPartySession: FlowSession) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            //TODO some checks before recording the finalised transaction?
            return subFlow(ReceiveFinalityFlow(counterPartySession))
        }

    }
}