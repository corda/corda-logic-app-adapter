package com.r3.logicapps.workbench

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.isA
import com.natpryce.hamkrest.throws
import com.r3.logicapps.BusRequest.InvokeFlowWithInputStates
import com.r3.logicapps.BusRequest.InvokeFlowWithoutInputStates
import com.r3.logicapps.BusRequest.QueryFlowState
import com.r3.logicapps.BusResponse.Confirmation.Committed
import com.r3.logicapps.BusResponse.Confirmation.Submitted
import com.r3.logicapps.BusResponse.FlowError
import com.r3.logicapps.BusResponse.FlowOutput
import com.r3.logicapps.BusResponse.InvocationState
import com.r3.logicapps.BusResponse.StateOutput
import com.r3.logicapps.servicebus.ServicebusMessage
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.UniqueIdentifier.Companion
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.CordaX500Name
import org.junit.Test

class WorkbenchAdapterTests {

    @Test
    fun `transforming an unknown message fails`() {
        val json = """{
            |   "messageName" : "NonSensicalRequest"
            |}""".trimMargin()

        assertThat(
            { WorkbenchAdapterImpl.transformIngress(json) },
            throws(isA<IllegalArgumentException>(has(Exception::message, equalTo("Unknown message name"))))
        )
    }

    @Test
    fun `transforming an invalid CreateContractRequest fails`() {
        val json = """{
        |  "messageName": "CreateContractRequest",
        |  "hocus" : "pocus"
        |}""".trimMargin()

        assertThat(
            { WorkbenchAdapterImpl.transformIngress(json) },
            throws(
                isA<IllegalArgumentException>(
                    has(
                        Exception::message,
                        equalTo("Not a valid message for schema class com.r3.logicapps.workbench.WorkbenchSchema\$FlowInvocationRequestSchema: #: 4 schema violations found")
                    )
                )
            )
        )
    }

    @Test
    fun `transforms a valid CreateContractRequest`() {
        val json = """{
        |  "messageName": "CreateContractRequest",
        |  "requestId": "81a87eb0-b5aa-4d53-a39f-a6ed0742d90d",
        |  "workflowName": "net.corda.workbench.refrigeratedTransportation.flow.CreateFlow",
        |  "parameters": [
        |    {
        |      "name": "state",
        |      "value": "Created"
        |    },
        |    {
        |      "name": "owner",
        |      "value": "O=Alice Ltd., L=Shanghai, C=CN"
        |    },
        |    {
        |      "name": "initiatingCounterparty",
        |      "value": "O=Bob Ltd., L=Beijing, C=CN"
        |    },
        |    {
        |      "name": "device",
        |      "value": "O=Charly GmbH, OU=Device01, L=Berlin, C=DE"
        |    },
        |    {
        |      "name": "supplyChainOwner",
        |      "value": "O=Denise SARL, L=Marseille, C=FR"
        |    },
        |    {
        |      "name": "supplyChainObserver",
        |      "value": "O=Denise SARL, L=Marseille, C=FR"
        |    },
        |    {
        |      "name": "minHumidity",
        |      "value": "12"
        |    },
        |    {
        |      "name": "maxHumidity",
        |      "value": "45"
        |    },
        |    {
        |      "name": "minTemperature",
        |      "value": "-20"
        |    },
        |    {
        |      "name": "maxTemperature",
        |      "value": "-7"
        |    }
        |  ],
        |  "messageSchemaVersion": "1.0.0"
        |}""".trimMargin()

        val actual = WorkbenchAdapterImpl.transformIngress(json)

        val expected = InvokeFlowWithoutInputStates(
            requestId = "81a87eb0-b5aa-4d53-a39f-a6ed0742d90d",
            workflowName = "net.corda.workbench.refrigeratedTransportation.flow.CreateFlow",
            parameters = mapOf(
                "state" to "Created",
                "owner" to "O=Alice Ltd., L=Shanghai, C=CN",
                "initiatingCounterparty" to "O=Bob Ltd., L=Beijing, C=CN",
                "device" to "O=Charly GmbH, OU=Device01, L=Berlin, C=DE",
                "supplyChainOwner" to "O=Denise SARL, L=Marseille, C=FR",
                "supplyChainObserver" to "O=Denise SARL, L=Marseille, C=FR",
                "minHumidity" to "12",
                "maxHumidity" to "45",
                "minTemperature" to "-20",
                "maxTemperature" to "-7"
            )
        )

        @Suppress("RemoveExplicitTypeArguments")
        assertThat(actual, isA<InvokeFlowWithoutInputStates>(equalTo(expected)))
    }

