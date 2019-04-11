package com.r3.logicapps.processing

import net.corda.core.node.AppServiceHub
import net.corda.core.utilities.getOrThrow

class ServiceDrivenMessageProcessor(appServiceHub: AppServiceHub) : MessageProcessorImpl(
    startFlowDelegate = { flowLogic ->
        val handle = appServiceHub.startFlow(flowLogic)
        try {
            val result = handle.returnValue.getOrThrow()
            TODO()
            FlowInvocationResult()
        } catch (exception: Throwable) {
            FlowInvocationResult(exception = exception)
        }
    }
)