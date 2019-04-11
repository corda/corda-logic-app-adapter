package com.r3.logicapps.processing

import com.r3.logicapps.BusRequest
import com.r3.logicapps.BusResponse

interface MessageProcessor {
    fun invoke(message: BusRequest): BusResponse?
}