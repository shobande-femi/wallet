package com.example.wallet.common.types

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
enum class TransferType {
    ISSUER_TO_GATEWAY,
    ISSUER_TO_REGULAR_USER,
    ISSUER_TO_LIQUIDITY_PROVIDER,

    GATEWAY_TO_GATEWAY,
    GATEWAY_TO_ISSUER,
    GATEWAY_TO_REGULAR_USER,
    GATEWAY_TO_LIQUIDITY_PROVIDER,

    REGULAR_USER_TO_REGULAR_USER,
    REGULAR_USER_TO_ISSUER,
    REGULAR_USER_TO_GATEWAY,
    REGULAR_USER_TO_LIQUIDITY_PROVIDER,

    LIQUIDITY_PROVIDER_TO_LIQUIDITY_PROVIDER,
    LIQUIDITY_PROVIDER_TO_ISSUER,
    LIQUIDITY_PROVIDER_TO_GATEWAY,
    LIQUIDITY_PROVIDER_TO_REGULAR_USER
}

fun isOnUs(transferType: TransferType): Boolean {
    when (transferType) {
        TransferType.ISSUER_TO_GATEWAY -> return false
        TransferType.ISSUER_TO_REGULAR_USER -> return true
        TransferType.ISSUER_TO_LIQUIDITY_PROVIDER -> return true

        TransferType.GATEWAY_TO_GATEWAY -> return false
        TransferType.GATEWAY_TO_ISSUER -> return false
        TransferType.GATEWAY_TO_REGULAR_USER -> return false
        TransferType.GATEWAY_TO_LIQUIDITY_PROVIDER -> return false

        TransferType.REGULAR_USER_TO_REGULAR_USER -> return true
        TransferType.REGULAR_USER_TO_ISSUER -> return true
        TransferType.REGULAR_USER_TO_GATEWAY -> return false
        TransferType.REGULAR_USER_TO_LIQUIDITY_PROVIDER -> return true

        TransferType.LIQUIDITY_PROVIDER_TO_LIQUIDITY_PROVIDER -> return true
        TransferType.LIQUIDITY_PROVIDER_TO_ISSUER -> return true
        TransferType.LIQUIDITY_PROVIDER_TO_GATEWAY -> return false
        TransferType.LIQUIDITY_PROVIDER_TO_REGULAR_USER -> return true
    }
}
