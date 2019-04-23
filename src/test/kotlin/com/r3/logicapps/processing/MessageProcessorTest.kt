package com.r3.logicapps.processing

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.r3.logicapps.BusRequest
import com.r3.logicapps.BusRequest.InvokeFlowWithoutInputStates
import com.r3.logicapps.BusResponse
import com.r3.logicapps.BusResponse.Error.FlowError
import com.r3.logicapps.TestBase
import com.r3.logicapps.stubs.ServiceBusClientStub
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash.Companion
import net.corda.core.identity.CordaX500Name
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals

class MessageProcessorTest : TestBase() {

    private val busClient = ServiceBusClientStub()
    private val mockUUID = UUID.randomUUID()

    @Test
    fun `fails if referencing non-existent class`() {
        val requestId = "1234"
        val linearId = UniqueIdentifier()

        val messageProcessor = MessageProcessorImpl(
            startFlowDelegate = { _, _, _ -> FlowInvocationResult(linearId = linearId, hash = null) },
            retrieveStateDelegate = { StateQueryResult(isNewContract = false) }
        )
        val (busResponse) = messageProcessor.invoke(
            BusRequest.InvokeFlowWithoutInputStates(requestId, "com.nowhere.SimpleFlow", emptyMap()),
            busClient,
            mockUUID
        )

        val response = busResponse as? FlowError
            ?: error("Response of type ${busResponse::class.simpleName}, expected FlowError")
        assertEquals(ClassNotFoundException::class, response.cause::class)
    }

    @Test
    fun `can invoke simple flow without arguments`() {
        val requestId = "1234"
        val linearId = UniqueIdentifier()

        val messageProcessor = MessageProcessorImpl(
            startFlowDelegate = { _, _, _ ->
                FlowInvocationResult(
                    linearId = linearId,
                    hash = Companion.zeroHash,
                    fromName = CordaX500Name.parse("O=Member 1, L=London, C=GB"),
                    toNames = emptyList()
                )
            },
            retrieveStateDelegate = { StateQueryResult(isNewContract = false) }
        )

        val (busResponse, commit, submit) = messageProcessor.invoke(
            BusRequest.InvokeFlowWithoutInputStates(requestId, "com.r3.logicapps.processing.SimpleFlow", emptyMap()),
            busClient,
            mockUUID
        )

        val response = busResponse as? BusResponse.FlowOutput
            ?: error("Response of type ${busResponse::class.simpleName}, expected FlowOutput")

        val cr = commit as? BusResponse.Confirmation.Committed
            ?: error("Response of type ${commit::class.simpleName}, expected Committed")
        val sr = submit as? BusResponse.Confirmation.Submitted
            ?: error("Response of type ${submit::class.simpleName}, expected Submitted")

        assertThat(cr.requestId, equalTo(requestId))
        assertThat(sr.requestId, equalTo(requestId))

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

        val messageProcessor = MessageProcessorImpl(
            startFlowDelegate = { _, _, _ ->
                FlowInvocationResult(
                    linearId = linearId,
                    hash = Companion.zeroHash,
                    fromName = CordaX500Name.parse("O=Member 1, L=London, C=GB"),
                    toNames = emptyList()
                )
            },
            retrieveStateDelegate = { StateQueryResult(isNewContract = false) }
        )

        val (busResponse, commit, submit) = messageProcessor.invoke(
            BusRequest.InvokeFlowWithoutInputStates(
                requestId,
                "com.r3.logicapps.processing.SimpleFlowWithInput",
                params
            ),
            busClient,
            mockUUID
        )

        val response = busResponse as? BusResponse.FlowOutput
            ?: error("Response of type ${busResponse::class.simpleName}, expected FlowOutput")

        val cr = commit as? BusResponse.Confirmation.Committed
            ?: error("Response of type ${commit::class.simpleName}, expected Committed")
        val sr = submit as? BusResponse.Confirmation.Submitted
            ?: error("Response of type ${submit::class.simpleName}, expected Submitted")

        assertThat(cr.requestId, equalTo(requestId))
        assertThat(sr.requestId, equalTo(requestId))

        assertEquals(requestId, response.requestId)
        assertEquals(0, response.fields.size)
        assertEquals(linearId, response.linearId)
        assertEquals(true, response.isNewContract)
    }

