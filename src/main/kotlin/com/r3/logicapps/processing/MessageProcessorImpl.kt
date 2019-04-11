package com.r3.logicapps.processing

import com.r3.logicapps.BusRequest
import com.r3.logicapps.BusResponse
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.messaging.FlowProgressHandle

class MessageProcessorImpl(
    private val startFlowDelegate: (FlowLogic<*>) -> Any?
) : MessageProcessor {
    override fun invoke(message: BusRequest): BusResponse = when (message) {
        is BusRequest.InvokeFlowWithoutInputStates -> {
            val linearId = UniqueIdentifier("")
            val fields = emptyMap<String, String>()
            // TODO startFlowDelegate(), obtain transaction ID and output state
            try {
                BusResponse.FlowOutput(message::class, message.requestId, linearId, fields, true)
            } catch (exception: Throwable) {
                BusResponse.FlowError(message::class, message.requestId, exception)
            } as BusResponse
        }
        is BusRequest.InvokeFlowWithInputStates -> {
            val linearId = UniqueIdentifier("")
            val fields = emptyMap<String, String>()
            // TODO startFlowDelegate(), obtain transaction ID and output state
            try {
                BusResponse.FlowOutput(message::class, message.requestId, linearId, fields, false)
            } catch (exception: Throwable) {
                BusResponse.FlowError(message::class, message.requestId, exception)
            } as BusResponse
        }
        is BusRequest.QueryFlowState -> {
            // TODO Query vault
            BusResponse.FlowError(message::class, message.requestId, NotImplementedError("QueryFlowState not implemented"))
        }
    }
}