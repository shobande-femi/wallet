package com.isw.paple.issuer.flows

import co.paralleluniverse.fibers.Suspendable
import com.isw.paple.common.contracts.WalletContract
import com.isw.paple.common.types.Wallet
import com.isw.paple.common.types.toState
import com.isw.paple.common.utilities.getWalletStateByWalletId
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

/**
 * Adds a new [Wallet] state to the ledger.
 * The state is always added as a "uni-lateral state" to the node calling this flow.
 */
@StartableByRPC
class CreateGatewayWallet(val wallet: Wallet) : FlowLogic<SignedTransaction>() {

    companion object {
        // TODO: fine tune progress tracker
        object NOTARY_ID : ProgressTracker.Step("Getting Notary Identity")
        object TX_BUILDER : ProgressTracker.Step("Creating transaction builder and assigning notary")
        object OTHER_TX_COMPONENTS : ProgressTracker.Step("Gathering transaction's other components")

        object TX_VERIFICATION : ProgressTracker.Step("Verifying transaction")
        object TX_SIGNING : ProgressTracker.Step("Signing a transaction")
        object FINALISING : ProgressTracker.Step("Finalising transaction") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(NOTARY_ID, TX_BUILDER, OTHER_TX_COMPONENTS, TX_VERIFICATION, TX_SIGNING, FINALISING)
    }

    override val progressTracker: ProgressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {
        logger.info("Initiating CreateGatewayWallet flow")
        val walletId = wallet.walletId

        logger.info("Checking for existence of state for $wallet.")
        val result = getWalletStateByWalletId(walletId, serviceHub)

        if (result != null) {
            val linearId = result.state.data.linearId
            throw IllegalArgumentException("Wallet $walletId already exists with linearId ($linearId).")
        }

        logger.info("No state for $wallet. Adding it.")
        val walletState = wallet.toState(ourIdentity)
        val notary = serviceHub.networkMapCache.notaryIdentities.first()

        // The node running this flow is always the only signer.
        val command = Command(WalletContract.Add(), listOf(ourIdentity.owningKey))
        val outputStateAndContract = StateAndContract(walletState, WalletContract.CONTRACT_ID)
        val unsignedTransaction = TransactionBuilder(notary = notary).withItems(command, outputStateAndContract)

        val signedTransaction = serviceHub.signInitialTransaction(unsignedTransaction)

        progressTracker.currentStep = FINALISING
        return subFlow(FinalityFlow(signedTransaction))

    }

}