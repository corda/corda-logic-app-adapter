package com.r3.logicapps.servicebus

import com.microsoft.azure.servicebus.QueueClient
import com.microsoft.azure.servicebus.ReceiveMode
import com.microsoft.azure.servicebus.primitives.*
import net.corda.core.internal.ThreadBox
import net.corda.core.utilities.contextLogger
import rx.Observable
import rx.subjects.BehaviorSubject
import java.lang.IllegalArgumentException
import java.lang.Math.min
import java.time.Duration
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class ServicebusConnectionService(val bus: String,
                                  val inboundQueue: String,
                                  val outboundQueue: String) : ServiceState {
    companion object {
        val log = contextLogger()
        const val MAX_RETRY_COUNT = 10
        val clientMode = ReceiveMode.PEEKLOCK
        const val RETRY_POLICY_NAME = "exponential retry policy"
    }

    private class InnerState {
        var running = false
        var senderClient: QueueClient? = null
        var receiverClient: QueueClient? = null
        var connectThread: Thread? = null
    }

    private val exponentialRetry = RetryExponential(Duration.ofSeconds(5), Duration.ofMinutes(3), MAX_RETRY_COUNT, RETRY_POLICY_NAME)

    private var lock = ReentrantLock()
    private val state = ThreadBox(InnerState())
    private var _active: Boolean = false
    private var _change: BehaviorSubject<Boolean> = BehaviorSubject.create<Boolean>(false)
    private val _observable: Observable<Boolean> = _change.serialize().distinctUntilChanged()

    val senderClient: QueueClient?
        get() = state.locked { senderClient }

    val receiverClient: QueueClient?
        get() = state.locked { receiverClient }

    override var active: Boolean
        get() = lock.withLock { _active }
        set(value) {
            if (value != _active) {
                log.info("Status change to $value")
                _change.onNext(value)
            }
        }

    override val change: Observable<Boolean>
        get() = _observable

    override fun start() {
        state.locked {
            require(!running) { "Start can't be called more than once" }
            running = true
            connectThread = Thread({ reconnectLoop() }, "Bus connector thread").apply { isDaemon = true}
            connectThread!!.start()
        }
    }

    override fun stop() {
        val connectThread = state.locked {
            if (running) {
                log.info("Shutting down bus connections")
                running = false
                active = false
                senderClient?.close()
                receiverClient?.close()
                connectThread
            } else null
        }
        connectThread?.interrupt()
        connectThread?.join(60000)
    }

    private fun reconnectLoop() {
        var reconnectInterval = 1000L
        var tryCount = 0
        while (state.locked { running } && tryCount < MAX_RETRY_COUNT) {
            tryCount++
            log.info("Trying to connect to the service bus. Attempt $tryCount")
            val newInboundClient = connect(bus, inboundQueue)
            val newOutBoundClient = connect(bus, outboundQueue)
            if (newInboundClient == null || newOutBoundClient == null) {
                try {
                    Thread.sleep(reconnectInterval)
                } catch (e: InterruptedException) {
                    log.warn("Service bus reconnection thread was interrupted")
                }
                reconnectInterval = min(2L * reconnectInterval, 60000)
                continue
            }

            state.locked {
                senderClient = newOutBoundClient
                receiverClient = newInboundClient
            }
            active = true
            break
        }
    }

    private fun connect(connectionString: String, queueName: String): QueueClient? {
        return try {
            QueueClient(ConnectionStringBuilder(connectionString, queueName).apply { retryPolicy = exponentialRetry },
                clientMode
            )
        } catch (e: Exception) {
            when (e) {
                is ServiceBusException -> {
                    log.error("Connection to $queueName could not be established. Retrying")
                    null
                }

                is InterruptedException -> {
                    log.error("Connection attempt to $queueName was interrupted")
                    null
                }

                is IllegalArgumentException, is IllegalConnectionStringFormatException -> {
                    log.error("Service bus connection details are invalid. connectionString=$connectionString queueName=$queueName. Shutting down")
                    System.exit(1)
                    null
                }

                else -> {
                    null
                }
            }
        }
    }
}