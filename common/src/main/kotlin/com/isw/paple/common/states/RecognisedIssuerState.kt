package com.isw.paple.common.states

import com.isw.paple.common.contracts.RecognisedIssuerContract
import com.isw.paple.common.schemas.RecognisedIssuerStateSchemaV1
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import java.time.Instant

@BelongsToContract(RecognisedIssuerContract::class)
data class RecognisedIssuerState (
    val issuer: Party,
    val addedBy: Party,
    val currencyCode: String,
    val activated: Boolean,
    override val participants: List<AbstractParty>,
    override val linearId: UniqueIdentifier,
    val addedAt: Instant = Instant.now(),
    val lastUpdated: Instant = Instant.now()
) : LinearState, QueryableState {

    constructor(
        issuer: Party,
        addedBy: Party,
        currencyCode: String,
        activated: Boolean
    ) : this(issuer, addedBy, currencyCode, activated, listOf(addedBy), UniqueIdentifier(issuer.toString()))

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is RecognisedIssuerStateSchemaV1 -> RecognisedIssuerStateSchemaV1.PersistentRecognisedIssuerState(
                issuer = issuer.name.toString(),
                addedBy = addedBy.name.toString(),
                currencyCode = currencyCode,
                activated = activated,
                linearId = linearId.id.toString(),
                addedAt = addedAt.toEpochMilli(),
                lastUpdated = lastUpdated.toEpochMilli()
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(RecognisedIssuerStateSchemaV1)

    fun activate() : RecognisedIssuerState{
        return copy(
            issuer = issuer,
            addedBy = addedBy,
            currencyCode = currencyCode,
            activated = true,
            participants = participants,
            linearId = linearId,
            addedAt = addedAt,
            lastUpdated = lastUpdated
        )
    }

    fun deactivate() : RecognisedIssuerState{
        return copy(
            issuer = issuer,
            addedBy = addedBy,
            currencyCode = currencyCode,
            activated = false,
            participants = participants,
            linearId = linearId,
            addedAt = addedAt,
            lastUpdated = lastUpdated
        )
    }
}