    @Test
    fun `transforming an invalid CreateContractActionRequest fails`() {
        val json = """{
        |  "messageName": "CreateContractActionRequest",
        |  "hocus" : "pocus"
        |}""".trimMargin()

        assertThat(
            { WorkbenchAdapterImpl.transformIngress(json) },
            throws(
                isA<IllegalArgumentException>(
                    has(
                        Exception::message,
                        equalTo("Not a valid message for schema class com.r3.logicapps.workbench.WorkbenchSchema\$FlowUpdateRequestSchema: #: 5 schema violations found")
                    )
                )
            )
        )
    }

    @Test
    fun `transforms a valid CreateContractActionRequest`() {
        val json = """{
        |    "messageName": "CreateContractActionRequest",
        |    "requestId": "5a2b34a6-5fa0-4400-b1f5-686a7c212d52",
        |    "contractLedgerIdentifier": "f2ef3c6f-4e1a-4375-bb3c-f622c29ec3b6",
        |    "workflowFunctionName": "net.corda.workbench.refrigeratedTransportation.flow.CreateFlow",
        |    "parameters": [
        |        {
        |            "name": "newCounterparty",
        |            "value": "NorthwindTraders"
        |        }
        |    ],
        |    "messageSchemaVersion": "1.0.0"
        |}""".trimMargin()

        val actual = WorkbenchAdapterImpl.transformIngress(json)

        val expected = InvokeFlowWithInputStates(
            requestId = "5a2b34a6-5fa0-4400-b1f5-686a7c212d52",
            linearId = UniqueIdentifier.fromString("f2ef3c6f-4e1a-4375-bb3c-f622c29ec3b6"),
            workflowName = "net.corda.workbench.refrigeratedTransportation.flow.CreateFlow",
            parameters = mapOf("newCounterparty" to "NorthwindTraders")
        )

        @Suppress("RemoveExplicitTypeArguments")
        assertThat(actual, isA<InvokeFlowWithInputStates>(equalTo(expected)))
    }

    @Test
    fun `transforming an invalid ReadContractRequest fails`() {
        val json = """{
        |  "messageName": "ReadContractRequest",
        |  "hocus" : "pocus"
        |}""".trimMargin()

        assertThat(
            { WorkbenchAdapterImpl.transformIngress(json) },
            throws(
                isA<IllegalArgumentException>(
                    has(
                        Exception::message,
                        equalTo("Not a valid message for schema class com.r3.logicapps.workbench.WorkbenchSchema\$FlowStateRequestSchema: #: 3 schema violations found")
                    )
                )
            )
        )
    }

    @Test
    fun `transforms a valid ReadContractRequest`() {
        val json = """{
        |    "messageName": "ReadContractRequest",
        |    "requestId": "9c2e532f-15bb-4eb8-ae58-34722c5776f4",
        |    "contractLedgerIdentifier": "3aa6120b-b809-4cdc-9a19-81546482b313",
        |    "messageSchemaVersion": "1.0.0"
        |}""".trimMargin()

        val actual = WorkbenchAdapterImpl.transformIngress(json)

        val expected = QueryFlowState(
            requestId = "9c2e532f-15bb-4eb8-ae58-34722c5776f4",
            linearId = UniqueIdentifier.fromString("3aa6120b-b809-4cdc-9a19-81546482b313")
        )

        @Suppress("RemoveExplicitTypeArguments")
        assertThat(actual, isA<QueryFlowState>(equalTo(expected)))
    }

