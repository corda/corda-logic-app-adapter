package com.r3.logicapps.workbench

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.r3.logicapps.RPCRequest
import com.r3.logicapps.RPCRequest.FlowInvocationRequest
import com.r3.logicapps.RPCResponse
import com.r3.logicapps.RPCResponse.FlowOutput
import com.r3.logicapps.servicebus.ServicebusMessage
import com.r3.logicapps.workbench.WorkbenchSchema.FlowInvocationRequestSchema
import org.everit.json.schema.ValidationException
import org.json.JSONObject

class ValidatingWorkbenchAdapter : WorkbenchAdapter {

    @Throws(IllegalArgumentException::class)
    override fun transformIngress(message: ServicebusMessage): RPCRequest =
        ObjectMapper().readTree(message).let { json ->
            when (json.messageName()) {
                "CreateContractRequest"       -> {
                    validateFlowInvocationRequest(message)
                    transformFlowInvocationRequest(json)
                }
                "CreateContractActionRequest" -> TODO("do it!")
                "ReadContractRequest"         -> TODO("do it!")
                else                          -> throw IllegalArgumentException("Unknown message name")
            }
        }

    private fun validateFlowInvocationRequest(message: String) {
        try {
            FlowInvocationRequestSchema.underlying.validate(JSONObject(message))
        } catch (exception: ValidationException) {
            throw IllegalArgumentException("Flow invocation message: " + exception.message)
        }
    }

    private fun transformFlowInvocationRequest(root: JsonNode): FlowInvocationRequest {
        val requestId = (root.get("requestId") as? TextNode)?.textValue()
            ?: throw IllegalArgumentException("Invalid request ID provided")

        val workflowName = (root.get("workflowName") as? TextNode)?.textValue()
            ?: throw IllegalArgumentException("Invalid workflow name provided")

        val parameters =
            (root.get("parameters") as? ArrayNode ?: throw IllegalArgumentException("No parameters provided")).map {
                (it as? ObjectNode)?.let { parameter ->
                    val key = (parameter.get("name") as? TextNode)?.textValue()
                        ?: throw IllegalArgumentException("Malformed Key")

                    val value = (parameter.get("value") as? TextNode)?.textValue()
                        ?: throw IllegalArgumentException("Malformed Value")

                    key to value
                } ?: throw IllegalArgumentException("Malformed Parameter")
            }.toMap()

        return FlowInvocationRequest(requestId, workflowName, parameters)
    }

    @Throws(IllegalArgumentException::class)
    override fun transformEgress(message: RPCResponse): ServicebusMessage {
        when (message) {
            is FlowOutput -> TODO("do it!")
        }
    }

    private fun JsonNode.messageName(): String? = (get("messageName") as? TextNode)?.textValue()
}
