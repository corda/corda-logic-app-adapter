package com.r3.logicapps.rpc

import com.r3.logicapps.RPCRequest
import com.r3.logicapps.RPCResponse
import net.corda.core.flows.FlowLogic
import net.corda.core.messaging.FlowProgressHandle

class RPCInvokerImpl(
    private val startFlowDelegate: (FlowLogic<*>) -> FlowProgressHandle<*>
) : RPCInvoker {
    /**
     * TODO
     *  - Invoke RPC using the message parameters
     *  - Obtain the transaction ID
     *  - Obtain the output state
     */
    override fun invoke(message: RPCRequest): RPCResponse? = when (message) {
        is RPCRequest.InvokeFlowWithoutInputStates -> {
            null
        }
        is RPCRequest.InvokeFlowWithInputStates -> {
            null
        }
        is RPCRequest.QueryFlowState -> {
            null
        }
    }
}