    @Test
    fun `generates a valid service bus message for a flow output`() {
        val expected = """{
        |  "messageName" : "ContractMessage",
        |  "blockId" : 999,
        |  "blockHash" : "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",
        |  "requestId" : "81a87eb0-b5aa-4d53-a39f-a6ed0742d90d",
        |  "additionalInformation" : { },
        |  "contractLedgerIdentifier" : "f1a27656-3b1a-4469-8e37-04d9e2764bf6",
        |  "contractProperties" : [ {
        |    "name" : "state",
        |    "value" : "Created"
        |  }, {
        |    "name" : "owner",
        |    "value" : "O=Alice Ltd., L=Shanghai, C=CN"
        |  } ],
        |  "modifyingTransactions" : [ {
        |    "from" : "O=Member 1, L=London, C=GB",
        |    "to" : [ "O=Member 2, L=Manchester, C=GB", "O=Member 3, L=Bristol, C=GB" ],
        |    "transactionId" : 999,
        |    "transactionHash" : "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
        |  } ],
        |  "contractId" : 1,
        |  "connectionId" : 1,
        |  "messageSchemaVersion" : "1.0.0",
        |  "isNewContract" : false
        |}""".trimMargin()

        val actual = WorkbenchAdapterImpl.transformEgress(
            FlowOutput(
                InvokeFlowWithoutInputStates::class,
                "81a87eb0-b5aa-4d53-a39f-a6ed0742d90d",
                UniqueIdentifier.fromString("f1a27656-3b1a-4469-8e37-04d9e2764bf6"),
                mapOf(
                    "state" to "Created",
                    "owner" to "O=Alice Ltd., L=Shanghai, C=CN"
                ),
                false,
                CordaX500Name.parse("O=Member 1, L=London, C=GB"),
                listOf(
                    CordaX500Name.parse("O=Member 2, L=Manchester, C=GB"),
                    CordaX500Name.parse("O=Member 3, L=Bristol, C=GB")
                ),
                SecureHash.allOnesHash
            )
        )

        assertThat(actual.sanitized, equalTo(expected))
    }

    @Test
    fun `generates a valid service bus message for error output`() {
        val expected = """{
        |  "messageName" : "CreateContractRequest",
        |  "requestId" : "7d4ce6d9-554c-4bd0-acc8-b04cdef298f9",
        |  "additionalInformation" : {
        |    "errorCode" : 561675734,
        |    "errorMessage" : "Boooom!"
        |  },
        |  "contractLedgerIdentifier" : "27b3b7ad-10ce-4bd4-a72c-1bf215709a21",
        |  "status" : "Failure",
        |  "messageSchemaVersion" : "1.0.0"
        |}""".trimMargin()

        val actual = WorkbenchAdapterImpl.transformEgress(
            FlowError(
                ingressType = InvokeFlowWithoutInputStates::class,
                requestId = "7d4ce6d9-554c-4bd0-acc8-b04cdef298f9",
                linearId = Companion.fromString("27b3b7ad-10ce-4bd4-a72c-1bf215709a21"),
                exception = IllegalStateException("Boooom!")
            )
        )

        assertThat(actual.sanitized, equalTo(expected))
    }

    @Test
    fun `generates a valid service bus message for state queries`() {
        val expected = """{
        |  "messageName" : "ContractMessage",
        |  "requestId" : "7d4ce6d9-554c-4bd0-acc8-b04cdef298f9",
        |  "additionalInformation" : { },
        |  "contractLedgerIdentifier" : "27b3b7ad-10ce-4bd4-a72c-1bf215709a21",
        |  "contractProperties" : [ {
        |    "name" : "lorem",
        |    "value" : "ipsum"
        |  }, {
        |    "name" : "dolor",
        |    "value" : "sit amet"
        |  } ],
        |  "messageSchemaVersion" : "1.0.0",
        |  "isNewContract" : false
        |}""".trimMargin()

        val actual = WorkbenchAdapterImpl.transformEgress(
            StateOutput(
                requestId = "7d4ce6d9-554c-4bd0-acc8-b04cdef298f9",
                linearId = Companion.fromString("27b3b7ad-10ce-4bd4-a72c-1bf215709a21"),
                fields = mapOf(
                    "lorem" to "ipsum",
                    "dolor" to "sit amet"
                ),
                isNewContract = false
            )
        )

        assertThat(actual.sanitized, equalTo(expected))
    }

