package com.r3.logicapps.workbench

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.NullNode
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
import com.r3.logicapps.BusResponse.Error
import com.r3.logicapps.BusResponse.Error.CorrelatableError
import com.r3.logicapps.BusResponse.Error.FlowError
import com.r3.logicapps.BusResponse.Error.GenericError
import com.r3.logicapps.BusResponse.FlowOutput
import com.r3.logicapps.BusResponse.InvocationState
import com.r3.logicapps.BusResponse.StateOutput
import com.r3.logicapps.servicebus.ServicebusMessage
import com.r3.logicapps.workbench.WorkbenchSchema.FlowInvocationRequestSchema
import com.r3.logicapps.workbench.WorkbenchSchema.FlowStateRequestSchema
import com.r3.logicapps.workbench.WorkbenchSchema.FlowUpdateRequestSchema
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
import org.everit.json.schema.ValidationException
import org.json.JSONObject
import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.math.absoluteValue
import kotlin.reflect.KClass
import kotlin.text.Charsets.UTF_8

object WorkbenchAdapterImpl : WorkbenchAdapter {

    private const val CONNECTION_ID = 1

    private val jsonWriter = ObjectMapper().setDefaultPrettyPrinter(DefaultPrettyPrinter().apply {
        PlatformIndependentIndenter().let {
            indentArraysWith(it)
            indentObjectsWith(it)
        }
    }).writerWithDefaultPrettyPrinter()

    @Throws(IngressFormatException::class)
    override fun transformIngress(message: ServicebusMessage): BusRequest {
        // try to parse JSON
        val node = try {
            ObjectMapper().readTree(message)
        } catch (e: JsonParseException) {
            throw IngressFormatException(e)
        } ?: throw IngressFormatException("No ingress message presented")

        // try to determine the request ID early--even if the message is otherwise invalid--so we can return
        // correlation information to the caller
        val requestId = try {
            node.extractRequestId("requestId")
        } catch (e: IllegalArgumentException) {
            throw IngressFormatException(e)
        }

        return try {
            transformIngress(node, message)
        } catch (iae: IllegalArgumentException) {
            throw CorrelatableIngressFormatException(
                requestId = requestId,
                cause = iae
            )
        } catch (ve: ValidationException) {
            throw CorrelatableIngressFormatException(
                message = "${ve.errorMessage}: ${ve.allMessages.joinToString(", ")}",
                cause = ve,
                requestId = requestId
            )
        }
    }

