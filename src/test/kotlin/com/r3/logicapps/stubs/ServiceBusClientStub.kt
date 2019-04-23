package com.r3.logicapps.stubs

import com.microsoft.azure.servicebus.IMessageHandler
import com.r3.logicapps.servicebus.ServicebusClient
import net.corda.core.utilities.contextLogger
import java.util.*

class ServiceBusClientStub: ServicebusClient {

    private companion object {
        val log = contextLogger()
    }

    override fun start() {
        log.info("Service bus client stub started")
    }

    override fun close() {
        log.info("Service bus client stub stopped")
    }

    override fun send(message: String) {
        log.info("Service bus client stub sent message $message")
    }

    override fun registerReceivedMessageHandler(handler: IMessageHandler) {
    }

    override fun acknowledge(lockId: UUID) {
        log.info("Service bus client stub acknowledged message")
    }

}