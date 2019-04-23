package com.r3.logicapps.processing

import com.r3.logicapps.BusRequest
import com.r3.logicapps.BusResponse
import com.r3.logicapps.servicebus.ServicebusClient
import java.util.UUID

interface MessageProcessor {
    fun invoke(message: BusRequest, client: ServicebusClient, messageLockTokenId: UUID): List<BusResponse>
}