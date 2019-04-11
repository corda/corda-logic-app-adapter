package com.r3.logicapps.servicebus

import com.microsoft.azure.servicebus.ExceptionPhase
import com.microsoft.azure.servicebus.IMessage
import com.microsoft.azure.servicebus.IMessageHandler
import com.r3.logicapps.TestBase
import junit.framework.TestCase.fail
import net.corda.core.utilities.getOrThrow
import org.junit.Test
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.assertEquals

class ServicebusClientTests : TestBase() {

    private val partyA = "PartyA".toIdentity()
    private val partyB = "PartyB".toIdentity()

    companion object {
        const val SERVICE_BUS = "Endpoint=sb://bogdan-logicapp-bus.servicebus.windows.net/;SharedAccessKeyName=RootManageSharedAccessKey;SharedAccessKey=eAIZ5rfIoZQCEeZ9GGcxvjO6m20hCKs9wbzAykAtcSU="
        const val QUEUE1 = "from-corda"
        const val QUEUE2 = "to-corda"
    }

    @Test(timeout = 10000)
    fun `test send-receive`() {
        val threadA = thread {
            val msg = "{ping: pong}"
            val count = AtomicInteger(0)
            val client = ServicebusClientImpl(SERVICE_BUS, inboundQueue = QUEUE1, outboundQueue = QUEUE2)
            client.start()
            client.registerReceivedMessageHandler(MyMessageHandler("ClientA", client, count) { e -> fail(e?.message) })
            //start the ping-pong
            client.send(msg)
            while (count.get() < 10) {}
            client.close()
        }

        val threadB = thread {
            val count = AtomicInteger(0)
            val client = ServicebusClientImpl(SERVICE_BUS, inboundQueue = QUEUE2, outboundQueue = QUEUE1)
            client.start()
            client.registerReceivedMessageHandler(MyMessageHandler("ClientB", client, count) { e -> fail(e?.message) })
            //start the ping-pong
            while (count.get() < 10) {}
            client.close()
        }

        threadA.join()
        threadB.join()
    }

    @Test
    fun `test blocking consumer`() {
        val clientA = ServicebusClientImpl(SERVICE_BUS, inboundQueue = QUEUE1, outboundQueue = QUEUE2)
        val clientB = ServicebusClientImpl(SERVICE_BUS, inboundQueue = QUEUE2, outboundQueue = QUEUE1)

        clientA.start()
        clientB.start()

        val consumerThread = thread {
            assertEquals("{test: test}", clientB.receive())
        }

        clientA.send("{test: test}")
        consumerThread.join()
    }

    @Test(timeout = 60000)
    fun `node consumes and replies`() = withDriver {
        //produce a message
        val message = "{\n" +
                "  \"messageName\": \"CreateContractRequest\",\n" +
                "  \"requestId\": \"81a87eb0-b5aa-4d53-a39f-a6ed0742d90d\",\n" +
                "  \"workflowName\": \"net.corda.workbench.refrigeratedTransportation.flow.CreateFlow\",\n" +
                "  \"parameters\": [\n" +
                "    {\n" +
                "      \"name\": \"state\",\n" +
                "      \"value\": \"Created\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"owner\",\n" +
                "      \"value\": \"O=Alice Ltd., L=Shanghai, C=CN\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"initiatingCounterparty\",\n" +
                "      \"value\": \"O=Bob Ltd., L=Beijing, C=CN\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"device\",\n" +
                "      \"value\": \"O=Charly GmbH, OU=Device01, L=Berlin, C=DE\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"supplyChainOwner\",\n" +
                "      \"value\": \"O=Denise SARL, L=Marseille, C=FR\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"supplyChainObserver\",\n" +
                "      \"value\": \"O=Denise SARL, L=Marseille, C=FR\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"minHumidity\",\n" +
                "      \"value\": \"12\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"maxHumidity\",\n" +
                "      \"value\": \"45\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"minTemperature\",\n" +
                "      \"value\": \"-20\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"maxTemperature\",\n" +
                "      \"value\": \"-7\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"messageSchemaVersion\": \"1.0.0\"\n" +
                "}"
        //TODO: update once implementation of message processor is finished
        val expected = "{\n" +
                "  \"messageName\" : \"CreateContractRequest\",\n" +
                "  \"requestId\" : \"81a87eb0-b5aa-4d53-a39f-a6ed0742d90d\",\n" +
                "  \"additionalInformation\" : {\n" +
                "    \"errorMessage\" : \"Unable to find 'net.corda.workbench.refrigeratedTransportation.flow.CreateFlow' on the class path\"\n" +
                "  },\n" +
                "  \"status\" : \"Failure\",\n" +
                "  \"messageSchemaVersion\" : \"1.0.0\"\n" +
                "}"
        val client = ServicebusClientImpl(SERVICE_BUS, QUEUE1, QUEUE2)
        client.start()
        client.send(message)
        startNode(providedName = partyA.name, customOverrides = mapOf("cordappSignerKeyFingerprintBlacklist" to emptyList<String>(),
            "devMode" to true)).getOrThrow()
        assertEquals(expected, client.receive())
    }

    class MyMessageHandler(val id: String, val client: ServicebusClient, val count: AtomicInteger, val errorHandler: (e: Throwable?) -> Any?) : IMessageHandler {
        override fun onMessageAsync(message: IMessage?): CompletableFuture<Void> {
            println("Client $id received message. Current count is ${count.get()}" )
            count.set(count.get() + 1)
            if (count.get() <= 10) {
                println("Client $id sending message with count ${count.get()}")
                client.send(String(message!!.messageBody.binaryData.first(), UTF_8))
            }
            return CompletableFuture.completedFuture(null)
        }

        override fun notifyException(exception: Throwable?, phase: ExceptionPhase?) {
            errorHandler(exception)
        }
    }
}