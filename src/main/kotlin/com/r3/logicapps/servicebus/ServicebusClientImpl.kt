package com.r3.logicapps.servicebus

import com.microsoft.azure.servicebus.*
import com.microsoft.azure.servicebus.primitives.ConnectionStringBuilder
import com.microsoft.azure.servicebus.primitives.RetryExponential
import com.microsoft.azure.servicebus.primitives.ServiceBusException
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class ServicebusClientImpl(private val connectionString: String,
                           private val inboundQueue: String,
                           private val outboundQueue: String,
                           private val threadPool: ExecutorService = Executors.newFixedThreadPool(1)) : ServicebusClient {

    private companion object {
        val log = LoggerFactory.getLogger(javaClass.enclosingClass)
        val clientMode = ReceiveMode.PEEKLOCK
        const val MAX_RETRY_COUNT = Integer.MAX_VALUE //For the time being use a large number until we figure out what to do in case of failure
        const val RETRY_POLICY_NAME = "INSERT_MEANINGFUL_NAME_HERE"
        val MESSAGE_LOCK_RENEW_TIMEOUT: Duration = Duration.ofSeconds(60)
    }

    private val started = AtomicBoolean(false)

    //TODO: Bogdan - perhaps this should be configurable
    //Seems like this policy is used by both senders and receivers to retry failed operations before throwing exceptions.
    private val exponentialRetry = RetryExponential(Duration.ofSeconds(5), Duration.ofMinutes(3), MAX_RETRY_COUNT, RETRY_POLICY_NAME)

    private var sender: QueueClient? = null
    private var receiver: QueueClient? = null
    private var blockingReceiver: IMessageReceiver? = null

    override fun send(message: String) {
        require(started.get()) { "Service bus client should be started before calling send()" }
        log.info("Sending message to ${sender!!.queueName}")
        val serviceBusMessage = Message(message).apply {
            contentType = "application/json"
            //TODO: Bogdan - what could we use label and messageId for?
            //label =
            //messageId = could be used to avoid duplicates in case of failure
        }
        try {
            sender!!.send(serviceBusMessage)
        } catch (e: ServiceBusException) {
            log.error("Message could not be sent to entity", e)
            //TODO: Bogdan - treat failures here or propagate
            //call some other failureHandler?
        } catch (e: InterruptedException) {
            log.error("Sending thread was interrupted", e)
            //TODO: have no clue what happens in this case; perhaps try resend
        }
        log.info("Message sent")
    }

    override fun receive(): String {
        require(started.get()) { "Service bus client should be started before calling receive()" }
        val msg = blockingReceiver!!.receive()
        blockingReceiver!!.complete(msg.lockToken)
        return String(msg.messageBody.binaryData.first(), UTF_8)
    }

    override fun registerReceivedMessageHandler(handler: IMessageHandler) {
        require(started.get()) { "Service bus client should be started before calling registerReceivedMessageHandler()" }
        //TODO: Bogdan - would having several handlers be required in the future?
        //TODO: autoComplete = true means the bus receives an ACK as soon as the message is received, deleting it from the queue. Perhaps
        //TODO: complete() should be called after flow start to avoid loss - THIS NEEDS TESTING!!!!
        receiver!!.registerMessageHandler(handler, MessageHandlerOptions(1, true, MESSAGE_LOCK_RENEW_TIMEOUT), threadPool)
    }

    //TODO: Bogdan - if the retry policy has a finite number of retries, the initial connection could fail, in which case, I think we should throw and
    override fun start() {
        require(!started.get()) { "Service bus client can't be started twice" }
        try {
            sender = QueueClient(ConnectionStringBuilder(connectionString, outboundQueue).apply { retryPolicy = exponentialRetry }, clientMode)
        } catch (e: ServiceBusException) {
            log.error("Connection to $outboundQueue could not be established", e)
        } catch (e: InterruptedException) {
            log.error("Connection attempt to $outboundQueue was interrupted", e)
            //TODO: have no clue what happens in this case
        }

        try {
            receiver = QueueClient(ConnectionStringBuilder(connectionString, inboundQueue).apply { retryPolicy = exponentialRetry }, clientMode)
        } catch (e: ServiceBusException) {
            log.error("Connection to $inboundQueue could not be established", e)
        } catch (e: InterruptedException) {
            log.error("Connection attempt to $inboundQueue was interrupted", e)
            //TODO: have no clue what happens in this case
        }

        try {
            blockingReceiver = ClientFactory.createMessageReceiverFromConnectionStringBuilder(ConnectionStringBuilder(connectionString, inboundQueue).apply { retryPolicy = exponentialRetry }, clientMode)
        } catch (e: ServiceBusException) {
            log.error("Connection to $inboundQueue could not be established", e)
        } catch (e: InterruptedException) {
            log.error("Connection attempt to $inboundQueue was interrupted", e)
            //TODO: have no clue what happens in this case
        }

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
}