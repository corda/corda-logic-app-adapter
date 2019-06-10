package com.r3.logicapps.servicebus

import com.microsoft.azure.servicebus.ExceptionPhase
import com.microsoft.azure.servicebus.IMessage
import com.microsoft.azure.servicebus.IMessageHandler
import com.r3.logicapps.TestBase
import junit.framework.TestCase.fail
import net.corda.core.utilities.getOrThrow
import org.junit.Ignore
import org.junit.Test
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class ServicebusClientTests : TestBase() {

    private val partyA = "PartyA".toIdentity()
    private val partyB = "PartyB".toIdentity()

    companion object {
        // A service bus connection string allowing the user to write to the bus
        val SERVICE_BUS: String = TODO()

        // The name of the queue on which Corda puts messages
        val FROM_CORDA_QUEUE: String = TODO()

        // The name of the queue from which Corda reads messages
        val TO_CORDA_QUEUE: String = TODO()
    }

    @Test(timeout = 10000)
    @Ignore("This test requires access to a service bus")
    fun `test send-receive`() {
        val threadA = thread {
            val msg = "{ping: pong}"
            val count = AtomicInteger(0)
            val connectionService = ServicebusConnectionService(SERVICE_BUS, inboundQueue = FROM_CORDA_QUEUE, outboundQueue = TO_CORDA_QUEUE)
            connectionService.start()
            val client = ServicebusClientImpl(connectionService)
            client.start()
            val statusSubscriber = connectionService.change.subscribe({ ready ->
                if (ready) {
                    client.registerReceivedMessageHandler(MyMessageHandler("ClientA", client, count) { e -> fail(e?.message) })
                    //start the ping-pong
                    client.send(msg)
                }
            }, { fail("Error in connection service state change") })

            while (count.get() < 10) {}
            statusSubscriber.unsubscribe()
            client.close()
            connectionService.stop()
        }

        val threadB = thread {
            val count = AtomicInteger(0)
            val connectionService = ServicebusConnectionService(SERVICE_BUS, inboundQueue = TO_CORDA_QUEUE, outboundQueue = FROM_CORDA_QUEUE)
            connectionService.start()
            val client = ServicebusClientImpl(connectionService)
            client.start()
            val statusSubscriber = connectionService.change.subscribe({ ready ->
                if (ready) {
                    client.registerReceivedMessageHandler(MyMessageHandler("ClientB", client, count) { e -> fail(e?.message) })
                }
            }, { fail("Error in connection service state change") })
            //start the ping-pong
            while (count.get() < 10) {}
            statusSubscriber.unsubscribe()
            client.close()
            connectionService.stop()
        }

        threadA.join()
        threadB.join()
    }

    @Test(timeout = 120000)
    @Ignore("This test requires access to a service bus")
    fun `node consumes and replies`() = withDriver {
        val message = "{\n" +
                "          \"messageName\": \"CreateContractRequest\",\n" +
                "          \"requestId\": \"81a87eb0-b5aa-4d53-a39f-a6ed0742d90d\",\n" +
                "          \"workflowName\": \"net.corda.workbench.refrigeratedTransportation.flow.CreateFlow\",\n" +
                "          \"parameters\": [\n" +
                "            {\n" +
                "              \"name\": \"state\",\n" +
                "              \"value\": \"Created\"\n" +
                "            },\n" +
                "            {\n" +
                "              \"name\": \"owner\",\n" +
                "              \"value\": \"O=Alice Ltd., L=Shanghai, C=CN\"\n" +
                "            },\n" +
                "            {\n" +
                "              \"name\": \"initiatingCounterparty\",\n" +
                "              \"value\": \"O=Bob Ltd., L=Beijing, C=CN\"\n" +
                "            },\n" +
                "            {\n" +
                "              \"name\": \"device\",\n" +
                "              \"value\": \"O=Charly GmbH, OU=Device01, L=Berlin, C=DE\"\n" +
                "            },\n" +
                "            {\n" +
                "              \"name\": \"supplyChainOwner\",\n" +
                "              \"value\": \"O=Denise SARL, L=Marseille, C=FR\"\n" +
                "            },\n" +
                "            {\n" +
                "              \"name\": \"supplyChainObserver\",\n" +
                "              \"value\": \"O=Denise SARL, L=Marseille, C=FR\"\n" +
                "            },\n" +
                "            {\n" +
                "              \"name\": \"minHumidity\",\n" +
                "              \"value\": \"12\"\n" +
                "            },\n" +
                "            {\n" +
                "              \"name\": \"maxHumidity\",\n" +
                "              \"value\": \"45\"\n" +
                "            },\n" +
                "            {\n" +
                "              \"name\": \"minTemperature\",\n" +
                "              \"value\": \"-20\"\n" +
                "            },\n" +
                "            {\n" +
                "              \"name\": \"maxTemperature\",\n" +
                "              \"value\": \"-7\"\n" +
                "            }\n" +
                "          ],\n" +
                "          \"messageSchemaVersion\": \"1.0.0\"\n" +
                "        }"
        //TODO: update once implementation of message processor is finished
        val expected = "{\n" +
                "\t\"messageName\" : \"CreateContractRequest\",\n" +
                "\t\"requestId\" : \"81a87eb0-b5aa-4d53-a39f-a6ed0742d90d\",\n" +
                "\t\"additionalInformation\" : {\n" +
                "\t\t\"errorCode\" : 1426288842,\n" +
                "\t\t\"errorMessage\" : \"Unable to find 'net.corda.workbench.refrigeratedTransportation.flow.CreateFlow' on the class path\"\n" +
                "\t},\n" +
                "\t\"status\" : \"Failure\",\n" +
                "\t\"messageSchemaVersion\" : \"1.0.0\"\n" +
                "}"
        val latch = CountDownLatch(1)
        val connectionService = ServicebusConnectionService(SERVICE_BUS, FROM_CORDA_QUEUE, TO_CORDA_QUEUE)
        connectionService.start()
        val client = ServicebusClientImpl(connectionService)
        client.start()
        val subscriber = connectionService.change.subscribe {
            if (it) {
                client.registerReceivedMessageHandler(object : IMessageHandler {
                    override fun onMessageAsync(message: IMessage?): CompletableFuture<Void> {
                        println("Received from Corda: ${String(message!!.body, UTF_8)}")
                        if (expected == String(message!!.body, UTF_8))
                            latch.countDown()
                        return CompletableFuture.completedFuture(null)
                    }

                    override fun notifyException(exception: Throwable?, phase: ExceptionPhase?) {
                        println(exception)
                    }

                })
                client.send(message)
            }
        }

        startNode(providedName = partyA.name, customOverrides = mapOf("cordappSignerKeyFingerprintBlacklist" to emptyList<String>(),
            "devMode" to true)).getOrThrow()
        latch.await()
        subscriber.unsubscribe()
        client.close()
        connectionService.stop()
    }

    class MyMessageHandler(val id: String, val client: ServicebusClient, val count: AtomicInteger, val errorHandler: (e: Throwable?) -> Any?) : IMessageHandler {
        override fun onMessageAsync(message: IMessage?): CompletableFuture<Void> {
            println("Client $id received message. Current count is ${count.get()}" )
            count.set(count.get() + 1)
            if (count.get() <= 10) {
                println("Client $id sending message with count ${count.get()}")
                client.send(String(message!!.body, UTF_8))
            }
            client.acknowledge(message!!.lockToken)
            return CompletableFuture.completedFuture(null)
        }

        override fun notifyException(exception: Throwable?, phase: ExceptionPhase?) {
            errorHandler(exception)
        }
    }
}