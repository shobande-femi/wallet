package com.isw.paple.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.isw.paple.common.contracts.RecognisedIssuerContract
import com.isw.paple.common.utilities.getActivatedRecognisedIssuer
import com.isw.paple.common.utilities.getRecognisedIssuer
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

object ActivateRecognisedIssuerFlow {

    class PartyNotYetRecognisedIssuerException(unrecognisedIssuer: Party) : FlowException(
        "Party $unrecognisedIssuer to be activated has not been added as a recognised issuer"
    )
    class IssuerPartyAlreadyActivatedException(recognisedIssuer: Party) : FlowException(
        "Issuer Party $recognisedIssuer has already been activated"
    )

    /**
     * Recognised issuers can be activated or deactivated
     * When a gateway marks a recognised issuer as deactivated, it would refuse certain transactions originating from
     * that supposed issuer.
     * Example: Wallet Creation Requests and Cash Issuance
     *
     * At any point in time, there can be only one activated recognised issuer per currency.
     * When a gateway marks an recognised issuer as activated, the pre existing activated recognised issuer is marked
     * as deactivated.
     * One must be cautious with activations and de-activations, as deactivating an issuer may render cash issued by that
     * issuer unspendable until re activation or wallets created by that issuer may not be able to perform transactions
     *
     * @param recognisedIssuerParty the party (which must have been added as a recognised issuer) to activate
     * @param currencyCode currency for which this [recognisedIssuerParty] is marked as it's issuer
     */
    @InitiatingFlow
    @StartableByRPC
    class Initiator(private val recognisedIssuerParty: Party, private val currencyCode: String) : FlowLogic<SignedTransaction>() {

        companion object {
            object CHECK_ISSUER : ProgressTracker.Step(
                "checking that the party to be activated has been added as a recognised issuer and hasn't been activated"
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

            if (inputPreExistingRecognisedIssuerPartyStateAndRef.state.data.activated) {
                throw IssuerPartyAlreadyActivatedException(recognisedIssuerParty)
            }
            val outputPreExistingRecognisedIssuerPartyState = inputPreExistingRecognisedIssuerPartyStateAndRef.state.data.activate()
            val outputPreExistingRecognisedIssuerPartyStateAndContract = StateAndContract(
                outputPreExistingRecognisedIssuerPartyState,
                RecognisedIssuerContract.CONTRACT_ID
            )
            val activateRecognisedIssuerCommand = Command(RecognisedIssuerContract.Activate(), listOf(ourIdentity.owningKey))

            progressTracker.currentStep = NOTARY_ID
            val notary = serviceHub.networkMapCache.notaryIdentities.first()

            val txBuilder = TransactionBuilder(notary = notary).withItems(
                inputPreExistingRecognisedIssuerPartyStateAndRef,
                outputPreExistingRecognisedIssuerPartyStateAndContract,
                activateRecognisedIssuerCommand
            )

            //fetch the already activated recognised issuer party
            //as there can be only one activated recognised issuer per currency, this activated issuer must be deactivated
            //it is possible there is not recognised issuer that has been activated, in such case the below query returns null
            //in such scenarios, there is not recognised issuer to deactivate
            //Hence, we need not consume any further states
            val inputActivatedRecognisedIssuerStateAndRef = getActivatedRecognisedIssuer(currencyCode, serviceHub)
            val outputActivatedRecognisedIssuerState = inputActivatedRecognisedIssuerStateAndRef?.state?.data?.deactivate()
            //only add this state to the tx builder if there was a recognised issuer that was activated
            if (outputActivatedRecognisedIssuerState != null) {
                txBuilder.addInputState(inputActivatedRecognisedIssuerStateAndRef)
                txBuilder.addOutputState(outputActivatedRecognisedIssuerState, RecognisedIssuerContract.CONTRACT_ID)
            }

            progressTracker.currentStep = TX_VERIFICATION
            txBuilder.verify(serviceHub)

            progressTracker.currentStep = TX_SIGNING
            val signedTransaction = serviceHub.signInitialTransaction(txBuilder)

            progressTracker.currentStep = FINALISING
            return subFlow(FinalityFlow(signedTransaction, listOf()))

        }

    }
}