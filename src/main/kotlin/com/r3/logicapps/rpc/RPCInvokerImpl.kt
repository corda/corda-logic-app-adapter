package com.r3.logicapps.rpc

import com.r3.logicapps.RPCRequest
import com.r3.logicapps.RPCResponse
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.node.AppServiceHub

class RPCInvokerImpl(private val appServiceHub: AppServiceHub): RPCInvoker {
    override fun invoke(message: RPCRequest): RPCResponse {
        /**
         * TODO
         *  - Invoke RPC using the message parameters
         *  - Obtain the transaction ID
         *  - Obtain the output state
         */
        return RPCResponse.FlowOutput("", UniqueIdentifier.fromString(""), emptyMap(), false)
    }
}