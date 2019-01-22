package com.isw.paple.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.isw.paple.common.contracts.RecognisedIssuerContract
import com.isw.paple.common.states.RecognisedIssuerState
import com.isw.paple.common.utilities.getRecognisedIssuerStateByIssuerName
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

object AddRecognisedIssuerFlow {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(private val issuerParty: Party) : FlowLogic<SignedTransaction>() {

        companion object {
            // TODO: fine tune progress tracker
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

        override val progressTracker: ProgressTracker =
            tracker()

        @Suspendable
        override fun call(): SignedTransaction {

            //TODO: check that proposed issuer party is well known
            val recognisedIssuerState = RecognisedIssuerState(issuerParty, ourIdentity)

            logger.info("Checking for existence of state for $recognisedIssuerState.")
            val result = getRecognisedIssuerStateByIssuerName(issuerParty.toString(), serviceHub)

            if (result != null) {
                val linearId = result.state.data.linearId
                throw IllegalArgumentException("Wallet $recognisedIssuerState already exists with linearId ($linearId).")
            }

            logger.info("No state for $recognisedIssuerState. Adding it.")

            progressTracker.currentStep = NOTARY_ID
            val notary = serviceHub.networkMapCache.notaryIdentities.first()

            // The node running this flow is always the only signer.
            val command = Command(RecognisedIssuerContract.Add(), listOf(ourIdentity.owningKey))
            val outputStateAndContract = StateAndContract(recognisedIssuerState, RecognisedIssuerContract.CONTRACT_ID)
            progressTracker.currentStep = TX_BUILDER
            val unsignedTransaction = TransactionBuilder(notary = notary).withItems(command, outputStateAndContract)

            // TODO: verify transaction

            progressTracker.currentStep = TX_SIGNING
            val signedTransaction = serviceHub.signInitialTransaction(unsignedTransaction)

            progressTracker.currentStep = FINALISING
            return subFlow(FinalityFlow(signedTransaction, listOf()))

        }

    }

}