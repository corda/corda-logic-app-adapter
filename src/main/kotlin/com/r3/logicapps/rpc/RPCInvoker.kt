package com.r3.logicapps.rpc

import com.r3.logicapps.RPCRequest

interface RPCInvoker {
    /**
     * TODO
     *  - Invoke RPC using the message parameters
     *  - Obtain the transaction ID
     *  - obtain the output state
     *  -
     */
    fun invoke(message: RPCRequest)
}