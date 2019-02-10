package com.example.wallet.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.example.wallet.common.contracts.RecognisedIssuerContract
import com.example.wallet.common.utilities.getRecognisedIssuer
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

object DeactivateRecognisedIssuerFlow {

    class PartyNotYetRecognisedIssuerException(unrecognisedIssuer: Party) : FlowException(
        "Party $unrecognisedIssuer to be deactivated has not been added as a recognised issuer"
    )
    class IssuerPartyAlreadyDeactivatedException(recognisedIssuer: Party) : FlowException(
        "Issuer Party $recognisedIssuer has already been deactivated"
    )

    /**
     * Recognised issuers can be activated or deactivated
     * When a gateway marks a recognised issuer as deactivated, it would refuse certain transactions originating from
     * that supposed issuer.
     * Example: Wallet Creation Requests and Cash Issuance
     *
     * @param recognisedIssuerParty the party (which must have been added as a recognised issuer) to deactivate
     * @param currencyCode currency for which this [recognisedIssuerParty] is marked as it's issuer
     *
     */
    @InitiatingFlow
    @StartableByRPC
    class Initiator(private val recognisedIssuerParty: Party, private val currencyCode: String) : FlowLogic<SignedTransaction>() {

        companion object {
            object CHECK_ISSUER : ProgressTracker.Step(
                "checking that the party to be deactivated has been added as a recognised issuer and hasn't been deactivated"
            )
            object NOTARY_ID : ProgressTracker.Step("Getting Notary Identity")
            object TX_BUILDER : ProgressTracker.Step("Creating transaction builder, assigning notary, and gathering other components")

            object TX_VERIFICATION : ProgressTracker.Step("Verifying transaction")
            object TX_SIGNING : ProgressTracker.Step("Signing a transaction")
            object FINALISING : ProgressTracker.Step("Finalising transaction") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                CHECK_ISSUER,
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
            progressTracker.currentStep = CHECK_ISSUER
            val inputPreExistingRecognisedIssuerPartyStateAndRef = getRecognisedIssuer(
                recognisedIssuerParty.toString(),
                currencyCode,
                serviceHub
            ) ?: throw PartyNotYetRecognisedIssuerException(recognisedIssuerParty)

            if (!inputPreExistingRecognisedIssuerPartyStateAndRef.state.data.activated) {
                throw IssuerPartyAlreadyDeactivatedException(recognisedIssuerParty)
            }
            val outputPreExistingRecognisedIssuerPartyState = inputPreExistingRecognisedIssuerPartyStateAndRef.state.data.deactivate()
            val outputPreExistingRecognisedIssuerPartyStateAndContract = StateAndContract(
                outputPreExistingRecognisedIssuerPartyState,
                RecognisedIssuerContract.CONTRACT_ID
            )
            val deactivateRecognisedIssuerCommand = Command(RecognisedIssuerContract.Deactivate(), listOf(ourIdentity.owningKey))

            progressTracker.currentStep = NOTARY_ID
            val notary = serviceHub.networkMapCache.notaryIdentities.first()

            val txBuilder = TransactionBuilder(notary = notary).withItems(
                inputPreExistingRecognisedIssuerPartyStateAndRef,
                outputPreExistingRecognisedIssuerPartyStateAndContract,
                deactivateRecognisedIssuerCommand
            )

            progressTracker.currentStep = TX_VERIFICATION
            txBuilder.verify(serviceHub)

            progressTracker.currentStep = TX_SIGNING
            val signedTransaction = serviceHub.signInitialTransaction(txBuilder)

            progressTracker.currentStep = FINALISING
            return subFlow(FinalityFlow(signedTransaction, listOf()))

        }

    }
}