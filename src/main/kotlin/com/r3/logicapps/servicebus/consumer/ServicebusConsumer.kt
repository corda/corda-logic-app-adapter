package com.r3.logicapps.servicebus.consumer

interface ServicebusConsumer {
    /**
     * TODO
     *  - register a message handler to call this method
     *  - transform message to a generic format
     *  - propagate message to the adapter
     */
    fun handleMessage(message: String)
}