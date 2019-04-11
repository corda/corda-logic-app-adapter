package com.r3.logicapps.servicebus

import com.microsoft.azure.servicebus.IMessageHandler


//TODO: Bogdan add kdocs to the interface
interface ServicebusClient {

    fun start()
    fun close()

    fun send(message: String)
    fun registerReceivedMessageHandler(handler: IMessageHandler)

}