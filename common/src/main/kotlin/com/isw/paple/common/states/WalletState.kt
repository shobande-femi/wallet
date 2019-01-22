package com.isw.paple.common.states

import com.isw.paple.common.contracts.WalletContract
import com.isw.paple.common.schemas.WalletStateSchemaV1
import com.isw.paple.common.types.WalletStatus
import com.isw.paple.common.types.WalletType
import net.corda.core.contracts.Amount
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import java.time.Instant
import java.util.*

@BelongsToContract(WalletContract::class)
data class WalletState (
    val owner: Party,
    val createdBy: Party,
    val balance: Amount<Currency>,
    val status: WalletStatus,
    val type: WalletType,
    val verified: Boolean,
    override val participants: List<AbstractParty>,
    override val linearId: UniqueIdentifier,
    val createdAt: Instant = Instant.now(),
    val lastUpdated: Instant = Instant.now()
) : LinearState, QueryableState {

    constructor(
        owner: Party,
        walletId: String,
        createdBy: Party,
        balance: Amount<Currency>,
        status: WalletStatus,
        type: WalletType
    ) : this(owner, createdBy, balance, status, type, false, listOf(owner, createdBy), UniqueIdentifier(walletId))

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is WalletStateSchemaV1 -> WalletStateSchemaV1.PersistentWalletState(
                owner = owner.name.toString(),
                createdBy = createdBy.name.toString(),
                balance = balance.quantity.toString(),
                currency = balance.token.toString(),
                status = status.name,
                type = type.name,
                verified = verified,
                linearId = linearId.id.toString(),
                walletId = linearId.externalId.toString(),
                createdAt = createdAt.toEpochMilli(),
                lastUpdated = lastUpdated.toEpochMilli()
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(WalletStateSchemaV1)
}