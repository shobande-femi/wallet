package com.isw.paple.gateway.flows

import co.paralleluniverse.fibers.Suspendable
import com.isw.paple.common.contracts.WalletContract
import com.isw.paple.common.types.Wallet
import com.isw.paple.common.types.WalletType
import com.isw.paple.common.types.toState
import com.isw.paple.common.utilities.getRecognisedIssuerStateByIssuerName
import com.isw.paple.issuer.flows.CreateGatewayWallet
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

@InitiatedBy(CreateGatewayWallet::class)
class CreateGatewayWalletResponder(private val issuerSession: FlowSession) : FlowLogic<SignedTransaction>() {

    companion object {
        // TODO: fine tune progress tracker
        object RECEIVING : ProgressTracker.Step("Waiting for wallet info")
        object VERIFYING : ProgressTracker.Step("Verifying wallet info")
        object NOTARY_ID : ProgressTracker.Step("Getting Notary Identity")
        object SIGNING : ProgressTracker.Step("Generating and signing transaction proposal")
        object COLLECTING_SIGNATURES : ProgressTracker.Step("Collecting signatures from other parties") {
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }
        object FINALISING : ProgressTracker.Step("Finalising transaction") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(RECEIVING, VERIFYING, NOTARY_ID, SIGNING, COLLECTING_SIGNATURES, FINALISING)
    }

    override val progressTracker: ProgressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {
        //there is a prerecorded list of issuers that this gateway recognises
        //we need to check that the party initiating this session is a recognised issuer
        getRecognisedIssuerStateByIssuerName(issuerSession.counterparty.toString(), serviceHub) ?: throw IllegalStateException("Flow must be run by an recognised issuer node.")

        // Wait for a wallet creation request to come in from the other party.
        progressTracker.currentStep = RECEIVING
        val wallet = receiveAndValidateWalletCreationRequest()

        // Put together a proposed transaction that performs wallet creation.
        progressTracker.currentStep = SIGNING
        val unsignedTransaction = assembleSharedTransaction(wallet, issuerSession.counterparty)
        val partiallySignedTransaction = serviceHub.signInitialTransaction(unsignedTransaction.tx)

        progressTracker.currentStep = COLLECTING_SIGNATURES
        val signedTransaction = subFlow(CollectSignaturesFlow(partiallySignedTransaction, listOf(issuerSession)))

        progressTracker.currentStep = FINALISING
        return subFlow(FinalityFlow(transaction = signedTransaction, sessions = listOf(issuerSession)))
    }

    @Suspendable
    private fun receiveAndValidateWalletCreationRequest(): Wallet {
        return issuerSession.receive<Wallet>().unwrap {
            progressTracker.currentStep = VERIFYING
            require(it.owner == ourIdentity) {"We must own this wallet before signing it's creation"}
            require(it.amount.quantity == 0L) {"Wallet funding and creation cannot occur simultaneously"}
            require(it.type == WalletType.GATEWAY_OWNED) {"Wallet type must be gateway owned"}
            it
        }
    }

    @Suspendable
    private fun assembleSharedTransaction(wallet: Wallet, issuerParty: Party): SharedTransaction {
        progressTracker.currentStep = NOTARY_ID
        val notary = serviceHub.networkMapCache.notaryIdentities.first()

        val walletState = wallet.toState(createdBy = issuerParty)

        val command = Command(WalletContract.Create(), listOf(ourIdentity.owningKey, issuerParty.owningKey))
        val outputStateAndContract = StateAndContract(walletState, WalletContract.CONTRACT_ID)
        val unsignedTransaction = TransactionBuilder(notary = notary).withItems(command, outputStateAndContract)

        return SharedTransaction(unsignedTransaction)
    }

    data class SharedTransaction(val tx: TransactionBuilder)
}