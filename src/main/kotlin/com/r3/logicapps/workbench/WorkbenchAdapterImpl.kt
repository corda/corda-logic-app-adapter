package com.r3.logicapps.workbench

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.r3.logicapps.BusRequest
import com.r3.logicapps.BusRequest.InvokeFlowWithInputStates
import com.r3.logicapps.BusRequest.InvokeFlowWithoutInputStates
import com.r3.logicapps.BusRequest.QueryFlowState
import com.r3.logicapps.BusResponse
import com.r3.logicapps.BusResponse.Confirmation
import com.r3.logicapps.BusResponse.Confirmation.Committed
import com.r3.logicapps.BusResponse.Confirmation.Submitted
import com.r3.logicapps.BusResponse.FlowError
import com.r3.logicapps.BusResponse.FlowOutput
import com.r3.logicapps.BusResponse.StateOutput
import com.r3.logicapps.servicebus.ServicebusMessage
import com.r3.logicapps.workbench.WorkbenchSchema.FlowInvocationRequestSchema
import com.r3.logicapps.workbench.WorkbenchSchema.FlowStateRequestSchema
import com.r3.logicapps.workbench.WorkbenchSchema.FlowUpdateRequestSchema
import net.corda.core.contracts.UniqueIdentifier
import org.everit.json.schema.ValidationException
import org.json.JSONObject
import kotlin.reflect.KClass

object WorkbenchAdapterImpl : WorkbenchAdapter {

    private const val FAKE_BLOCK_ID = 999
    private const val FAKE_TRANSACTION_ID = 999
    private const val FAKE_CONTRACT_ID = 1
    private const val FAKE_CONNECTION_ID = 1

    @Throws(IllegalArgumentException::class)
    override fun transformIngress(message: ServicebusMessage): BusRequest =
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

    @Throws(IllegalArgumentException::class)
    override fun transformEgress(message: BusResponse): ServicebusMessage = when (message) {
        is FlowOutput  -> transformFlowOutputResponse(message)
        is StateOutput -> transformStateOutputMessage(message)
        is FlowError   -> transformFlowErrorResponse(message)
        is Confirmation -> transformConfirmationResponse(message)
    }

    private fun WorkbenchSchema.validate(message: String) {
        try {
            underlying.validate(JSONObject(message))
        } catch (exception: ValidationException) {
            throw IllegalArgumentException("Not a valid message for schema ${this::class.java}: ${exception.message}")
        }
    }

    private fun transformFlowOutputResponse(flowOutput: FlowOutput): ServicebusMessage {
        val node = JsonNodeFactory.instance.objectNode().apply {
            put("messageName", "ContractMessage")
            // TODO moritzplatt 2019-04-12 -- need to agree on appropriate content for this field
            put("blockId", FAKE_BLOCK_ID)
            put("blockhash", flowOutput.transactionHash.toString())
            put("requestId", flowOutput.requestId)
            putObject("additionalInformation")
            put("contractLedgerIdentifier", flowOutput.linearId.toString())
            putArray("contractProperties").apply {
                flowOutput.fields.forEach { k, v ->
                    addObject().apply {
                        put("name", k)
                        put("value", v)
                    }
                }
            }
            putArray("modifyingTransactions").apply {
                addObject().apply {
                    put("from", flowOutput.fromName.toString())
                    putArray("to").apply {
                        flowOutput.toNames.forEach {
                            add(it.toString())
                        }
                    }
                    // TODO moritzplatt 2019-04-12 -- need to agree on appropriate content for this field
                    put("transactionId", FAKE_TRANSACTION_ID)
                    put("transactionHash", flowOutput.transactionHash.toString())
                }
            }
            // TODO moritzplatt 2019-04-12 -- need to agree on appropriate content for this field
            put("contractId", FAKE_CONTRACT_ID)
            // TODO moritzplatt 2019-04-12 -- need to agree on appropriate content for this field
            put("connectionId", FAKE_CONNECTION_ID)
            put("messageSchemaVersion", "1.0.0")
            put("isNewContract", flowOutput.isNewContract)
        }
        return ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(node)
    }

    private fun transformStateOutputMessage(flowOutput: StateOutput): ServicebusMessage {
        val node = JsonNodeFactory.instance.objectNode().apply {
            put("messageName", "ContractMessage")
            put("requestId", flowOutput.requestId)
            putObject("additionalInformation")
            put("contractLedgerIdentifier", flowOutput.linearId.toString())
            putArray("contractProperties").apply {
                flowOutput.fields.forEach { k, v ->
                    addObject().apply {
                        put("name", k)
                        put("value", v)
                    }
                }
            }
            put("messageSchemaVersion", "1.0.0")
            put("isNewContract", flowOutput.isNewContract)
        }
        return ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(node)

    }

    private fun transformFlowErrorResponse(flowError: FlowError): ServicebusMessage {
        val node = JsonNodeFactory.instance.objectNode().apply {
            put("messageName", flowError.ingressType.toWorkbenchName())
            put("requestId", flowError.requestId)
            putObject("additionalInformation").apply {
                put("errorCode", flowError.exception.errorCode())
                put("errorMessage", flowError.exception.message ?: "")
            }
            flowError.linearId?.let {
                put("contractLedgerIdentifier", it.toString())
            }
            put("status", "Failure")
            put("messageSchemaVersion", "1.0.0")
        }
        return ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(node)
    }

    private fun transformConfirmationResponse(confirmation: Confirmation): ServicebusMessage {
        val node = JsonNodeFactory.instance.objectNode().apply {
            put("messageName", confirmation.ingressType.toWorkbenchName())
            putObject("additionalInformation")
            put("requestId", confirmation.requestId)

            // TODO moritzplatt 2019-04-12 -- need to agree on appropriate content for this field
            put("contractId", FAKE_CONTRACT_ID)
            // TODO moritzplatt 2019-04-12 -- need to agree on appropriate content for this field
            put("connectionId", FAKE_CONNECTION_ID)

            put("messageSchemaVersion", "1.0.0")
            put("status", confirmation.toWorkbenchName())
        }
        return ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(node)
    }

    private fun transformFlowInvocationRequest(json: JsonNode): InvokeFlowWithoutInputStates {
        val requestId = json.extractRequestId("requestId")
        val workflowName = json.extractWorkflowName("workflowName")
        val parameters = json.extractParameters("parameters")
        return InvokeFlowWithoutInputStates(requestId, workflowName, parameters)
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

    private fun JsonNode.messageName(): String? = (get("messageName") as? TextNode)?.textValue()

    private fun KClass<*>.toWorkbenchName() = when (this) {
        InvokeFlowWithoutInputStates::class -> "CreateContractRequest"
        InvokeFlowWithInputStates::class    -> "CreateContractActionRequest"
        QueryFlowState::class               -> "ReadContractRequest"
        else                                -> throw IllegalArgumentException("Unknown bus request type ${this.simpleName}")
    }
}

private fun Confirmation.toWorkbenchName(): String = when (this) {
    is Submitted -> "Submitted"
    is Committed -> "Committed"
}
