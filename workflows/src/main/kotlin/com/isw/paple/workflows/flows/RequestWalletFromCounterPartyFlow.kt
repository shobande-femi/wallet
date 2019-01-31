package com.isw.paple.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.isw.paple.common.states.WalletState
import com.isw.paple.common.utilities.getWalletStateByWalletId
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

object RequestWalletFromCounterPartyFlow {
    class NonExistentWalletException(walletId: String) : FlowException("Wallet with id: $walletId doesn't exist")

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
            //TODO: uncomment this when wallet verification logic is done
            //require(inputRecipientWalletState.verified) {"Wallet must be verified"}
            require(recipientWalletState.owner == recipientSession.counterparty) {"Recipient session must own recipient wallet"}
            //TODO: maybe include onlyFromParties logic?

            return recipientWalletStateAndRef
        }

    }

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