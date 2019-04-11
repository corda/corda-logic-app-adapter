package com.r3.logicapps

import com.r3.logicapps.processing.MessageProcessor
import com.r3.logicapps.processing.MessageProcessorImpl
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.loggerFor

@CordaService
class LogicAppService(
    private val appServiceHub: AppServiceHub
) : SingletonSerializeAsToken() {

    private val messageProcessor: MessageProcessor = MessageProcessorImpl(
        startFlowDelegate = { flowLogic -> appServiceHub.startTrackedFlow(flowLogic) }
    )

    init {
        initializeService()
        appServiceHub.registerUnloadHandler(::unloadService)
    }

    private fun initializeService() {
        log.info("Starting service")
    }

    private fun unloadService() {
        log.info("Stopping service")
    }

    companion object {
        private val log = loggerFor<LogicAppService>()
    }
}
