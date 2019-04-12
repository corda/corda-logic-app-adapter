package com.r3.logicapps.cordapp

import com.microsoft.azure.servicebus.ExceptionPhase
import com.microsoft.azure.servicebus.IMessage
import com.microsoft.azure.servicebus.IMessageHandler
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.r3.logicapps.TestBase
import com.r3.logicapps.servicebus.ServicebusClient
import com.r3.logicapps.servicebus.ServicebusClientImpl
import com.r3.logicapps.servicebus.ServicebusClientTests
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

class CreateFlowTests : TestBase() {

    @Test
    fun `bla`() = withDriver {
        val (alice) = startNodes("Alice".toIdentity())
        assertThat(alice.nodeInfo.platformVersion, equalTo(4))

        val count = AtomicInteger(0)
        val client = ServicebusClientImpl(
            ServicebusClientTests.SERVICE_BUS,
            inboundQueue = ServicebusClientTests.FROM_CORDA_QUEUE,
            outboundQueue = ServicebusClientTests.TO_CORDA_QUEUE
        )
        client.start()
        client.registerReceivedMessageHandler(TestMessageHandler(client, count))
        client.send("""{
        |  "messageName": "CreateContractRequest",
        |  "requestId": "81a87eb0-b5aa-4d53-a39f-a6ed0742d90d",
        |  "workflowName": "net.corda.logicapps.refrigeratedTransportation.flow.CreateFlow",
        |  "parameters": [
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
        |}""".trimMargin())
        while (count.get() < 1) {
            Thread.sleep(100)
        }
        client.close()
    }

    class TestMessageHandler(val client: ServicebusClient, private val count: AtomicInteger) : IMessageHandler {
        override fun onMessageAsync(message: IMessage?): CompletableFuture<Void> {
            count.addAndGet(1)
            println("Received:\n${message?.body?.toString(StandardCharsets.UTF_8)}\n")
            return CompletableFuture.completedFuture(null)
        }

        override fun notifyException(exception: Throwable?, phase: ExceptionPhase?) {
            println("Error: $exception")
            return
        }
    }
}