    @Test
    fun `can invoke simple flow with arguments and an abbreviated flow name`() {
        val requestId = "1234"
        val linearId = UniqueIdentifier()
        val params = mapOf("a" to "a", "b" to "1", "c" to "2", "d" to "true")

        val messageProcessor = MessageProcessorImpl(
            startFlowDelegate = { _, _, _ ->
                FlowInvocationResult(
                    linearId = linearId,
                    hash = Companion.zeroHash,
                    fromName = CordaX500Name.parse("O=Member 1, L=London, C=GB"),
                    toNames = emptyList()
                )
            },
            retrieveStateDelegate = { StateQueryResult(isNewContract = false) }
        )

        val (busResponse, commit, submit) = messageProcessor.invoke(
            InvokeFlowWithoutInputStates(
                requestId,
                "SimpleFlowWithInput",
                params
            ),
            busClient,
            mockUUID
        )

        val response = busResponse as? BusResponse.FlowOutput
            ?: error("Response of type ${busResponse::class.simpleName}, expected FlowOutput")

        val cr = commit as? BusResponse.Confirmation.Committed
            ?: error("Response of type ${commit::class.simpleName}, expected Committed")
        val sr = submit as? BusResponse.Confirmation.Submitted
            ?: error("Response of type ${submit::class.simpleName}, expected Submitted")

        assertThat(cr.requestId, equalTo(requestId))
        assertThat(sr.requestId, equalTo(requestId))

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

        val messageProcessor = MessageProcessorImpl(
            startFlowDelegate = { _, _, _ ->
                FlowInvocationResult(
                    linearId = linearId,
                    hash = Companion.zeroHash,
                    fromName = CordaX500Name.parse("O=Member 1, L=London, C=GB"),
                    toNames = emptyList()
                )
            },
            retrieveStateDelegate = { StateQueryResult(isNewContract = false) }
        )

        val (busResponse, commit, submit) = messageProcessor.invoke(
            BusRequest.InvokeFlowWithoutInputStates(
                requestId,
                "com.r3.logicapps.processing.SimpleFlowWithInput",
                params
            ),
            busClient,
            mockUUID
        )

        val cr = commit as? BusResponse.Confirmation.Committed
            ?: error("Response of type ${commit::class.simpleName}, expected Committed")
        val sr = submit as? BusResponse.Confirmation.Submitted
            ?: error("Response of type ${submit::class.simpleName}, expected Submitted")

        assertThat(cr.requestId, equalTo(requestId))
        assertThat(sr.requestId, equalTo(requestId))

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

        val messageProcessor = MessageProcessorImpl(
            startFlowDelegate = { _, _ , _ ->
                FlowInvocationResult(
                    linearId = linearId,
                    hash = Companion.zeroHash,
                    fromName = CordaX500Name.parse("O=Member 1, L=London, C=GB"),
                    toNames = emptyList()
                )
            },
            retrieveStateDelegate = { StateQueryResult(isNewContract = false) }
        )

        val busResponses = messageProcessor.invoke(
            BusRequest.InvokeFlowWithoutInputStates(
                requestId,
                "com.r3.logicapps.processing.SimpleFlowWithInput",
                params
            ),
            busClient,
            mockUUID
        )

        val (busResponse) = busResponses
        val r = busResponse as? FlowError
            ?: error("Response of type ${busResponses::class.simpleName}, expected FlowError")

        assertEquals(IllegalStateException::class, r.cause::class)
    }

    @Test
    fun `can invoke simple flow with multiple recipients`() {
        val requestId = "1234"
        val linearId = UniqueIdentifier()
        val params = mapOf("a" to "hello", "b" to "123", "c" to "1.23", "d" to "true")

        val messageProcessor = MessageProcessorImpl(
            startFlowDelegate = { _, _, _ ->
                FlowInvocationResult(
                    linearId = linearId,
                    hash = Companion.zeroHash,
                    fromName = CordaX500Name.parse("O=Member 1, L=London, C=GB"),
                    toNames = listOf(
                        CordaX500Name.parse("O=Member 2, L=Madrid, C=ES"),
                        CordaX500Name.parse("O=Member 3, L=Bilbao, C=ES")
                    )
                )
            },
            retrieveStateDelegate = { StateQueryResult(isNewContract = false) }
        )

        val (invocation1, invocation2, busResponse, commit, submit) = messageProcessor.invoke(
            BusRequest.InvokeFlowWithoutInputStates(
                requestId,
                "com.r3.logicapps.processing.SimpleFlowWithInput",
                params
            ),
            busClient,
            mockUUID
        )

        val i1 = invocation1 as? BusResponse.InvocationState
            ?: error("Response of type ${invocation1::class.simpleName}, expected InvocationState")
        val i2 = invocation2 as? BusResponse.InvocationState
            ?: error("Response of type ${invocation2::class.simpleName}, expected InvocationState")


        assertThat(i1.requestId, equalTo(requestId))
        assertThat(i2.requestId, equalTo(requestId))

        val cr = commit as? BusResponse.Confirmation.Committed
            ?: error("Response of type ${commit::class.simpleName}, expected Committed")
        val sr = submit as? BusResponse.Confirmation.Submitted
            ?: error("Response of type ${submit::class.simpleName}, expected Submitted")

        assertThat(cr.requestId, equalTo(requestId))
        assertThat(sr.requestId, equalTo(requestId))

        val response = busResponse as? BusResponse.FlowOutput
            ?: error("Response of type ${busResponse::class.simpleName}, expected FlowOutput")
        assertEquals(requestId, response.requestId)
        assertEquals(0, response.fields.size)
        assertEquals(linearId, response.linearId)
        assertEquals(true, response.isNewContract)
    }

}