package com.r3.logicapps.workbench

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.isA
import com.natpryce.hamkrest.present
import com.natpryce.hamkrest.startsWith
import com.natpryce.hamkrest.throws
import com.r3.logicapps.BusRequest.InvokeFlowWithInputStates
import com.r3.logicapps.BusRequest.InvokeFlowWithoutInputStates
import com.r3.logicapps.BusRequest.QueryFlowState
import net.corda.core.contracts.UniqueIdentifier
import org.junit.Test

class WorkbenchAdapterIngressTests {

    private val adapter = WorkbenchAdapterImpl(1)

    @Test
    fun `transforming a non-JSON message fails`() {
        val nonJson = "arrrrghh!!!"

        assertThat(
            { adapter.transformIngress(nonJson) },
            throws(
                isA<IngressFormatException>(
                    has(
                        Exception::message,
                        present(startsWith("com.fasterxml.jackson.core.JsonParseException: Unrecognized token 'arrrrghh'"))
                    )
                )
            )
        )
    }

    @Test
    fun `transforming an empty message fails`() {
        val emptyString = ""

        assertThat(
            { adapter.transformIngress(emptyString) },
            throws(
                isA<IngressFormatException>(
                    has(
                        Exception::message,
                        present(startsWith("No ingress message presented"))
                    )
                )
            )
        )
    }

    @Test
    fun `a request ID is made available even though the message is otherwise nonsensical`() {
        val json = """{
            |   "requestId" : "ea3bcdca-cffb-4122-9025-c96c72db1213",
            |   "xxx" : "yyy"
            |}""".trimMargin()

        assertThat(
            { adapter.transformIngress(json) },
            throws(
                isA<CorrelatableIngressFormatException>(
                    has(
                        Exception::message,
                        equalTo("java.lang.IllegalArgumentException: Unknown message name")
                    )
                )
            )
        )
    }

    @Test
    fun `transforming an unknown message fails`() {
        val json = """{
            |   "requestId" : "7749774d-9bbc-4445-8b11-c801d615ea12",
            |   "messageName" : "NonSensicalRequest"
            |}""".trimMargin()

        assertThat(
            { adapter.transformIngress(json) },
            throws(
                isA<IngressFormatException>(
                    has(
                        Exception::message,
                        equalTo("java.lang.IllegalArgumentException: Unknown message name")
                    )
                )
            )
        )
    }

    @Test
    fun `transforming an invalid CreateContractRequest fails`() {
        val json = """{
        |  "requestId" : "7749774d-9bbc-4445-8b11-c801d615ea12",
        |  "messageName": "CreateContractRequest",
        |  "hocus" : "pocus"
        |}""".trimMargin()

        assertThat(
            { adapter.transformIngress(json) },
            throws(
                isA<IngressFormatException>(
                    has(
                        Exception::message,
                        equalTo("3 schema violations found: #: required key [workflowName] not found, #: required key [parameters] not found, #: required key [messageSchemaVersion] not found")
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

        val actual = adapter.transformIngress(json)

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
        |  "requestId" : "7749774d-9bbc-4445-8b11-c801d615ea12",
        |  "messageName": "CreateContractActionRequest",
        |  "hocus" : "pocus"
        |}""".trimMargin()

        assertThat(
            { adapter.transformIngress(json) },
            throws(
                isA<IngressFormatException>(
                    has(
                        Exception::message,
                        equalTo("4 schema violations found: #: required key [workflowFunctionName] not found, #: required key [contractLedgerIdentifier] not found, #: required key [parameters] not found, #: required key [messageSchemaVersion] not found")
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

        val actual = adapter.transformIngress(json)

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
        |  "requestId" : "7749774d-9bbc-4445-8b11-c801d615ea12",
        |  "messageName": "ReadContractRequest",
        |  "hocus" : "pocus"
        |}""".trimMargin()

        assertThat(
            { adapter.transformIngress(json) },
            throws(
                isA<IngressFormatException>(
                    has(
                        Exception::message,
                        equalTo("2 schema violations found: #: required key [contractLedgerIdentifier] not found, #: required key [messageSchemaVersion] not found")
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

        val actual = adapter.transformIngress(json)

        val expected = QueryFlowState(
            requestId = "9c2e532f-15bb-4eb8-ae58-34722c5776f4",
            linearId = UniqueIdentifier.fromString("3aa6120b-b809-4cdc-9a19-81546482b313")
        )

        @Suppress("RemoveExplicitTypeArguments")
        assertThat(actual, isA<QueryFlowState>(equalTo(expected)))
    }
}
