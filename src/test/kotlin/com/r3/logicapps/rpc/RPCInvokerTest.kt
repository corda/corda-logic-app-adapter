package com.r3.logicapps.rpc

import com.r3.logicapps.TestBase
import org.junit.Test
import kotlin.test.assertEquals

class RPCInvokerTest : TestBase() {
    private val partyA = "PartyA".toIdentity()
    private val partyB = "PartyB".toIdentity()

    @Test
    fun `node test`() = withDriver {
        val (partyAHandle, partyBHandle) = startNodes(partyA, partyB)
        assertEquals(partyB.name, partyAHandle.resolveName(partyB.name))
        assertEquals(partyA.name, partyBHandle.resolveName(partyA.name))
    }
}