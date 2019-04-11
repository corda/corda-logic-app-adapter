package com.r3.logicapps.processing

import com.r3.logicapps.BusRequest
import com.r3.logicapps.BusResponse
import net.corda.core.flows.FlowLogic
import net.corda.core.messaging.FlowProgressHandle

class MessageProcessorImpl(
    private val startFlowDelegate: (FlowLogic<*>) -> FlowProgressHandle<*>
) : MessageProcessor {
    /**
     * TODO
     *  - Invoke RPC using the message parameters
     *  - Obtain the transaction ID
     *  - Obtain the output state
     */
    override fun invoke(message: BusRequest): BusResponse? = when (message) {
        is BusRequest.InvokeFlowWithoutInputStates -> {
            null
        }
        is BusRequest.InvokeFlowWithInputStates -> {
            null
        }
        is BusRequest.QueryFlowState -> {
            null
        }
    }
}