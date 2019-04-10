package com.r3.logicapps

import com.r3.logicapps.rpc.RPCInvoker
import com.r3.logicapps.rpc.RPCInvokerImpl
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken

@CordaService
class LogicAppService(appServiceHub: AppServiceHub): SingletonSerializeAsToken() {
    private val rpcInvoker: RPCInvoker = RPCInvokerImpl(appServiceHub)
}
