package com.r3.logicapps.processing

import com.r3.logicapps.BusRequest
import com.r3.logicapps.BusResponse
import com.r3.logicapps.TestBase
import net.corda.core.contracts.UniqueIdentifier
import org.junit.Test
import kotlin.test.assertEquals

class MessageProcessorTest : TestBase() {
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
    fun `can invoke simple flow without arguments`() {
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
    fun `can invoke simple flow with empty arguments`() {
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
    fun `can invoke simple flow with specific arguments`() {
        val requestId = "1234"
        val linearId = UniqueIdentifier()
        val params = mapOf("a" to "hello", "b" to "123", "c" to "1.23", "d" to "true")

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
    fun `cannot invoke simple flow with wrong arguments`() {
        val requestId = "1234"
        val linearId = UniqueIdentifier()
        val params = mapOf("a" to "hello", "b" to "a123", "c" to "1.23", "d" to "true")

        val messageProcessor = MessageProcessorImpl { FlowInvocationResult(linearId = linearId) }
        val busResponse = messageProcessor.invoke(
            BusRequest.InvokeFlowWithoutInputStates(requestId, "com.r3.logicapps.processing.SimpleFlowWithInput", params)
        )

        val response = busResponse as? BusResponse.FlowError
            ?: error("Response of type ${busResponse::class.simpleName}, expected FlowError")
        assertEquals(requestId, response.requestId)
        assertEquals(IllegalStateException::class, response.exception::class)
    }
}