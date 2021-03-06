package com.example.wallet.common.states

import com.example.wallet.common.contracts.WalletContract
import com.example.wallet.common.schemas.WalletStateSchemaV1
import com.example.wallet.common.types.WalletStatus
import com.example.wallet.common.types.WalletType
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
    val issuedBy: Party,
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
        issuedBy: Party,
        balance: Amount<Currency>,
        status: WalletStatus,
        type: WalletType
    ) : this(owner, issuedBy, balance, status, type, false, setOf(owner, issuedBy).toList(), UniqueIdentifier(walletId))

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is WalletStateSchemaV1 -> WalletStateSchemaV1.PersistentWalletState(
                owner = owner.name.toString(),
                issuedBy = issuedBy.name.toString(),
                balance = balance.quantity.toString(),
                currency = balance.token.currencyCode,
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

    fun withNewBalance(balance: Amount<Currency>): WalletState {
        return copy(
            owner = owner,
            issuedBy = issuedBy,
            balance = balance,
            status = status,
            type = type,
            verified = verified,
            participants = participants,
            linearId = linearId,
            createdAt = createdAt,
            lastUpdated = lastUpdated
        )
    }

    fun verifyWallet() : WalletState {
        return copy(
            owner = owner,
            issuedBy = issuedBy,
            balance = balance,
            status = status,
            type = type,
            verified = true,
            participants = participants,
            linearId = linearId,
            createdAt = createdAt,
            lastUpdated = lastUpdated
        )
    }
}