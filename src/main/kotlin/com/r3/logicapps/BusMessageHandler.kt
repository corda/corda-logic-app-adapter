package com.r3.logicapps

import com.microsoft.azure.servicebus.ExceptionPhase
import com.microsoft.azure.servicebus.IMessage
import com.microsoft.azure.servicebus.IMessageHandler
import com.r3.logicapps.BusResponse.Error.CorrelatableError
import com.r3.logicapps.BusResponse.Error.GenericError
import com.r3.logicapps.processing.MessageProcessor
import com.r3.logicapps.servicebus.ServicebusClient
import com.r3.logicapps.workbench.CorrelatableIngressFormatException
import com.r3.logicapps.workbench.IngressFormatException
import com.r3.logicapps.workbench.WorkbenchAdapter
import net.corda.core.utilities.contextLogger
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.CompletableFuture

class BusMessageHandler(
    private val busClient: ServicebusClient,
    private val messageProcessor: MessageProcessor,
    private val workbenchAdapter: WorkbenchAdapter
) : IMessageHandler {
    companion object {
        val log = contextLogger()
    }

    override fun onMessageAsync(message: IMessage?): CompletableFuture<Void> {
        val payload = String(message!!.body, StandardCharsets.UTF_8)
        log.info("Received message: $payload")

        try {
            handleRequest(workbenchAdapter.transformIngress(payload), message.lockToken)
        } catch (exception: IngressFormatException) {
            handleError(exception, message.lockToken)
        } finally {
            return CompletableFuture.completedFuture(null)
        }
    }

    private fun handleRequest(request: BusRequest, messageLockTokenId: UUID) {
        messageProcessor.invoke(request, busClient, messageLockTokenId).forEach {
            val response = workbenchAdapter.transformEgress(it)
            log.info("Sending reply: $response")
            busClient.send(response)
        }
    }

    private fun handleError(exception: IngressFormatException, messageLockTokenId: UUID) {
        log.warn("Ingress message couldn't be deserialised", exception.message)
        val error = when (exception) {
            is CorrelatableIngressFormatException -> CorrelatableError(exception, exception.requestId)
            else                                  -> GenericError(exception)
        }
        busClient.acknowledge(messageLockTokenId)
        busClient.send(workbenchAdapter.transformEgress(error))
    }

    override fun notifyException(exception: Throwable?, phase: ExceptionPhase?) {
        log.error("Exception while receiving message in phase $phase", exception)
    }

}