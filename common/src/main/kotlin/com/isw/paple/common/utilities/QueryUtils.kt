package com.isw.paple.common.utilities

import com.isw.paple.common.schemas.RecognisedIssuerStateSchemaV1
import com.isw.paple.common.schemas.WalletStateSchemaV1
import com.isw.paple.common.states.RecognisedIssuerState
import com.isw.paple.common.states.WalletState
import com.isw.paple.common.types.WalletType
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

fun getRecognisedIssuerStateByIssuerName(issuer: String, services: ServiceHub): StateAndRef<RecognisedIssuerState>? {
    val states = getState<RecognisedIssuerState>(services) { generalCriteria ->
        val additionalCriteria = QueryCriteria.VaultCustomQueryCriteria(RecognisedIssuerStateSchemaV1.PersistentRecognisedIssuerState::issuer.equal(issuer))
        generalCriteria.and(additionalCriteria)
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