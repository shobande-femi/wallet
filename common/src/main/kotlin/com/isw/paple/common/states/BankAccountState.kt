package com.isw.paple.common.states

import com.isw.paple.common.schemas.BankAccountStateSchemaV1
import com.isw.paple.common.types.AccountNumber
import com.isw.paple.common.types.BankAccountType
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.unspecifiedCountry
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import java.time.Instant
import java.util.*

data class BankAccountState(
        val owner: Party,
        val country: String,
        val accountName: String,
        val accountNumber: AccountNumber,
        val currency: Currency,
        val type: BankAccountType,
        val verified: Boolean,
        override val participants: List<AbstractParty>,
        override val linearId: UniqueIdentifier,
        val lastUpdated: Instant = Instant.now()
) : LinearState, QueryableState {

    constructor(
            owner: Party,
            country: String,
            accountId: String,
            accountName: String,
            accountNumber: AccountNumber,
            currency: Currency,
            type: BankAccountType
    ) : this(owner, country, accountName, accountNumber, currency, type, false, listOf(owner), UniqueIdentifier(accountId))

    init {
        require(country in countryCodes) { "Invalid country code $country" }
    }

    companion object {
        private val countryCodes: Set<String> = setOf(*Locale.getISOCountries(), CordaX500Name.unspecifiedCountry)
    }

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is BankAccountStateSchemaV1 -> BankAccountStateSchemaV1.PersistentBankAccountState(
                    owner = owner.name.toString(),
                    country = country,
                    accountName = accountName,
                    accountNumber = accountNumber.digits,
                    currency = currency.currencyCode,
                    type = type.name,
                    verified = verified,
                    lastUpdated = lastUpdated.toEpochMilli(),
                    linearId = linearId.id.toString(),
                    externalId = linearId.externalId.toString()
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(BankAccountStateSchemaV1)
}