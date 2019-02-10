package com.example.wallet.common.schemas

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

object WalletStateSchema

object WalletStateSchemaV1 : MappedSchema(
    schemaFamily = WalletStateSchema.javaClass,
    version = 1,
    mappedTypes = listOf(PersistentWalletState::class.java)
) {

    @Entity
    @Table(name = "wallet_states")
    class PersistentWalletState(
        @Column(name = "owner")
        var owner: String,
        @Column(name = "issued_by")
        var issuedBy: String,
        @Column(name = "balance")
        var balance: String,
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
        @Column(name = "wallet_id")
        var walletId: String,
        @Column(name = "created_at")
        var createdAt: Long,
        @Column(name = "last_updated")
        var lastUpdated: Long
    ) : PersistentState() {
        @Suppress("UNUSED")
        constructor() : this(
            owner = "",
            issuedBy = "",
            balance = "",
            currency = "",
            status = "",
            type = "",
            verified = false,
            linearId = "",
            walletId = "",
            createdAt = 0L,
            lastUpdated = 0L
        )
    }

}