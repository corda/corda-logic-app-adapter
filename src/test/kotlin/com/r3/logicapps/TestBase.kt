package com.r3.logicapps

import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.TestIdentity
import net.corda.testing.driver.DriverDSL
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.driver
import net.corda.testing.node.TestCordapp

abstract class TestBase {
    protected fun String.toIdentity() = TestIdentity(CordaX500Name(this, "", "GB"))

    protected fun withDriver(block: DriverDSL.() -> Unit) = driver(
        DriverParameters(
            cordappsForAllNodes = listOf(TestCordapp.findCordapp("com.r3.logicapps").withConfig(mapOf(
                "connectionString" to "Endpoint=sb://bogdan-logicapp-bus.servicebus.windows.net/;SharedAccessKeyName=RootManageSharedAccessKey;SharedAccessKey=eAIZ5rfIoZQCEeZ9GGcxvjO6m20hCKs9wbzAykAtcSU=",
                "inboundQueue" to "to-corda",
                "outboundQueue" to "from-corda"
            ))),
            isDebug = true,
            startNodesInProcess = true
        ), block
    )

    protected fun DriverDSL.startNodes(vararg identities: TestIdentity) = identities
        .map { startNode(providedName = it.name).getOrThrow() }

    protected fun NodeHandle.resolveName(name: CordaX500Name) = rpc.wellKnownPartyFromX500Name(name)!!.name
}