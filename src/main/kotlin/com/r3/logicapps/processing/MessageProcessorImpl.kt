package com.r3.logicapps.processing

import com.r3.logicapps.BusRequest
import com.r3.logicapps.BusRequest.InvokeFlowWithInputStates
import com.r3.logicapps.BusRequest.InvokeFlowWithoutInputStates
import com.r3.logicapps.BusRequest.QueryFlowState
import com.r3.logicapps.BusResponse
import com.r3.logicapps.BusResponse.Confirmation
import com.r3.logicapps.BusResponse.Error.FlowError
import com.r3.logicapps.BusResponse.FlowOutput
import com.r3.logicapps.BusResponse.InvocationState
import com.r3.logicapps.BusResponse.StateOutput
import com.r3.logicapps.Invocable
import com.r3.logicapps.servicebus.ServicebusClient
import io.github.classgraph.ClassGraph
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.pooledScan
import net.corda.core.node.services.IdentityService
import net.corda.core.utilities.contextLogger
import java.util.UUID

open class MessageProcessorImpl(
    private val startFlowDelegate: (FlowLogic<*>, ServicebusClient, UUID) -> FlowInvocationResult,
    private val retrieveStateDelegate: (UniqueIdentifier) -> StateQueryResult,
    private val identityService: IdentityService? = null,
    private val owner: CordaX500Name
) : MessageProcessor {

    override fun invoke(message: BusRequest, client: ServicebusClient, messageLockTokenId: UUID): List<BusResponse> =
        when (message) {
            is InvokeFlowWithoutInputStates ->
                processInvocationMessage(
                    requestId = message.requestId,
                    linearId = null,
                    invocable = message,
                    isNew = true,
                    client = client,
                    messageLockTokenId = messageLockTokenId
                )
            is InvokeFlowWithInputStates    ->
                processInvocationMessage(
                    requestId = message.requestId,
                    linearId = message.linearId,
                    invocable = message,
                    isNew = false,
                    client = client,
                    messageLockTokenId = messageLockTokenId
                )
            is QueryFlowState               -> {
                // Message can be safely ACKd
                log.debug("Acknowledging message with lockTokenId $messageLockTokenId")
                client.acknowledge(messageLockTokenId)
                listOf(processQueryMessage(message.requestId, message.linearId))
            }
        }

    private fun processInvocationMessage(
        requestId: String,
        linearId: UniqueIdentifier?,
        invocable: Invocable,
        isNew: Boolean,
        client: ServicebusClient,
        messageLockTokenId: UUID
    ): List<BusResponse> {
        val ingressType = invocable::class
        var flowLogic: FlowLogic<*>? = null
        try {
            val linearIdParameter = linearId?.let { mapOf("linearId" to linearId.toString()) } ?: emptyMap()
            flowLogic = deriveFlowLogic(invocable.workflowName, invocable.parameters + linearIdParameter)
        } catch (exception: Throwable) {
            // Catch exception thrown by flow logic creation and ACK the message to avoid double ACK. In case of flow invocation errors, they
            // can appear after the handle is created, at which point the message is already ACKd.
            log.debug("Exception during flow logic creation", exception)
            log.debug("Acknowledging message with lockTokenId $messageLockTokenId")
            client.acknowledge(messageLockTokenId)
            listOf(FlowError(ingressType, requestId, exception, linearId))
        }

        return try {
            val result = startFlowDelegate(flowLogic!!, client, messageLockTokenId)

            val lid = result.linearId ?: linearId ?: error("Unable to derive linear ID after flow invocation")
            val transactionHash = result.hash ?: error("Unable to derive transaction hash after flow invocation")

            listOf(
                InvocationState(
                    requestId = requestId,
                    linearId = lid,
                    parameters = invocable.parameters,
                    caller = owner,
                    flowClass = flowLogic::class,
                    transactionHash = transactionHash
                ),
                FlowOutput(
                    ingressType = ingressType,
                    requestId = requestId,
                    linearId = lid,
                    fields = result.fields,
                    isNewContract = isNew,
                    fromName = owner,
                    toNames = result.toNames,
                    transactionHash = transactionHash
                ),
                Confirmation.Committed(
                    requestId = requestId,
                    linearId = lid,
                    ingressType = ingressType
                ),
                Confirmation.Submitted(
                    requestId = requestId,
                    linearId = lid,
                    ingressType = ingressType
                )
            )
        } catch (exception: Throwable) {
            log.debug("Exception during flow invocation", exception)
            listOf(FlowError(ingressType, requestId, exception, linearId))
        }
    }

    private fun processQueryMessage(requestId: String, linearId: UniqueIdentifier): BusResponse = try {
        retrieveStateDelegate(linearId).let { result ->
            StateOutput(
                requestId = requestId,
                linearId = linearId,
                fields = result.fields,
                isNewContract = result.isNewContract
            )
        }
    } catch (exception: Throwable) {
        FlowError(
            ingressType = QueryFlowState::class,
            requestId = requestId,
            cause = exception,
            linearId = linearId
        )
    }

    private fun deriveFlowLogic(flowName: String, parameters: Map<String, String>): FlowLogic<*> {
        val flowClasses = flowClassList
        val fullFlowName = flowClasses.firstOrNull { it.endsWith(".$flowName") }
        val clazz = try {
            Class.forName(fullFlowName ?: flowName)
        } catch (_: ClassNotFoundException) {
            throw ClassNotFoundException("Unable to find '$flowName' on the class path")
        }
        val flowLogic = clazz.asSubclass(FlowLogic::class.java) ?: error("$flowName is not a subclass of FlowLogic")
        val statement = FlowInvoker.getFlowStatementFromString(flowLogic, parameters, identityService)
        if (statement.errors.isNotEmpty()) {
            throw IllegalStateException(statement.errors.joinToString("; "))
        }
        val ctor = statement.ctor ?: error("Unable to derive applicable constructor for $flowName")
        val arguments = statement.arguments ?: error("Unable to derive arguments for $flowName")
        return ctor.newInstance(*arguments) as? FlowLogic<*>
            ?: error("Unable to instantiate $flowName with provided arguments")
    }

    private companion object {
        val log = contextLogger()
        val flowClassList: List<String> by lazy {
            val scanResult = ClassGraph().enableAllInfo().pooledScan()
            val flowClasses = scanResult.getSubclasses(FlowLogic::class.java.name).map { it.name }
            flowClasses
        }
    }
}