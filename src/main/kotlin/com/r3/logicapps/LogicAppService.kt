package com.r3.logicapps

import com.r3.logicapps.processing.MessageProcessor
import com.r3.logicapps.processing.ServiceDrivenMessageProcessor
import com.r3.logicapps.servicebus.ServicebusClient
import com.r3.logicapps.servicebus.ServicebusClientImpl
import com.r3.logicapps.servicebus.ServicebusConnectionService
import com.r3.logicapps.workbench.WorkbenchAdapterImpl
import net.corda.core.internal.uncheckedCast
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.contextLogger
import rx.Subscription

@CordaService
class LogicAppService(
    private val appServiceHub: AppServiceHub
) : SingletonSerializeAsToken() {

    private val messageProcessor: MessageProcessor = ServiceDrivenMessageProcessor(appServiceHub)

    private lateinit var serviceBusClient: ServicebusClient
    private lateinit var serviceBusConnectionService: ServicebusConnectionService

    private var statusSubscriber: Subscription? = null

    init {
        initializeService()

        // Note that the shutdown handler is not guaranteed to be called as the node process may crash or get killed.
        appServiceHub.registerUnloadHandler(::unloadService)
    }

    private fun initializeService() {
        log.info("Starting service")
        val config = appServiceHub.getAppContext().config
        val connectionString: String = if (config.exists("connectionString")) { uncheckedCast(config.get("connectionString")) } else { "" }
        val inboundQueue: String = if (config.exists("inboundQueue")) { uncheckedCast(config.get("inboundQueue")) } else { "" }
        val outboundQueue: String = if (config.exists("outboundQueue")) { uncheckedCast(config.get("outboundQueue")) } else { "" }

        val workbenchAdapter = WorkbenchAdapterImpl(appServiceHub.myInfo.platformVersion)

        serviceBusConnectionService = ServicebusConnectionService(connectionString, inboundQueue, outboundQueue)
        serviceBusConnectionService.start()

        serviceBusClient = ServicebusClientImpl(serviceBusConnectionService)
        serviceBusClient.start()

        statusSubscriber = serviceBusConnectionService.change.subscribe({ ready ->
            if (ready) {
                serviceBusClient.registerReceivedMessageHandler(
                    BusMessageHandler(
                        serviceBusClient,
                        messageProcessor,
                        workbenchAdapter
                    )
                )
            }
        }, { log.error("Error in connection service state change", it) })

    }

    private fun unloadService() {
        log.info("Stopping service")
        statusSubscriber?.unsubscribe()
        statusSubscriber = null
        serviceBusClient.close()
        serviceBusConnectionService.stop()
    }

    companion object {
        private val log = contextLogger()
    }
}
