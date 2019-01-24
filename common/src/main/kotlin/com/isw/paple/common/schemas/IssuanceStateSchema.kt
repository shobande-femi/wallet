package com.isw.paple.common.schemas

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

object IssuanceStateSchema

object IssuanceStateSchemaV1 : MappedSchema(
    schemaFamily = IssuanceStateSchema.javaClass,
    version = 1,
    mappedTypes = listOf(PersistentIssuanceState::class.java)
) {

    @Entity
    @Table(name = "issuance_states")
    class PersistentIssuanceState(
        @Column(name = "issuer")
        var issuer: String,
        @Column(name = "recipient")
        var recipient: String,
        @Column(name = "amount")
        var amount: String,
        @Column(name = "currency")
        var currency: String,
        @Column(name = "status")
        var status: String,
        @Column(name = "linear_id")
        var linearId: String,
        @Column(name = "created_at")
        var createdAt: Long
    ) : PersistentState() {
        @Suppress("UNUSED")
        constructor() : this(
            issuer = "",
            recipient = "",
            amount = "",
            currency = "",
            status = "",
            linearId = "",
            createdAt = 0L
        )
    }
}