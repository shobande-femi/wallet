package com.example.wallet.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.example.wallet.common.contracts.TransferReceiptContract
import com.example.wallet.common.contracts.WalletContract
import com.example.wallet.common.states.WalletState
import com.example.wallet.common.types.Transfer
import com.example.wallet.common.types.TransferStatus
import com.example.wallet.common.types.isOnUs
import com.example.wallet.common.types.toState
import com.example.wallet.common.utilities.getActivatedRecognisedIssuer
import com.example.wallet.common.utilities.getWalletStateByWalletId
import com.example.wallet.common.utilities.moveFunds
import net.corda.core.contracts.Command
import net.corda.core.contracts.InsufficientBalanceException
import net.corda.core.contracts.StateAndContract
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashException

/** A generic flow pair for transfers between wallets of all kinds
 */
object WalletToWalletTransferFlow {

    class NonExistentWalletException(walletId: String) : FlowException("Wallet with id: $walletId doesn't exist")
    class WeAreNotOwnerException: FlowException("If transfer is on us, we must be recipient")
    class UnspecifiedRecipient : FlowException("If transfer is not on us, a recipient party other than our self must be provided")

    /**
     * Transfers between wallets can be thought of in 2 forms
     * 1. On Us Transfers: The initiating party owns the sender and recipient wallets. All wallet kinds except ones owned
     * by gateways are really owned by the Issuer. Hence, transfers between these wallet kinds do not involve a change
     * in ownership of cash states. However, Sender and recipient wallets are consumed to reflect the transfer.
     * When it is an on us transfer, the recipient must be our identity.
     * 2. Not on Us transfers: these transfers occur when the owners of the sender and recipient wallets are different
     * parties. The sender party may not have a view of the recipient wallet, and must initiate a flow session with
     * the recipient party, requesting for the recipient wallet. On receipt of this wallet, the sender can correctly
     * construct the transaction.
     * When it is a not on us transfer, our identity must not be the recipient party, for obvious reasons.
     * //TODO this approach is not suitable as it poses privacy challenges, it will be changed sometime in future
     *
     * The assembled transaction consumed the sender and recipient states, adding new sender and recipient states (with debit and credit done)
     * as outputs.
     * An Transaction receipt state is also generated, for ease of tracking transaction history
     * In the cash of an on us transfer, cash states need not change ownership
     * On the other hand, we generate a spend, sending some cash (as specified in the transfer amount) to the recipient
     *
     * @param transfer information of the transfer to be performed
     */
    @InitiatingFlow
    @StartableByRPC
    class Initiator(private val transfer: Transfer) : FlowLogic<SignedTransaction>() {

        class NoActivatedRecognisedIssuerException : FlowException("Cannot find an activated recognised issuer in vault")

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

            //decide if this is an onUs or a notOnUs transfer
            return if (isOnUs(transfer.type)) {
                if (transfer.recipient != ourIdentity) throw WeAreNotOwnerException()
                onUsTransfer(inputSenderWalletStateAndRef)
            } else {
                if (transfer.recipient == ourIdentity) throw UnspecifiedRecipient()
                notOnUsTransfer(inputSenderWalletStateAndRef)
            }
        }

        @Suspendable
        private fun assembleTx(inputSenderWalletStateAndRef: StateAndRef<WalletState>, inputRecipientWalletStateAndRef: StateAndRef<WalletState>): TransactionBuilder {
            val inputSenderWalletState = inputSenderWalletStateAndRef.state.data
            val inputRecipientWalletState = inputRecipientWalletStateAndRef.state.data

            if (inputSenderWalletState.balance.token != inputRecipientWalletState.balance.token) {
                //TODO remove this when cross currency transfer is implemented
                throw FlowException("cross currency transfers not allowed yet")
            }
            //TODO: checks for embargo on wallet
            //TODO: check transfer limits

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
                    inputSenderWalletStateAndRef,
                    inputRecipientWalletStateAndRef,
                    outputSenderWalletStateAndContract,
                    outputRecipientWalletStateAndContract,

                    createTransferReceiptCommand,
                    outputTransferReceiptStateAndContract
                )
        }

        @Suspendable
        private fun onUsTransfer(inputSenderWalletStateAndRef: StateAndRef<WalletState>): SignedTransaction {
            logger.info("Getting recipient wallet: ${transfer.recipientWalletId}")
            val inputRecipientWalletStateAndRef = getWalletStateByWalletId(walletId = transfer.recipientWalletId, services = serviceHub) ?: throw NonExistentWalletException(transfer.recipientWalletId)

            val txBuilder = assembleTx(inputSenderWalletStateAndRef, inputRecipientWalletStateAndRef)

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

        @Suspendable
        private fun notOnUsTransfer(inputSenderWalletStateAndRef: StateAndRef<WalletState>) : SignedTransaction {
            //if we are the issuer, we definitely have a copy of the wallet
            //if not we must request the issuer gives us a copy of this state
            //should we assume there would always be a single issuing party?

            val inputSenderWalletState = inputSenderWalletStateAndRef.state.data
            val inputRecipientWalletStateAndRef = subFlow(RequestWalletFromCounterPartyFlow.Initiator(transfer.recipient, transfer.recipientWalletId))
            val inputRecipientWalletState = inputRecipientWalletStateAndRef.state.data

            val txBuilder = assembleTx(inputSenderWalletStateAndRef, inputRecipientWalletStateAndRef)

            val activatedRecognisedIssuerStateAndRef = getActivatedRecognisedIssuer(
                transfer.amount.token.currencyCode,
                serviceHub
            ) ?: throw NoActivatedRecognisedIssuerException()

            val (_, keys) = try {
                Cash.generateSpend(
                    services = serviceHub,
                    tx = txBuilder,
                    amount = transfer.amount,
                    to = transfer.recipient,
                    ourIdentity = ourIdentityAndCert,
                    onlyFromParties = setOf(activatedRecognisedIssuerStateAndRef.state.data.issuer)
                )
            } catch (e: InsufficientBalanceException) {
                throw CashException("Insufficient cash for spend: ${e.message}", e)
            }

            logger.info("Verifying tx")
            progressTracker.currentStep = TX_VERIFICATION
            txBuilder.verify(serviceHub)

            logger.info("Signing tx")
            progressTracker.currentStep = SIGNING
            val signedTx = serviceHub.signInitialTransaction(txBuilder, keys)

            val participantSessions = (inputSenderWalletState.participants+inputRecipientWalletState.participants)
                .toSet()
                .filter { it != ourIdentity }
                .map {
                    initiateFlow(it as Party)
                }

            logger.info("Finalising tx")
            progressTracker.currentStep = FINALISING
            return subFlow(FinalityFlow(transaction = signedTx, sessions = participantSessions))
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

            logger.info("Receiving finalised tx")
            progressTracker.currentStep = RECEIVE_FINALISED
            return subFlow(ReceiveFinalityFlow(senderSession))
        }

    }
}