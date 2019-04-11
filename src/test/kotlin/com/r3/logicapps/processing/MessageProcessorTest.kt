package com.r3.logicapps.processing

import com.r3.logicapps.BusRequest
import com.r3.logicapps.BusResponse
import com.r3.logicapps.TestBase
import net.corda.core.contracts.UniqueIdentifier
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals

class MessageProcessorTest : TestBase() {
    private val partyA = "PartyA".toIdentity()
    private val partyB = "PartyB".toIdentity()

    @Test
    fun `fails if referencing non-existent class`() {
        val requestId = "1234"
        val linearId = UniqueIdentifier()

        val messageProcessor = MessageProcessorImpl { FlowInvocationResult(linearId = linearId) }
        val busResponse = messageProcessor.invoke(
            BusRequest.InvokeFlowWithoutInputStates(requestId, "com.nowhere.SimpleFlow", emptyMap())
        )

        val response = busResponse as? BusResponse.FlowError
            ?: error("Response of type ${busResponse::class.simpleName}, expected FlowError")
        assertEquals(ClassNotFoundException::class, response.exception::class)
    }

    @Test
    fun `can invoke simple flow without parameters`() {
        val requestId = "1234"
        val linearId = UniqueIdentifier()

        val messageProcessor = MessageProcessorImpl { FlowInvocationResult(linearId = linearId) }
        val busResponse = messageProcessor.invoke(
            BusRequest.InvokeFlowWithoutInputStates(requestId, "com.r3.logicapps.processing.SimpleFlow", emptyMap())
        )

        val response = busResponse as? BusResponse.FlowOutput
            ?: error("Response of type ${busResponse::class.simpleName}, expected FlowOutput")
        assertEquals(requestId, response.requestId)
        assertEquals(0, response.fields.size)
        assertEquals(linearId, response.linearId)
        assertEquals(true, response.isNewContract)
    }

    @Test
    fun `can invoke simple flow with parameters`() {
        val requestId = "1234"
        val linearId = UniqueIdentifier()
        val params = mapOf("a" to "", "b" to "", "c" to "", "d" to "")

        val messageProcessor = MessageProcessorImpl { FlowInvocationResult(linearId = linearId) }
        val busResponse = messageProcessor.invoke(
            BusRequest.InvokeFlowWithoutInputStates(requestId, "com.r3.logicapps.processing.SimpleFlowWithInput", params)
        )

        val response = busResponse as? BusResponse.FlowOutput
            ?: error("Response of type ${busResponse::class.simpleName}, expected FlowOutput")
        assertEquals(requestId, response.requestId)
        assertEquals(0, response.fields.size)
        assertEquals(linearId, response.linearId)
        assertEquals(true, response.isNewContract)
    }

    @Test
    @Ignore
    fun `node test`() = withDriver {
        val (partyAHandle, partyBHandle) = startNodes(partyA, partyB)
        assertEquals(partyB.name, partyAHandle.resolveName(partyB.name))
        assertEquals(partyA.name, partyBHandle.resolveName(partyA.name))
    }
}