    @Test
    fun `a valid submitted message is generated `() {
        val expected = """{
        |  "messageName" : "CreateContractRequest",
        |  "additionalInformation" : { },
        |  "requestId" : "7d4ce6d9-554c-4bd0-acc8-b04cdef298f9",
        |  "contractId" : 1,
        |  "connectionId" : 1,
        |  "messageSchemaVersion" : "1.0.0",
        |  "status" : "Submitted"
        |}""".trimMargin()

        val actual = WorkbenchAdapterImpl.transformEgress(
            Submitted(
                ingressType = InvokeFlowWithoutInputStates::class,
                requestId = "7d4ce6d9-554c-4bd0-acc8-b04cdef298f9",
                linearId = Companion.fromString("27b3b7ad-10ce-4bd4-a72c-1bf215709a21")
            )
        )

        assertThat(actual.sanitized, equalTo(expected))
    }

    @Test
    fun `a valid committed message is generated `() {
        val expected = """{
        |  "messageName" : "CreateContractRequest",
        |  "additionalInformation" : { },
        |  "requestId" : "7d4ce6d9-554c-4bd0-acc8-b04cdef298f9",
        |  "contractId" : 1,
        |  "connectionId" : 1,
        |  "messageSchemaVersion" : "1.0.0",
        |  "status" : "Committed"
        |}""".trimMargin()

        val actual = WorkbenchAdapterImpl.transformEgress(
            Committed(
                ingressType = InvokeFlowWithoutInputStates::class,
                requestId = "7d4ce6d9-554c-4bd0-acc8-b04cdef298f9",
                linearId = Companion.fromString("27b3b7ad-10ce-4bd4-a72c-1bf215709a21")
            )
        )

        assertThat(actual.sanitized, equalTo(expected))
    }

    @Test
    fun `a valid event message is generated`() {
        val expected = """{
        |  "eventName" : "ContractFunctionInvocation",
        |  "requestId" : "7d4ce6d9-554c-4bd0-acc8-b04cdef298f9",
        |  "caller" : {
        |    "type" : "User",
        |    "id" : 388261153,
        |    "ledgerIdentifier" : "O=Member 1, L=London, C=GB"
        |  },
        |  "additionalInformation" : { },
        |  "contractId" : 1426306713,
        |  "contractProperties" : [ {
        |    "name" : "one",
        |    "value" : "eins"
        |  }, {
        |    "name" : "two",
        |    "value" : "zwei"
        |  }, {
        |    "name" : "three",
        |    "value" : "drei"
        |  } ],
        |  "transaction" : {
        |    "transactionId" : 1348943872,
        |    "transactionHash" : "0000000000000000000000000000000000000000000000000000000000000000",
        |    "from" : "O=Member 1, L=London, C=GB",
        |    "to" : "O=Member 2, L=Berlin, C=DE"
        |  },
        |  "inTransactionSequenceNumber" : 1,
        |  "connectionId" : 1,
        |  "messageSchemaVersion" : "1.0.0",
        |  "messageName" : "EventMessage"
        |}""".trimMargin()

        val actual = WorkbenchAdapterImpl.transformEgress(
            InvocationState(
                requestId = "7d4ce6d9-554c-4bd0-acc8-b04cdef298f9",
                linearId = Companion.fromString("27b3b7ad-10ce-4bd4-a72c-1bf215709a21"),
                parameters = mapOf(
                    "one" to "eins",
                    "two" to "zwei",
                    "three" to "drei"
                ),
                caller = CordaX500Name.parse("O=Member 1, L=London, C=GB"),
                flowClass = NonSenseFlow::class,
                fromName = CordaX500Name.parse("O=Member 1, L=London, C=GB"),
                toName = CordaX500Name.parse("O=Member 2, L=Berlin, C=DE"),
                transactionHash = SecureHash.zeroHash
            )
        )

        assertThat(actual.sanitized, equalTo(expected))
    }

    private val ServicebusMessage.sanitized: String
        get() = this.replace("\r", "")

    private class NonSenseFlow : FlowLogic<String>() {
        override fun call(): String = TODO()

    }
}
