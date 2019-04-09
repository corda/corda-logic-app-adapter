package com.r3.logicapps.servicebus.producer

interface ServicebusProducer {
    /**
     * TODO
     *  - put message on the bus
     *  - put transaction id and resulting state in a response
     *  - put that onto bus
     */
    fun handleMessage(string: String)
}