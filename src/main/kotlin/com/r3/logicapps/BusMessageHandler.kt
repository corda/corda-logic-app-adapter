package com.r3.logicapps

import com.microsoft.azure.servicebus.ExceptionPhase
import com.microsoft.azure.servicebus.IMessage
import com.microsoft.azure.servicebus.IMessageHandler
import com.r3.logicapps.processing.MessageProcessor
import com.r3.logicapps.servicebus.ServicebusClient
import com.r3.logicapps.workbench.WorkbenchAdapterImpl
import net.corda.core.utilities.contextLogger
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture

class BusMessageHandler(
    private val busClient: ServicebusClient,
    private val messageProcessor: MessageProcessor
) : IMessageHandler {
    companion object {
        val log = contextLogger()
    }

    override fun onMessageAsync(message: IMessage?): CompletableFuture<Void> {
        // At this point, the client has already signalled the bus that the message is acknowledged and will
        // be removed from the queue, preventing redelivery should anything happen between this point and the first checkpoint
        // Consider ACK-ing the messages on flow completion
        // TODO: Bogdan - find out how to trigger client.complete(messageLockId) on first flow checkpoint to prevent losses
        // Perhaps ACK the messages also when message processing fails due to invalid messages
        val payload = String(message!!.body, StandardCharsets.UTF_8)
        log.info("Received message: $payload")
        val busRequest = WorkbenchAdapterImpl.transformIngress(payload)
        val messages = messageProcessor.invoke(busRequest)

        messages.forEach {
            val response = WorkbenchAdapterImpl.transformEgress(it)
            log.info("Sending reply: $response")
            busClient.send(response)
        }

        return CompletableFuture.completedFuture(null)
    }

    override fun notifyException(exception: Throwable?, phase: ExceptionPhase?) {
        log.error("Exception while receiving message in phase $phase", exception)
    }

}