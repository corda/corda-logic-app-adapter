package com.r3.logicapps

import com.microsoft.azure.servicebus.ExceptionPhase
import com.microsoft.azure.servicebus.IMessage
import com.microsoft.azure.servicebus.IMessageHandler
import com.r3.logicapps.BusRequest.InvokeFlowWithoutInputStates
import com.r3.logicapps.BusResponse.FlowOutput
import com.r3.logicapps.processing.MessageProcessor
import com.r3.logicapps.servicebus.ServicebusClient
import com.r3.logicapps.servicebus.ServicebusMessage
import com.r3.logicapps.workbench.WorkbenchAdapter
import net.corda.core.contracts.UniqueIdentifier
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.CompletableFuture

object Demo {
    val dummyAdapter = object : WorkbenchAdapter {
        override fun transformIngress(message: ServicebusMessage): BusRequest {
            // determine type and convert to BusRequest object
            return InvokeFlowWithoutInputStates("id", "name", mapOf("par" to "ams"))
        }

        override fun transformEgress(message: BusResponse): ServicebusMessage {
            // get message and translate to Workbench JSON
            return "response from corda"
        }
    }

    val dummyMessageProcessor = object : MessageProcessor {
        override fun invoke(message: BusRequest): BusResponse {
            // call corda with request
            return FlowOutput(
                    message::class,
                    "id",
                    UniqueIdentifier.fromString(UUID.randomUUID().toString()),
                    mapOf(),
                    true
                )
        }
    }

    val dummyClient = object : ServicebusClient {
        override fun start() {
            // connect to service bus
        }

        override fun close() {
            // close connections to service bus; should be called by corda service unload handler
        }

        override fun send(message: String) {
            // send using the internal QueueClient instance
        }

        override fun registerReceivedMessageHandler(handler: IMessageHandler) {
            // register handler that will run in a separate thread
        }

    }

    val dummyHandler = object : IMessageHandler {
        override fun onMessageAsync(message: IMessage?): CompletableFuture<Void> {
            val payload = String(message!!.body, StandardCharsets.UTF_8)
            val busRequest = dummyAdapter.transformIngress(payload)
            val response = dummyAdapter.transformEgress(dummyMessageProcessor.invoke(busRequest))
            dummyClient.send(response)
            return CompletableFuture.completedFuture(null)
        }

        override fun notifyException(exception: Throwable?, phase: ExceptionPhase?) {
        }

    }

    @JvmStatic
    fun main(args: Array<String>) {
        // end to end
        dummyClient.start()
        dummyClient.registerReceivedMessageHandler(dummyHandler)
    }
}