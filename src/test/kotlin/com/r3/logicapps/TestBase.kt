package com.r3.logicapps

import com.r3.logicapps.servicebus.ServicebusClientTests.Companion.FROM_CORDA_QUEUE
import com.r3.logicapps.servicebus.ServicebusClientTests.Companion.SERVICE_BUS
import com.r3.logicapps.servicebus.ServicebusClientTests.Companion.TO_CORDA_QUEUE
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.TestIdentity
import net.corda.testing.driver.DriverDSL
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.TestCordapp

abstract class TestBase {
    protected fun String.toIdentity() = TestIdentity(CordaX500Name(this, "", "GB"))

    protected fun withDriver(block: DriverDSL.() -> Unit) = driver(
        DriverParameters(
            cordappsForAllNodes = listOf(
                TestCordapp.findCordapp("com.r3.logicapps").withConfig(mapOf(
                    "connectionString" to SERVICE_BUS,
                    "inboundQueue" to TO_CORDA_QUEUE,
                    "outboundQueue" to FROM_CORDA_QUEUE
                ))
            ),
            isDebug = true,
            startNodesInProcess = true
        ), block
    )

    protected fun DriverDSL.startNodes(vararg identities: TestIdentity) = identities
        .map { startNode(providedName = it.name).getOrThrow() }
}