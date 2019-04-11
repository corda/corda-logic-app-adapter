package com.r3.logicapps.workbench

import com.r3.logicapps.BusRequest
import com.r3.logicapps.BusResponse
import com.r3.logicapps.servicebus.ServicebusMessage

interface WorkbenchAdapter {
    /**
     * TODO
     *  - determine the type of message
     *  - extract parameters
     */
    fun transformIngress(message: ServicebusMessage): BusRequest

    /**
     * TODO
     *  - generate JSON format expected
     */
    fun transformEgress(message: BusResponse): ServicebusMessage
}