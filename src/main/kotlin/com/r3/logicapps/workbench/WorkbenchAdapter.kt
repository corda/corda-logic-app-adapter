package com.r3.logicapps.workbench

import com.r3.logicapps.RPCRequest
import com.r3.logicapps.RPCResponse
import com.r3.logicapps.servicebus.ServicebusMessage

interface WorkbenchAdapter {
    /**
     * TODO
     *  - determine the type of message
     *  - extract parameters
     */
    fun transformIngress(message: ServicebusMessage): RPCRequest

    /**
     * TODO
     *  - generate JSON format expected
     */
    fun transformEgress(message: RPCResponse): ServicebusMessage
}