package com.r3.logicapps.workbench

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.isA
import com.natpryce.hamkrest.throws
import com.r3.logicapps.RPCRequest.FlowInvocationRequest
import org.junit.Test

class ValidatingWorkbenchAdapterTest {

    @Test
    fun `transforming unknown messages fails`() {
        val json = """{
            |   "messageName" : "NonSensicalRequest"
            |}""".trimMargin()

        assertThat(
            { ValidatingWorkbenchAdapter().transformIngress(json) },
            throws(isA<IllegalArgumentException>(has(Exception::message, equalTo("Unknown message name"))))
        )
    }

    @Test
    fun `transforming an invalid "CreateContractRequest" fails`() {
        val json = """{
        |  "messageName": "CreateContractRequest",
        |  "hocus" : "pocus"
        |}""".trimMargin()

        assertThat(
            { ValidatingWorkbenchAdapter().transformIngress(json) },
            throws(
                isA<IllegalArgumentException>(
                    has(
                        Exception::message,
                        equalTo("Flow invocation message: #: 4 schema violations found")
                    )
                )
            )
        )
    }

    @Test
    fun `transforms a valid "CreateContractRequest"`() {
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

        val actual = ValidatingWorkbenchAdapter().transformIngress(json)

        val expected = FlowInvocationRequest(
            "81a87eb0-b5aa-4d53-a39f-a6ed0742d90d",
            "net.corda.workbench.refrigeratedTransportation.flow.CreateFlow",
            mapOf(
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
        assertThat(actual, isA<FlowInvocationRequest>(equalTo(expected)))
    }
}