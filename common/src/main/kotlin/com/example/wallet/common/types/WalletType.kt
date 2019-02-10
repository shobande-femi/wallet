package com.example.wallet.common.types

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
enum class WalletType {
    UNKNOWN,
    ISSUER_OWNED,
    GATEWAY_OWNED,
    LIQUIDITY_PROVIDER_OWNED,
    REGULAR_USER_OWNED
}