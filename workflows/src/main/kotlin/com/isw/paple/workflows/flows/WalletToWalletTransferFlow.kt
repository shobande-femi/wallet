package com.isw.paple.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.isw.paple.common.contracts.TransferReceiptContract
import com.isw.paple.common.contracts.WalletContract
import com.isw.paple.common.states.WalletState
import com.isw.paple.common.types.*
import com.isw.paple.common.utilities.getWalletStateByWalletId
import com.isw.paple.common.utilities.moveFunds
import net.corda.core.contracts.Command
import net.corda.core.contracts.InsufficientBalanceException
import net.corda.core.contracts.StateAndContract
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashException

object WalletToWalletTransferFlow {

    class NonExistentWalletException(walletId: String) : FlowException("Wallet with id: $walletId doesn't exist")
    class WeAreNotOwnerException: FlowException("If transfer is on us, we must be recipient")
    class UnspecifiedRecipient : FlowException("If transfer is not on us, a recipient party other than our self must be provided")

    @InitiatingFlow
    @StartableByRPC
    class Initiator(private val transfer: Transfer) : FlowLogic<SignedTransaction>() {

        companion object {
            // TODO: fine tune progress tracker
            object NOTARY_ID : ProgressTracker.Step("Getting Notary Identity")
            object TX_BUILDER : ProgressTracker.Step("Creating transaction builder, assigning notary, and gathering other components")

            object TX_VERIFICATION : ProgressTracker.Step("Verifying transaction")
            object SIGNING : ProgressTracker.Step("Signing tx")
            object FINALISING : ProgressTracker.Step("Finalising transaction") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                NOTARY_ID,
                TX_BUILDER,
                TX_VERIFICATION,
                SIGNING,
                FINALISING
            )
        }

        override val progressTracker: ProgressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            logger.info("Starting WalletToWalletTransferFlow.Initiator")

            logger.info("Getting sender wallet: ${transfer.senderWalletId}")
            val inputSenderWalletStateAndRef = getWalletStateByWalletId(walletId = transfer.senderWalletId, services = serviceHub) ?: throw NonExistentWalletException(transfer.senderWalletId)
            val inputSenderWalletState = inputSenderWalletStateAndRef.state.data

