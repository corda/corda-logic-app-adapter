package com.r3.logicapps.servicebus

import com.microsoft.azure.servicebus.QueueClient
import com.microsoft.azure.servicebus.ReceiveMode
import com.microsoft.azure.servicebus.primitives.ConnectionStringBuilder
import com.microsoft.azure.servicebus.primitives.RetryPolicy
import com.microsoft.azure.servicebus.primitives.ServiceBusException
import net.corda.core.internal.ThreadBox
import net.corda.core.utilities.contextLogger
import java.lang.Math.min
import java.util.concurrent.CountDownLatch


// TODO: Bogdan - Service that should be used to establish initial connection to the service bus.
// It's currently WIP as it needs to be able to allow the LogicAppService to subscribe. Upon receiving
// notification of successful connection, the LogicAppService would then register the message handler
// A separate thread is used for creating a connection to not block the main thread of the LogicAppService and prevent
// the node from being shut down politely
class ServicebusConnectionService(val bus: String,
                                  val queue: String,
                                  val policy: RetryPolicy,
                                  val clientMode: ReceiveMode) {
    companion object {
        val log = contextLogger()
    }

    private class InnerState {
        var running = false
        var client: QueueClient? = null
        var connectThread: Thread? = null
    }

    private val state = ThreadBox(InnerState())

    fun start() {
        state.locked {
            require(!running) { "Start can't be called more than once" }
            running = true
            connectThread = Thread({ reconnectLoop() }, "Bus queue $queue connector thread").apply { isDaemon = true}
            connectThread!!.start()
        }
    }

    fun stop() {
        val connectThread = state.locked {
            if (running) {
                log.info("Shutting down connection to $queue")
                running = false
                client?.close()
                connectThread
            } else null
        }
        connectThread?.interrupt()
        connectThread?.join(60000)
    }

    private fun reconnectLoop() {
        var reconnectInterval = 1000L
        while (state.locked { running }) {
            log.info("Trying to connect to $queue")
            val latch = CountDownLatch(1)
            var newClient: QueueClient? = null
            try {
                newClient = QueueClient(ConnectionStringBuilder(bus, queue).apply { retryPolicy = policy }, clientMode)
            } catch (e: ServiceBusException) {
                log.error("Connection to $queue could not be established", e)
                latch.countDown()
            } catch (e: InterruptedException) {
                log.error("Connection attempt to $queue was interrupted", e)
            }
            state.locked { client = newClient }
            latch.await()
            state.locked {
                client?.close()
                client = null
            }

            try {
                Thread.sleep(reconnectInterval)
            } catch (e: InterruptedException) {
                log.warn("Service bus reconnection thread was interrupted")
            }

            reconnectInterval = min(2L * reconnectInterval, 60000)
        }
        log.info("Ended service bus reconnection thread")
    }
}