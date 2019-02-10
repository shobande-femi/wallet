package com.example.wallet.workflows

import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.getOrThrow
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.User

/**
 * Allows you to run your nodes through an IDE (as opposed to using deployNodes). Do not use in a production
 * environment.
 */
fun main(args: Array<String>) {
    val rpcUsers = listOf(User("user1", "test", permissions = setOf("ALL")))

    driver(DriverParameters(startNodesInProcess = true, waitForAllNodesToFinish = true)) {
        listOf(
            startNode(providedName = CordaX500Name("Issuer", "Victoria Island", "NG"), rpcUsers = rpcUsers),
            startNode(providedName = CordaX500Name("GatewayA", "Victoria Island", "NG"), rpcUsers = rpcUsers),
            startNode(providedName = CordaX500Name("GatewayB", "Victoria Island", "NG"), rpcUsers = rpcUsers)
        ).map { it.getOrThrow() }
    }
}