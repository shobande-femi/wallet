package com.isw.paple.common.types

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import net.corda.core.serialization.CordaSerializable

/**
 * Marker interface for bank account numbers.
 */
// TODO: Remove this hack at some point.
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes(
        JsonSubTypes.Type(value = NigerianAccountNumber::class, name = "ng"),
        JsonSubTypes.Type(value = NoAccountNumber::class, name = "none")
)
@CordaSerializable
interface AccountNumber {
    val digits: String
}

fun validateAccountNumber(accountNumber: String, accountNumberLength: Int, country: String, onlyDigits: Boolean = true) {
    require(accountNumber.length == accountNumberLength) {
        "A $country bank account accountNumber must be eight digits long."
    }

    if (onlyDigits) {
        require(accountNumber.matches(Regex("[0-9]+"))) {
            "An account accountNumber must only contain the numbers zero to nine."
        }
    }
}

@CordaSerializable
data class NigerianAccountNumber(override val digits: String): AccountNumber {
    init {
        validateAccountNumber(digits, 10, "Nigerian")
    }
    override fun toString() = "Account Number: $digits"
}

/**
 * Sometimes we don't have a bank account number.
 */
@CordaSerializable
data class NoAccountNumber(override val digits: String = "No bank account number available.") : AccountNumber