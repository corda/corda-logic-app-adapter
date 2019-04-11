package com.r3.logicapps.workbench

import com.r3.logicapps.BusRequest
import com.r3.logicapps.BusResponse
import com.r3.logicapps.servicebus.ServicebusMessage

interface WorkbenchAdapter {
    fun transformIngress(message: ServicebusMessage): BusRequest
    fun transformEgress(message: BusResponse): ServicebusMessage
}