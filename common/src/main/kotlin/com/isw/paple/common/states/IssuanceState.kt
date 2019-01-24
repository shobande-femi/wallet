package com.isw.paple.common.states

import com.isw.paple.common.contracts.IssuanceContract
import com.isw.paple.common.schemas.IssuanceStateSchemaV1
import com.isw.paple.common.types.IssuanceStatus
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

@BelongsToContract(IssuanceContract::class)
data class IssuanceState (
    val issuer: Party,
    val recipient: Party,
    val amount: Amount<Currency>,
    val status: IssuanceStatus,
    override val participants: List<AbstractParty>,
    override val linearId: UniqueIdentifier,
    val createdAt: Instant = Instant.now()
) : LinearState, QueryableState {

    constructor(
        issuer: Party,
        recipient: Party,
        amount: Amount<Currency>,
        status: IssuanceStatus
    ) : this(issuer, recipient, amount, status, setOf(issuer, recipient).toList(), UniqueIdentifier())


    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is IssuanceStateSchemaV1 -> IssuanceStateSchemaV1.PersistentIssuanceState(
                issuer = issuer.toString(),
                recipient = recipient.toString(),
                amount = amount.quantity.toString(),
                currency = amount.token.currencyCode,
                status = status.name,
                linearId = linearId.id.toString(),
                createdAt = createdAt.toEpochMilli()
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(IssuanceStateSchemaV1)

}