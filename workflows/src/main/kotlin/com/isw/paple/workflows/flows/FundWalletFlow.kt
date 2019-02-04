package com.isw.paple.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.isw.paple.common.types.Transfer
import com.isw.paple.common.types.TransferType
import com.isw.paple.common.types.WalletType
import com.isw.paple.common.utilities.getActivatedRecognisedIssuer
import net.corda.core.contracts.Amount
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import java.util.*

/**
 * This funding is different from [IssueFundsToWalletFlow] in that, the former requires creation of money by a recognised
 * activated issuer. Also, only gateways can receive such issuance. An issuer cannot issue money directly to any wallet
 * kind other than a gateway owned wallet.
 *
 * Every other wallet kind, must be funded via a gateway. Under the hood, this is a simple transfer from a gateway owned
 * wallet to the recipient wallet. In fact, Issuer owned wallets must be funded via gateway wallets.
 */
object FundWalletFlow {
    class NoActivatedRecognisedIssuerException : FlowException("Cannot find an activated recognised issuer in vault")
    class NotAllowedRecipientWalletTypeException : FlowException("The specified wallet type cannot be funded")

    /**
     * @param senderWalletId id of the wallet to be debited
     * @param recipientWalletId id of the wallet to funded
     * @param recipientWalletType type of the wallet to be funded
     * @param amount amount to fund the recipient wallet
     */
    @InitiatingFlow
    @StartableByRPC
    class Initiator(
        private val senderWalletId: String,
        private val recipientWalletId: String,
        private val recipientWalletType: WalletType,
        private val amount: Amount<Currency>
    ) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {

            val activatedRecognisedIssuerStateAndRef = getActivatedRecognisedIssuer(
                amount.token.currencyCode,
                serviceHub
            ) ?: throw NoActivatedRecognisedIssuerException()

            val transferType = when (recipientWalletType) {
                WalletType.ISSUER_OWNED -> TransferType.GATEWAY_TO_ISSUER
                WalletType.LIQUIDITY_PROVIDER_OWNED -> TransferType.GATEWAY_TO_LIQUIDITY_PROVIDER
                WalletType.REGULAR_USER_OWNED -> TransferType.GATEWAY_TO_REGULAR_USER
                else -> throw NotAllowedRecipientWalletTypeException()
            }

            val transfer = Transfer(
                senderWalletId = senderWalletId,
                recipientWalletId = recipientWalletId,
                recipient = activatedRecognisedIssuerStateAndRef.state.data.issuer,
                amount = amount,
                type = transferType
            )

            //TODO: logic to collect charges maybe? Charges to be paid to issuer and gateway?
            return subFlow(WalletToWalletTransferFlow.Initiator(transfer))
        }

    }
}