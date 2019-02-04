package com.isw.paple.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.isw.paple.common.contracts.RecognisedIssuerContract
import com.isw.paple.common.states.RecognisedIssuerState
import com.isw.paple.common.utilities.getActivatedRecognisedIssuer
import com.isw.paple.common.utilities.getRecognisedIssuer
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

object AddRecognisedIssuerFlow {
    /**
     * A gateway must be cautious to not accept cash issuance from a non legal issuer
     * Hence, a gateway must explicitly add recognised issuer to it's vault, then and only then can the issuer
     * issuer cash to the gateway.
     * If this wasn't set up, any party could simply print money for free. Send it to a gateway, for spending.

     * Only one issuer is allowed per currency.
     * The gateway may add multiple issuers of the same currency, however only one issuing party can be activated per
     * currency.
     * One must be careful about adding multiple issuers, besides the risk of accepting cash issuance from non legal
     * issuers, wallets created by the deactivated issuer may not be able to partake in any transaction
     */

    class AddIssuerTwiceException(recognisedIssuerState: RecognisedIssuerState) : FlowException(
        "Issuer $recognisedIssuerState already exists and is activated"
    )
    class IssuerExistsButNotActivatedException(recognisedIssuerState: RecognisedIssuerState) : FlowException(
        "Issuer $recognisedIssuerState already exists, but is not activated, to activate start ActivateRecognisedIssuerFlow"
    )
    class ActivatedIssuerExistsException(activatedIssuer: RecognisedIssuerState) : FlowException(
        "An activated issuer $activatedIssuer already exists"
    )
    class PartyNotInNetworkMapException(proposedIssuerParty: Party) : FlowException(
        "Proposed Issuer Party $proposedIssuerParty cannot be found in network map cache"
    )

    /**
     * @param proposedIssuerParty the party to add as a recognised issuer
     * @param currencyCode currency for which this [proposedIssuerParty] is marked as it's issuer
     */
    @InitiatingFlow
    @StartableByRPC
    class Initiator(private val proposedIssuerParty: Party, private val currencyCode: String) : FlowLogic<SignedTransaction>() {

        companion object {
            // TODO: fine tune progress tracker
            object CHECK_ISSUER : ProgressTracker.Step("Checking if issuer already exists")
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

            //check that no activated issuer exists for the specified currency
            val activatedRecognisedIssuerStateAndRef = getActivatedRecognisedIssuer(currencyCode, serviceHub)
            if (activatedRecognisedIssuerStateAndRef != null) {
                throw ActivatedIssuerExistsException(activatedRecognisedIssuerStateAndRef.state.data)
            }

            //check that the proposed issuer doesn't exist in the vault
            //if the proposed issuer exists, we take a step further to check it has been activated
            //if it has, throw AddIssuerTwiceException
            //on the other hand, the activated field may be zero, in this case, notify the caller to start the
            //ActivateRecognisedIssuerFlow
            val preExistingRecognisedIssuerStateAndRef = getRecognisedIssuer(proposedIssuerParty.toString(), currencyCode, serviceHub)
            if (preExistingRecognisedIssuerStateAndRef != null) {
                if (preExistingRecognisedIssuerStateAndRef.state.data.activated) {
                    throw AddIssuerTwiceException(preExistingRecognisedIssuerStateAndRef.state.data)
                }
                else {
                    throw IssuerExistsButNotActivatedException(preExistingRecognisedIssuerStateAndRef.state.data)
                }
            }

            //Check that the proposed issuer party to be added is one whose info has been cached in the network map
            val issuerPartyInfo = serviceHub.networkMapCache.getPartyInfo(proposedIssuerParty)
            val issuerParty = issuerPartyInfo?.party ?: throw PartyNotInNetworkMapException(proposedIssuerParty)

            val recognisedIssuerState = RecognisedIssuerState(issuerParty, ourIdentity, currencyCode, true)

            progressTracker.currentStep = NOTARY_ID
            val notary = serviceHub.networkMapCache.notaryIdentities.first()

            // The node running this flow is always the only signer.
            val addRecognisedIssuerCommand = Command(RecognisedIssuerContract.Add(), listOf(ourIdentity.owningKey))
            val outputRecognisedIssuerStateAndContract = StateAndContract(recognisedIssuerState, RecognisedIssuerContract.CONTRACT_ID)
            progressTracker.currentStep = TX_BUILDER
            val txBuilder = TransactionBuilder(notary = notary).withItems(addRecognisedIssuerCommand, outputRecognisedIssuerStateAndContract)

            progressTracker.currentStep = TX_VERIFICATION
            txBuilder.verify(serviceHub)

            progressTracker.currentStep = TX_SIGNING
            val signedTransaction = serviceHub.signInitialTransaction(txBuilder)

            progressTracker.currentStep = FINALISING
            return subFlow(FinalityFlow(signedTransaction, listOf()))

        }

    }

}