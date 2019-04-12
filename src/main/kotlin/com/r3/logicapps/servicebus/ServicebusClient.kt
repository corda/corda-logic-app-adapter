package com.r3.logicapps.servicebus

import com.microsoft.azure.servicebus.IMessageHandler
import com.microsoft.azure.servicebus.QueueClient

/**
 * Interface describing basic functionality of Azure Service Bus clients.
 */
interface ServicebusClient {

    /**
     * Starts the client and connects to the Service Bus endpoint
     */
    fun start()

    /**
     * Closes the connection to the Service Bus endpoint.
     */
    fun close()


    /**
     * Put a string message to the Service Bus.
     * @param message [String] payload in JSON format
     *
     */
    fun send(message: String)

    /**
     * Register a message handler for consumed messages. The Azure Service Bus SDK allows only one handler per
     * instance of [QueueClient]. Implementations of this interface should create additional clients should more handlers
     * bee needed.
     * @param handler intance of [IMessageHandler]
     */
    fun registerReceivedMessageHandler(handler: IMessageHandler)

}