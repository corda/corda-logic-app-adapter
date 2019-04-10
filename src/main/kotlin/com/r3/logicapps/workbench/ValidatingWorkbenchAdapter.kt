package com.r3.logicapps.workbench

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.TextNode
import com.r3.logicapps.RPCRequest
import com.r3.logicapps.RPCResponse
import com.r3.logicapps.RPCResponse.FlowOutput
import com.r3.logicapps.servicebus.ServicebusMessage

class ValidatingWorkbenchAdapter : WorkbenchAdapter {

    @Throws(IllegalArgumentException::class)
    override fun transformIngress(message: ServicebusMessage): RPCRequest {
        ObjectMapper().readTree(message).let { json ->
            when (json.messageName()) {
                "CreateContractRequest"       -> TODO("do it!")
                "CreateContractActionRequest" -> TODO("do it!")
                "ReadContractRequest"         -> TODO("do it!")
                else                          -> throw IllegalArgumentException("Unknown message name")
            }
        }
    }

    @Throws(IllegalArgumentException::class)
    override fun transformEgress(message: RPCResponse): ServicebusMessage {
        when (message) {
            is FlowOutput -> TODO("do it!")
        }
    }

    private fun JsonNode.messageName(): String? = (get("messageName") as? TextNode)?.textValue()
}
