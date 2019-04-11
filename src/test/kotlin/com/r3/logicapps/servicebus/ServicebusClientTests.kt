package com.r3.logicapps.servicebus

import com.microsoft.azure.servicebus.ExceptionPhase
import com.microsoft.azure.servicebus.IMessage
import com.microsoft.azure.servicebus.IMessageHandler
import junit.framework.TestCase.fail
import org.junit.Test
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.assertEquals

class ServicebusClientTests {

    companion object {
        const val SERVICE_BUS = "Endpoint=sb://bogdan-logicapp-bus.servicebus.windows.net/;SharedAccessKeyName=RootManageSharedAccessKey;SharedAccessKey=eAIZ5rfIoZQCEeZ9GGcxvjO6m20hCKs9wbzAykAtcSU="
        const val QUEUE1 = "to-corda"
        const val QUEUE2 = "from-corda"
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