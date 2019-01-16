package com.isw.paple.common.schemas

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

object RecognisedIssuerStateSchema

object RecognisedIssuerStateSchemaV1 : MappedSchema(
    schemaFamily = RecognisedIssuerStateSchema.javaClass,
    version = 1,
    mappedTypes = listOf(PersistentRecognisedIssuerState::class.java)
) {

    @Entity
    @Table(name = "wallet_states")
    class PersistentRecognisedIssuerState(
        @Column(name = "issuer")
        var issuer: String,
        @Column(name = "added_by")
        var addedBy: String,
        @Column(name = "linear_id")
        var linearId: String,
        @Column(name = "added_at")
        var addedAt: Long,
        @Column(name = "last_updated")
        var lastUpdated: Long
    ) : PersistentState() {
        @Suppress("UNUSED")
        constructor() : this(
            issuer = "",
            addedBy = "",
            linearId = "",
            addedAt = 0L,
            lastUpdated = 0L
        )
    }

}