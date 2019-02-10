package com.example.wallet.common.utilities

import com.example.wallet.common.schemas.RecognisedIssuerStateSchemaV1
import com.example.wallet.common.schemas.WalletStateSchemaV1
import com.example.wallet.common.states.RecognisedIssuerState
import com.example.wallet.common.states.WalletState
import com.example.wallet.common.types.WalletType
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.Builder.equal
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder


fun getWalletStateByWalletId(walletId: String, services: ServiceHub): StateAndRef<WalletState>? {
    val states = getState<WalletState>(services) { generalCriteria ->
        val walletIdCriteria = QueryCriteria.VaultCustomQueryCriteria(WalletStateSchemaV1.PersistentWalletState::walletId.equal(walletId))

        generalCriteria.and(walletIdCriteria)
    }
    return states.singleOrNull()
}

fun getWalletStateByWalletIdAndWalletType(walletId: String, type: WalletType, services: ServiceHub) : StateAndRef<WalletState>? {
    val states = getState<WalletState>(services) { generalCriteria ->
        val walletIdCriteria = QueryCriteria.VaultCustomQueryCriteria(WalletStateSchemaV1.PersistentWalletState::walletId.equal(walletId))
        val typeCriteria = QueryCriteria.VaultCustomQueryCriteria(WalletStateSchemaV1.PersistentWalletState::type.equal(type.name))

        generalCriteria.and(walletIdCriteria).and(typeCriteria)
    }
    return states.singleOrNull()
}

fun getRecognisedIssuer(issuer: String, currencyCode: String, services: ServiceHub): StateAndRef<RecognisedIssuerState>? {
    val states = getState<RecognisedIssuerState>(services) { generalCriteria ->
        val issuerNameCriteria = QueryCriteria.VaultCustomQueryCriteria(RecognisedIssuerStateSchemaV1.PersistentRecognisedIssuerState::issuer.equal(issuer))
        val currencyCodeCriteria = QueryCriteria.VaultCustomQueryCriteria(RecognisedIssuerStateSchemaV1.PersistentRecognisedIssuerState::currencyCode.equal(currencyCode))
        generalCriteria.and(issuerNameCriteria).and(currencyCodeCriteria)
    }
    return states.singleOrNull()
}

fun getActivatedRecognisedIssuer(currencyCode: String, services: ServiceHub): StateAndRef<RecognisedIssuerState>? {
    val states = getState<RecognisedIssuerState>(services) { generalCriteria ->
        val currencyCodeCriteria = QueryCriteria.VaultCustomQueryCriteria(RecognisedIssuerStateSchemaV1.PersistentRecognisedIssuerState::currencyCode.equal(currencyCode))
        val activatedCriteria = QueryCriteria.VaultCustomQueryCriteria(RecognisedIssuerStateSchemaV1.PersistentRecognisedIssuerState::activated.equal(true))
        generalCriteria.and(currencyCodeCriteria).and(activatedCriteria)
    }
    return states.singleOrNull()
}

fun getActivatedRecognisedIssuerByIssuerName(currencyCode: String, issuer: String, services: ServiceHub): StateAndRef<RecognisedIssuerState>? {
    val states = getState<RecognisedIssuerState>(services) { generalCriteria ->
        val currencyCodeCriteria = QueryCriteria.VaultCustomQueryCriteria(RecognisedIssuerStateSchemaV1.PersistentRecognisedIssuerState::currencyCode.equal(currencyCode))
        val activatedCriteria = QueryCriteria.VaultCustomQueryCriteria(RecognisedIssuerStateSchemaV1.PersistentRecognisedIssuerState::activated.equal(true))
        val issuerNameCriteria = QueryCriteria.VaultCustomQueryCriteria(RecognisedIssuerStateSchemaV1.PersistentRecognisedIssuerState::issuer.equal(issuer))

        generalCriteria.and(currencyCodeCriteria).and(activatedCriteria).and(issuerNameCriteria)
    }
    return states.singleOrNull()
}

private inline fun <reified U : ContractState> getState(
    services: ServiceHub,
    block: (generalCriteria: QueryCriteria.VaultQueryCriteria) -> QueryCriteria
): List<StateAndRef<U>> {
    val query = builder {
        val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
        block(generalCriteria)
    }
    val result = services.vaultService.queryBy<U>(query)
    return result.states
}