            return if (isOnUs(transfer.type)) {
                if (transfer.recipient != ourIdentity) throw WeAreNotOwnerException()
                onUsTransfer(inputSenderWalletState)
            } else {
                if (transfer.recipient == ourIdentity) throw UnspecifiedRecipient()
                notOnUsTransfer(inputSenderWalletState)
            }
        }

        private fun assembleTx(inputSenderWalletState: WalletState, inputRecipientWalletState: WalletState): TransactionBuilder {
            if (inputSenderWalletState.balance.token != inputRecipientWalletState.balance.token) {
                //TODO remove this when cross currency transfer is implemented
                throw FlowException("cross currency transfers not allowed yet")
            }
            //TODO: checks for embargo on wallet
            //TODO: check transfer limits, also check if wallet verified

            logger.info("Constructing tx")
            val transferCommand = Command(WalletContract.Transfer(), listOf(inputSenderWalletState.owner.owningKey))
            val (outputSenderWalletState, outputRecipientWalletState) = moveFunds(inputSenderWalletState, inputRecipientWalletState, transfer.amount)
            val outputSenderWalletStateAndContract = StateAndContract(outputSenderWalletState, WalletContract.CONTRACT_ID)
            val outputRecipientWalletStateAndContract = StateAndContract(outputRecipientWalletState, WalletContract.CONTRACT_ID)

            val createTransferReceiptCommand = Command(TransferReceiptContract.Create(), listOf(inputSenderWalletState.owner.owningKey))
            val outputTransferReceiptState = transfer.toState(inputSenderWalletState.owner, TransferStatus.UNKNOWN)
            val outputTransferReceiptStateAndContract = StateAndContract(outputTransferReceiptState, TransferReceiptContract.CONTRACT_ID)

            logger.info("Selecting notary identity")
            progressTracker.currentStep = NOTARY_ID
            val notary = serviceHub.networkMapCache.notaryIdentities.first()

            //TODO: update the lastUpdated field on wallet state
            progressTracker.currentStep = TX_BUILDER
            return TransactionBuilder(notary = notary)
                .withItems(
                    transferCommand,
                    outputSenderWalletStateAndContract,
                    outputRecipientWalletStateAndContract,

                    createTransferReceiptCommand,
                    outputTransferReceiptStateAndContract
                )
        }

        private fun onUsTransfer(inputSenderWalletState: WalletState): SignedTransaction {
            logger.info("Getting recipient wallet: ${transfer.recipientWalletId}")
            val inputRecipientWalletStateAndRef = getWalletStateByWalletId(walletId = transfer.recipientWalletId, services = serviceHub) ?: throw NonExistentWalletException(transfer.recipientWalletId)
            val inputRecipientWalletState = inputRecipientWalletStateAndRef.state.data

            val txBuilder = assembleTx(inputSenderWalletState, inputRecipientWalletState)

            logger.info("Verifying tx")
            progressTracker.currentStep = TX_VERIFICATION
            txBuilder.verify(serviceHub)

            logger.info("Signing tx")
            progressTracker.currentStep = SIGNING
            val signedTx = serviceHub.signInitialTransaction(txBuilder)

            logger.info("Finalising tx")
            progressTracker.currentStep = FINALISING
            return subFlow(FinalityFlow(transaction = signedTx, sessions = listOf()))
        }

        private fun notOnUsTransfer(inputSenderWalletState: WalletState) : SignedTransaction {
            //if we are the issuer, we definitely have a copy of the wallet
            //if not we must request the issuer gives us a copy of this state
            //should we assume there would always be a single issuing party?
            val recipientSession = initiateFlow(transfer.recipient)
            logger.info("Getting recipient wallet: ${transfer.recipientWalletId} from counter party")
            val inputRecipientWalletState = recipientSession.sendAndReceive<List<StateAndRef<WalletState>>>(transfer.recipientWalletId)
                .unwrap {
                    require(it.size == 1) {"only one recipient state needed"}
                    val walletState = it.single().state.data

                    //TODO: uncomment this when wallet verification logic is done
                    // require(walletState.verified) {"Wallet must be verified"}

                    require(walletState.owner == recipientSession.counterparty) {"Recipient must be owner of recipient wallet"}

                    //TODO: maybe include onlyFromParties logic?

                    walletState
            }

            val txBuilder = assembleTx(inputSenderWalletState, inputRecipientWalletState)

            val (_, keys) = try {
                Cash.generateSpend(
                    services = serviceHub,
                    tx = txBuilder,
                    amount = transfer.amount,
                    to = transfer.recipient,
                    ourIdentity = ourIdentityAndCert)
                //TODO include the onlyFromParties, to ensure gateways only accept cash from recognised issuers
            } catch (e: InsufficientBalanceException) {
                throw CashException("Insufficient cash for spend: ${e.message}", e)
            }

            logger.info("Verifying tx")
            progressTracker.currentStep = TX_VERIFICATION
            txBuilder.verify(serviceHub)

            logger.info("Signing tx")
            progressTracker.currentStep = SIGNING
            val signedTx = serviceHub.signInitialTransaction(txBuilder, keys)

            logger.info("Finalising tx")
            progressTracker.currentStep = FINALISING
            return subFlow(FinalityFlow(transaction = signedTx, sessions = listOf(recipientSession)))
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(private val senderSession: FlowSession) : FlowLogic<SignedTransaction>() {

        companion object {
            object RECEIVING : ProgressTracker.Step("Receiving recipient wallet id")
            object RECEIVE_FINALISED : ProgressTracker.Step("Receiving finalised transaction from gateway") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(RECEIVING, RECEIVE_FINALISED)
        }

        override val progressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            logger.info("Starting WalletToWalletTransferFlow.Responder")
            progressTracker.currentStep = RECEIVING
            val inputRecipientWalletStateAndRef = senderSession.receive<String>().unwrap {
                //TODO: maybe perform some checks, like sender is recognised issuer?
                getWalletStateByWalletId(walletId = it, services = serviceHub) ?: throw NonExistentWalletException(it)
            }
            SendStateAndRefFlow(senderSession, listOf(inputRecipientWalletStateAndRef))

            logger.info("Receiving finalised tx")
            progressTracker.currentStep = RECEIVE_FINALISED
            return subFlow(ReceiveFinalityFlow(senderSession))
        }

    }
}