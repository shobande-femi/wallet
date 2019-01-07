package com.isw.paple.common.schemas

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

object WalletStateSchema

object WalletStateSchemaV1 : MappedSchema(
    schemaFamily = WalletStateSchema.javaClass,
    version = 1,
    mappedTypes = listOf(PersistentBankAccountState::class.java)
) {

    @Entity
    @Table(name = "bank_account_states")
    class PersistentBankAccountState(
        @Column(name = "owner")
        var owner: String,
        @Column(name = "created_by")
        var createdBy: String,
        @Column(name = "amount")
        var amount: String,
        @Column(name = "currency")
        var currency: String,
        @Column(name = "status")
        var status: String,
        @Column(name = "type")
        var type: String,
        @Column(name = "verified")
        var verified: Boolean,
        @Column(name = "linear_id")
        var linearId: String,
        @Column(name = "external_id")
        var externalId: String,
        @Column(name = "created_at")
        var createdAt: Long,
        @Column(name = "last_updated")
        var lastUpdated: Long
    ) : PersistentState() {
        @Suppress("UNUSED")
        constructor() : this(
            owner = "",
            createdBy = "",
            amount = "",
            currency = "",
            status = "",
            type = "",
            verified = false,
            linearId = "",
            externalId = "",
            createdAt = 0L,
            lastUpdated = 0L
        )
    }

}