    private fun transformIngress(node: JsonNode, message: ServicebusMessage): BusRequest {
        return node.let { json ->
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
    }

    override fun transformEgress(message: BusResponse): ServicebusMessage = when (message) {
        // success cases
        is FlowOutput        -> transformFlowOutputResponse(message)
        is StateOutput       -> transformStateOutputResponse(message)
        is Confirmation      -> transformConfirmationResponse(message)
        is InvocationState   -> transformInvocationStateResponse(message)

        // error cases
        is FlowError         -> transformFlowErrorResponse(message)
        is GenericError      -> transformGenericErrorResponse(message)
        is CorrelatableError -> transformCorrelatableErrorResponse(message)
    }

    private fun WorkbenchSchema.validate(message: String) {
        underlying.validate(JSONObject(message))
    }

    private fun transformFlowOutputResponse(flowOutput: FlowOutput): ServicebusMessage {
        val node = JsonNodeFactory.instance.objectNode().apply {
            put("messageName", "ContractMessage")
            set("blockId", NullNode.instance)
            put("blockHash", flowOutput.transactionHash.toPrefixedString())
            put("requestId", flowOutput.requestId)
            putObject("additionalInformation")
            put("contractLedgerIdentifier", flowOutput.linearId.toString())
            putArray("contractProperties").apply {
                flowOutput.fields.toList().forEachIndexed { i, (k, v) ->
                    addObject().apply {
                        put("workflowPropertyId", NullNode.instance)
                        put("name", k)
                        put("value", v)
                    }
                }
            }
            putArray("modifyingTransactions").apply {
                addObject().apply {
                    put("from", flowOutput.fromName.toString())
                    put("to", flowOutput.linearId.toString())

                    put("transactionId", flowOutput.transactionHash.bytes.toPositiveBigInteger())
                    put("transactionHash", flowOutput.transactionHash.toPrefixedString())
                }
            }

            put("contractId", flowOutput.linearId.id.toByteArray().toPositiveBigInteger())
            put("connectionId", CONNECTION_ID)
            put("messageSchemaVersion", "1.0.0")
            put("isNewContract", flowOutput.isNewContract)
        }
        return node.toPrettyString()
    }

    private fun transformStateOutputResponse(flowOutput: StateOutput): ServicebusMessage {
        val node = JsonNodeFactory.instance.objectNode().apply {
            put("messageName", "ContractMessage")
            put("requestId", flowOutput.requestId)
            putObject("additionalInformation")
            put("contractLedgerIdentifier", flowOutput.linearId.toString())
            putArray("contractProperties").apply {
                flowOutput.fields.toList().forEachIndexed { i, (k, v) ->
                    addObject().apply {
                        set("workflowPropertyId", NullNode.instance)
                        put("name", k)
                        put("value", v)
                    }
                }
            }
            put("messageSchemaVersion", "1.0.0")
        }
        return node.toPrettyString()

    }

    private fun transformFlowErrorResponse(error: FlowError): ServicebusMessage {
        val node = JsonNodeFactory.instance.objectNode().apply {
            put("requestId", error.requestId)
            error.linearId?.let { put("contractId", it.id.toByteArray().toPositiveBigInteger()) }
            put("connectionId", CONNECTION_ID)
            put("messageName", error.ingressType.toWorkbenchName())
            error.linearId?.let {
                put("contractLedgerIdentifier", it.toString())
            }
            put(error)
        }
        return node.toPrettyString()
    }

    private fun transformCorrelatableErrorResponse(error: CorrelatableError): ServicebusMessage =
        JsonNodeFactory.instance.objectNode().apply {
            put("requestId", error.requestId)
            put(error)
        }.toPrettyString()

    private fun transformGenericErrorResponse(error: GenericError): ServicebusMessage =
        JsonNodeFactory.instance.objectNode().apply {
            put(error)
        }.toPrettyString()

    private fun ObjectNode.put(error: Error) {
        putObject("additionalInformation").apply {
            put("errorCode", error.cause.errorCode().absoluteValue)
            put("errorMessage", error.cause.message ?: "")
        }
        put("status", "Failure")
        put("messageSchemaVersion", "1.0.0")
    }

    private fun transformConfirmationResponse(confirmation: Confirmation): ServicebusMessage {
        val node = JsonNodeFactory.instance.objectNode().apply {
            put("messageName", confirmation.ingressType.toWorkbenchName())
            putObject("additionalInformation")
            put("requestId", confirmation.requestId)

            put("contractId", confirmation.linearId.id.toByteArray().toPositiveBigInteger())
            put("contractLedgerIdentifier", confirmation.linearId.toString())
            put("connectionId", CONNECTION_ID)

            put("messageSchemaVersion", "1.0.0")
            put("status", confirmation.toWorkbenchName())
        }
        return node.toPrettyString()
    }

    private fun transformInvocationStateResponse(flowOutput: InvocationState): ServicebusMessage {
        val node = JsonNodeFactory.instance.objectNode().apply {
            put("messageName", "EventMessage")
            put("eventName", "ContractFunctionInvocation")

            put("requestId", flowOutput.requestId)

            putObject("caller").apply {
                put("type", "User")
                put("id", flowOutput.caller.toString().toPositiveBigInteger())
                put("ledgerIdentifier", flowOutput.caller.toString())
            }

            putObject("additionalInformation")

            put("contractId", flowOutput.linearId.id.toByteArray().toPositiveBigInteger())
            put("contractLedgerIdentifier", flowOutput.linearId.toString())

            flowOutput.flowClass.simpleName?.let { put("functionName", it) }

            putArray("parameters").apply {
                flowOutput.parameters.forEach { (k, v) ->
                    addObject().apply {
                        put("name", k)
                        put("value", v)
                    }
                }
            }

            putObject("transaction").apply {
                put("transactionId", flowOutput.transactionHash.bytes.toPositiveBigInteger())
                put("transactionHash", flowOutput.transactionHash.toPrefixedString())
                put("from", flowOutput.caller.toString())
                put("to", flowOutput.linearId.toString())
            }

            set("inTransactionSequenceNumber", NullNode.instance)
            put("connectionId", CONNECTION_ID)
            put("messageSchemaVersion", "1.0.0")
        }
        return node.toPrettyString()
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
        InvokeFlowWithoutInputStates::class -> "CreateContractUpdate"
        InvokeFlowWithInputStates::class    -> "CreateContractActionUpdate"
        QueryFlowState::class               -> "ReadContractUpdate"
        else                                -> throw IllegalStateException("Unknown bus request type ${this.simpleName}")
    }

    private fun Confirmation.toWorkbenchName(): String = when (this) {
        is Submitted -> "Submitted"
        is Committed -> "Committed"
    }

    private fun String.toPositiveBigInteger(): BigInteger = toByteArray(UTF_8).toPositiveBigInteger()
    private fun ByteArray.toPositiveBigInteger(): BigInteger = BigInteger(ByteArray(1) + this)

    private fun UUID.toByteArray(): ByteArray {
        val bb = ByteBuffer.wrap(ByteArray(16))
        bb.putLong(mostSignificantBits)
        bb.putLong(leastSignificantBits)
        return bb.array()
    }

    private fun ObjectNode.toPrettyString(): String = jsonWriter.writeValueAsString(this)
    private fun SecureHash.toPrefixedString(): String = "0x${toString()}"
}
