package com.r3.logicapps.servicebus

import com.microsoft.azure.servicebus.*
import com.microsoft.azure.servicebus.primitives.ServiceBusException
import net.corda.core.utilities.contextLogger
import rx.Subscription
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The [threadPool] parameter should be left as default (1 thread) as async message processing is not currently supported
 */
class ServicebusClientImpl(private val busConnectionService: ServicebusConnectionService,
                           private val threadPool: ExecutorService = Executors.newFixedThreadPool(1)) : ServicebusClient {

    private companion object {
        val log = contextLogger()
        val MESSAGE_LOCK_RENEW_TIMEOUT: Duration = Duration.ofSeconds(60)
    }

    private val started = AtomicBoolean(false)

    private var statusSubscriber: Subscription? = null
    private var sender: QueueClient? = null
    private var receiver: QueueClient? = null

    override fun send(message: String) {
        require(started.get()) { "Service bus client should be started before calling send()" }
        log.info("Sending message to ${sender!!.queueName}")
        val serviceBusMessage = Message(message).apply {
            contentType = "application/json"
        }
        try {
            sender!!.send(serviceBusMessage)
        } catch (e: Exception) {
            handleException(e, "Message could not be sent to entity")
        }
        log.info("Message sent")
    }

    override fun registerReceivedMessageHandler(handler: IMessageHandler) {
        require(started.get()) { "Service bus client should be started before calling registerReceivedMessageHandler()" }
        receiver!!.registerMessageHandler(handler, MessageHandlerOptions(1, false, MESSAGE_LOCK_RENEW_TIMEOUT), threadPool)
    }

    override fun acknowledge(lockTokenId: UUID) {
        require(started.get()) {"Service bus client should be started before calling acknowledge()" }
        try {
            receiver!!.complete(lockTokenId)
        } catch (e: Exception) {
            handleException(e, "Message could not be acknowledged and removed from the bus")
        }

    }

    override fun start() {
        require(!started.get()) { "Service bus client can't be started twice" }
        statusSubscriber = busConnectionService.change.subscribe({ ready ->
            if (ready) {
                sender = busConnectionService.senderClient!!
                receiver = busConnectionService.receiverClient!!
            } else {
                // Currently no action is required as the client will be shutdown gracefully at this point
            }
        }, { log.error("Error in connection service state change", it) }
        )

        started.set(true)
    }

    override fun close() {
        require(started.get()) {"Service bus client can't be stopped twice"}
        try {
            sender?.close()
        } catch (e: ServiceBusException) {
            log.error("Could not close sender client", e)
        } finally {
            sender = null
        }
        try {
            receiver?.close()
        } catch (e: ServiceBusException) {
            log.error("Could not close receiver client", e)
        } finally {
            receiver = null
        }

        statusSubscriber?.unsubscribe()
        statusSubscriber = null

        started.set(false)
    }

    private fun handleException(e: Exception, msg: String) {
        when (e) {
            is ServiceBusException -> {
                log.error(msg, e)
                if (!e.isTransient) {
                    // Non transient error means the service bus will not be available anytime soon
                    log.error("Non transient error. Node shutting down")
                    System.exit(1)
                }
            }

            is InterruptedException -> {
                log.warn("Service bus client action thread was interrupted")
            }

            else -> {
                log.error("Unknown error", e)
            }
        }
    }
}