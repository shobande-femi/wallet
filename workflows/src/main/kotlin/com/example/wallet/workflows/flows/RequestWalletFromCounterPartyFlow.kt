package com.example.wallet.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.example.wallet.common.states.WalletState
import com.example.wallet.common.utilities.getWalletStateByWalletId
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

/**
 * Flow pair for requesting for a wallet state from a counter party
 * This is a makeshift implementation, and would likely become deprecated at some point as it poses some privacy concerns
 * Parties shouldn't be required to submit their wallet states to any party but a regulator or issuer
 *
 * In the meanwhile, this flow pair is used for not on us transfers i.e transfers to wallets that we do not have a copy of.
 * While building the transaction, we request for the recipient's wallet, so we correctly build the transaction
 */
object RequestWalletFromCounterPartyFlow {
    class NonExistentWalletException(walletId: String) : FlowException("Wallet with id: $walletId doesn't exist")

    /**
     * @param counterParty the to request wallet information from
     * @param walletId id of the wallet for which we expect the [counterParty] to return
     */
    @InitiatingFlow
    @StartableByRPC
    class Initiator(private val counterParty: Party, private val walletId: String) : FlowLogic<StateAndRef<WalletState>>() {

        @Suspendable
        override fun call(): StateAndRef<WalletState> {
            val recipientSession = initiateFlow(counterParty)
            logger.info("Getting recipient wallet: $walletId from counter party")

            recipientSession.send(walletId)
            val recipientWalletStatesAndRefs = subFlow(ReceiveStateAndRefFlow<WalletState>(recipientSession))
            require(recipientWalletStatesAndRefs.size == 1) {"only one recipient wallet needed"}

            val recipientWalletStateAndRef = recipientWalletStatesAndRefs.single()
            val recipientWalletState = recipientWalletStateAndRef.state.data
            require(recipientWalletState.owner == recipientSession.counterparty) {"Recipient session must own recipient wallet"}

            return recipientWalletStateAndRef
        }

    }

    /**
     * @param senderSession initiator of this session i.e the party requesting for wallet information
     */
    @InitiatedBy(Initiator::class)
    class Responder(private val senderSession: FlowSession) : FlowLogic<Unit>(){
        companion object {
            object RECEIVING : ProgressTracker.Step("Receiving recipient wallet id")

            fun tracker() = ProgressTracker(RECEIVING)
        }

        override val progressTracker = tracker()

        @Suspendable
        override fun call() {
            logger.info("Starting WalletToWalletTransferFlow.Responder")
            progressTracker.currentStep = RECEIVING
            val inputRecipientWalletStateAndRef = senderSession.receive<String>().unwrap {
                //TODO: maybe perform some checks, like sender is recognised issuer?
                getWalletStateByWalletId(walletId = it, services = serviceHub) ?: throw NonExistentWalletException(it)
            }
            logger.info("Fetched recipient wallet: ${inputRecipientWalletStateAndRef.state.data}")
            subFlow(SendStateAndRefFlow(senderSession, listOf(inputRecipientWalletStateAndRef)))
        }
    }
}