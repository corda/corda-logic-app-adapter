package com.r3.logicapps.servicebus

import com.microsoft.azure.servicebus.*
import com.microsoft.azure.servicebus.primitives.ConnectionStringBuilder
import com.microsoft.azure.servicebus.primitives.IllegalConnectionStringFormatException
import com.microsoft.azure.servicebus.primitives.RetryExponential
import com.microsoft.azure.servicebus.primitives.ServiceBusException
import net.corda.core.utilities.contextLogger
import java.lang.IllegalArgumentException
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The [threadPool] parameter should be left as default (1 thread) as async message processing is not currently supported
 */
class ServicebusClientImpl(private val connectionString: String,
                           private val inboundQueue: String,
                           private val outboundQueue: String,
                           private val threadPool: ExecutorService = Executors.newFixedThreadPool(1)) : ServicebusClient {

    private companion object {
        val log = contextLogger()
        val clientMode = ReceiveMode.PEEKLOCK
        const val MAX_RETRY_COUNT = Integer.MAX_VALUE //For the time being use a large number until we figure out what to do in case of failure
        const val RETRY_POLICY_NAME = "exponential retry policy"
        val MESSAGE_LOCK_RENEW_TIMEOUT: Duration = Duration.ofSeconds(60)
    }

    private val started = AtomicBoolean(false)

    // TODO: Bogdan - perhaps this should be configurable
    // Seems like this policy is used by both senders and receivers to retry failed operations before throwing exceptions.
    private val exponentialRetry = RetryExponential(Duration.ofSeconds(5), Duration.ofMinutes(3), MAX_RETRY_COUNT, RETRY_POLICY_NAME)

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
        } catch (e: ServiceBusException) {
            // With current retry policy this should rarely be thrown, but if it is, message will be discarded which is ok for now as
            // the service app only sends messages as replies to incoming bus requests
            log.error("Message could not be sent to entity", e)
        } catch (e: InterruptedException) {
            log.error("Sending thread was interrupted", e)
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
            log.error("Message could not be acknowledged and removed from the bus", e)
        }

    }

    override fun start() {
        require(!started.get()) { "Service bus client can't be started twice" }
        sender = connect(connectionString, outboundQueue)
        receiver = connect(connectionString, inboundQueue)
        started.set(true)
    }

    override fun close() {
        require(started.get()) {"Service bus client can't be stopped twice"}
        try {
            sender!!.close()
        } catch (e: ServiceBusException) {
            log.error("Could not close sender client", e)
        } finally {
            sender = null
        }
        try {
            receiver!!.close()
        } catch (e: ServiceBusException) {
            log.error("Could not close receiver client", e)
        } finally {
            receiver = null
        }

        started.set(false)
    }

    private fun connect(connectionString: String, queueName: String): QueueClient {
        var reconnectInterval = 1000L
        while (true) {
            try {
                return QueueClient(ConnectionStringBuilder(connectionString, queueName).apply {
                    retryPolicy = exponentialRetry
                }, clientMode)
            } catch (e: Exception) {
                when (e) {
                    is ServiceBusException -> {
                        // TODO: Bogdan - finish implementation of background retry logic in [ServiceBusConnectionService]
                        // log.error("Connection to $queueName could not be established. Retrying in $reconnectInterval ms")
                        log.error("Connection to $queueName could not be established. Shutting down")
                    }
                    is IllegalArgumentException, is IllegalConnectionStringFormatException -> {
                        log.error("Service bus connection details are invalid. connectionString=$connectionString queueName=$queueName. Shutting down")
                    }
                }
                // Retrying to connect in this thread seems to prevent a polite shutdown of the node, so we just kill it for now
                System.exit(1)
            }
            try {
                Thread.sleep(reconnectInterval)
            } catch (e: InterruptedException) {
                log.warn("Service bus reconnection thread was interrupted")
            }

            reconnectInterval = Math.min(2L * reconnectInterval, 60000)
        }
    }
}