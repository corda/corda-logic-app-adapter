package com.r3.logicapps.rpc

import com.r3.logicapps.RPCRequest
import com.r3.logicapps.RPCResponse

interface RPCInvoker {
    fun invoke(message: RPCRequest): RPCResponse?
}