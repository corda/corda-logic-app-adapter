package com.r3.logicapps.processing

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.r3.logicapps.BusRequest.InvokeFlowWithoutInputStates
import com.r3.logicapps.BusResponse
import com.r3.logicapps.BusResponse.Error.FlowError
import com.r3.logicapps.TestBase
import com.r3.logicapps.stubs.ServiceBusClientStub
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash.Companion
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.ALICE_NAME
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
            retrieveStateDelegate = { StateQueryResult(isNewContract = false) },
            owner = ALICE_NAME
        )
        val (busResponse) = messageProcessor.invoke(
            InvokeFlowWithoutInputStates(requestId, "com.nowhere.SimpleFlow", emptyMap()),
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
                    fromName = CordaX500Name.parse("O=Member 1, L=London, C=GB"),
                    toNames = emptyList(),
                    hash = Companion.zeroHash
                )
            },
            retrieveStateDelegate = { StateQueryResult(isNewContract = false) },
            owner = ALICE_NAME
        )

        val (state, output, commit, submit) = messageProcessor.invoke(
            InvokeFlowWithoutInputStates(requestId, "com.r3.logicapps.processing.SimpleFlow", emptyMap()),
            busClient,
            mockUUID
        )

        val s = state as? BusResponse.InvocationState
            ?: error("Response of type ${output::class.simpleName}, expected InvocationState")
        val o = output as? BusResponse.FlowOutput
            ?: error("Response of type ${output::class.simpleName}, expected FlowOutput")
        val cr = commit as? BusResponse.Confirmation.Committed
            ?: error("Response of type ${commit::class.simpleName}, expected Committed")
        val sr = submit as? BusResponse.Confirmation.Submitted
            ?: error("Response of type ${submit::class.simpleName}, expected Submitted")

        assertThat(s.requestId, equalTo(requestId))
        assertThat(s.caller.toString(), equalTo("O=Alice Corp, L=Madrid, C=ES"))
        assertThat(cr.requestId, equalTo(requestId))
        assertThat(sr.requestId, equalTo(requestId))

        assertEquals(requestId, o.requestId)
        assertEquals(0, o.fields.size)
        assertEquals(linearId, o.linearId)
        assertEquals(true, o.isNewContract)
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
                    fromName = CordaX500Name.parse("O=Member 1, L=London, C=GB"),
                    toNames = emptyList(),
                    hash = Companion.zeroHash
                )
            },
            retrieveStateDelegate = { StateQueryResult(isNewContract = false) },
            owner = ALICE_NAME
        )

        val (state, output, commit, submit) = messageProcessor.invoke(
            InvokeFlowWithoutInputStates(
                requestId,
                "com.r3.logicapps.processing.SimpleFlowWithInput",
                params
            ),
            busClient,
            mockUUID
        )

        val response = output as? BusResponse.FlowOutput
            ?: error("Response of type ${output::class.simpleName}, expected FlowOutput")

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
                    fromName = CordaX500Name.parse("O=Member 1, L=London, C=GB"),
                    toNames = emptyList(),
                    hash = Companion.zeroHash
                )
            },
            retrieveStateDelegate = { StateQueryResult(isNewContract = false) },
            owner = ALICE_NAME
        )

        val (state, output, commit, submit) = messageProcessor.invoke(
            InvokeFlowWithoutInputStates(
                requestId,
                "SimpleFlowWithInput",
                params
            ),
            busClient,
            mockUUID
        )

        val response = output as? BusResponse.FlowOutput
            ?: error("Response of type ${output::class.simpleName}, expected FlowOutput")
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
                    fromName = CordaX500Name.parse("O=Member 1, L=London, C=GB"),
                    toNames = emptyList(),
                    hash = Companion.zeroHash
                )
            },
            retrieveStateDelegate = { StateQueryResult(isNewContract = false) },
            owner = ALICE_NAME
        )

        val (state, output, commit, submit) = messageProcessor.invoke(
            InvokeFlowWithoutInputStates(
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

        val response = output as? BusResponse.FlowOutput
            ?: error("Response of type ${output::class.simpleName}, expected FlowOutput")
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
            startFlowDelegate = { _, _, _ ->
                FlowInvocationResult(
                    linearId = linearId,
                    fromName = CordaX500Name.parse("O=Member 1, L=London, C=GB"),
                    toNames = emptyList(),
                    hash = Companion.zeroHash
                )
            },
            retrieveStateDelegate = { StateQueryResult(isNewContract = false) },
            owner = ALICE_NAME
        )

        val busResponses = messageProcessor.invoke(
            InvokeFlowWithoutInputStates(
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
                    fromName = CordaX500Name.parse("O=Member 1, L=London, C=GB"),
                    toNames = listOf(
                        CordaX500Name.parse("O=Member 2, L=Madrid, C=ES"),
                        CordaX500Name.parse("O=Member 3, L=Bilbao, C=ES")
                    ),
                    hash = Companion.zeroHash
                )
            },
            retrieveStateDelegate = { StateQueryResult(isNewContract = false) },
            owner = ALICE_NAME
        )

        val (invocation, busResponse, commit, submit) = messageProcessor.invoke(
            InvokeFlowWithoutInputStates(
                requestId,
                "com.r3.logicapps.processing.SimpleFlowWithInput",
                params
            ),
            busClient,
            mockUUID
        )

        val inv = invocation as? BusResponse.InvocationState
            ?: error("Response of type ${invocation::class.simpleName}, expected InvocationState")

        assertThat(inv.requestId, equalTo(requestId))

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