package com.r3.logicapps.workbench

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.r3.logicapps.RPCRequest
import com.r3.logicapps.RPCRequest.FlowInvocationRequest
import com.r3.logicapps.RPCRequest.InvokeFlowWithInputStates
import com.r3.logicapps.RPCRequest.QueryFlowState
import com.r3.logicapps.RPCResponse
import com.r3.logicapps.RPCResponse.FlowOutput
import com.r3.logicapps.servicebus.ServicebusMessage
import com.r3.logicapps.workbench.WorkbenchSchema.FlowInvocationRequestSchema
import com.r3.logicapps.workbench.WorkbenchSchema.FlowStateRequestSchema
import com.r3.logicapps.workbench.WorkbenchSchema.FlowUpdateRequestSchema
import net.corda.core.contracts.UniqueIdentifier
import org.everit.json.schema.ValidationException
import org.json.JSONObject

class ValidatingWorkbenchAdapter : WorkbenchAdapter {

    @Throws(IllegalArgumentException::class)
    override fun transformIngress(message: ServicebusMessage): RPCRequest =
        ObjectMapper().readTree(message).let { json ->
            when (json.messageName()) {
                "CreateContractRequest"       -> {
                    FlowInvocationRequestSchema.validate(message)
                    transformFlowInvocationRequest(json)
                }
                "CreateContractActionRequest" -> {
                    FlowUpdateRequestSchema.validate(message)
                    transformFlowUpdateRequest(json)
                }
                "ReadContractRequest"         -> {
                    FlowStateRequestSchema.validate(message)
                    transformFlowStateRequest(json)
                }
                else                          -> throw IllegalArgumentException("Unknown message name")
            }
        }

    private fun WorkbenchSchema.validate(message: String) {
        try {
            underlying.validate(JSONObject(message))
        } catch (exception: ValidationException) {
            throw IllegalArgumentException("Not a valid message for schema ${this::class.java}: ${exception.message}")
        }
    }

    private fun transformFlowInvocationRequest(json: JsonNode): FlowInvocationRequest {
        val requestId = json.extractRequestId("requestId")
        val workflowName = json.extractWorkflowName("workflowName")
        val parameters = json.extractParameters("parameters")
        return FlowInvocationRequest(requestId, workflowName, parameters)
    }

    private fun transformFlowUpdateRequest(json: JsonNode): InvokeFlowWithInputStates {
        val requestId = json.extractRequestId("requestId")
        val linearId = UniqueIdentifier.fromString(json.extractLinearId("contractLedgerIdentifier"))
        val workflowName = json.extractWorkflowName("workflowFunctionName")
        val parameters = json.extractParameters("parameters")
        return InvokeFlowWithInputStates(requestId, linearId, workflowName, parameters)
    }

    private fun transformFlowStateRequest(json: JsonNode): QueryFlowState {
        val requestId = json.extractRequestId("requestId")
        val linearId = UniqueIdentifier.fromString(json.extractLinearId("contractLedgerIdentifier"))
        return QueryFlowState(requestId, linearId)
    }

    private fun JsonNode.extractRequestId(name: String) = (get(name) as? TextNode)?.textValue()
        ?: throw IllegalArgumentException("Invalid request ID provided")

    private fun JsonNode.extractLinearId(name: String) = (get(name) as? TextNode)?.textValue()
        ?: throw IllegalArgumentException("Invalid linear ID provided")

    private fun JsonNode.extractWorkflowName(name: String) = (get(name) as? TextNode)?.textValue()
        ?: throw IllegalArgumentException("Invalid workflow name provided")

    private fun JsonNode.extractParameters(name: String): Map<String, String> {
        return (get(name) as? ArrayNode ?: throw IllegalArgumentException("No parameters provided")).map {
            (it as? ObjectNode)?.let { parameter ->
                val key = (parameter.get("name") as? TextNode)?.textValue()
                    ?: throw IllegalArgumentException("Malformed Key")

                val value = (parameter.get("value") as? TextNode)?.textValue()
                    ?: throw IllegalArgumentException("Malformed Value")

                key to value
            } ?: throw IllegalArgumentException("Malformed Parameter")
        }.toMap()
    }

    @Throws(IllegalArgumentException::class)
    override fun transformEgress(message: RPCResponse): ServicebusMessage {
        when (message) {
            is FlowOutput -> TODO("do it!")
        }
    }

    private fun JsonNode.messageName(): String? = (get("messageName") as? TextNode)?.textValue()
}
