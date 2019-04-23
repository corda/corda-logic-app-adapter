package com.r3.logicapps.processing

import com.r3.logicapps.BusRequest
import com.r3.logicapps.BusRequest.QueryFlowState
import com.r3.logicapps.BusResponse
import com.r3.logicapps.BusResponse.Error.FlowError
import com.r3.logicapps.BusResponse.InvocationState
import com.r3.logicapps.Invocable
import com.r3.logicapps.servicebus.ServicebusClient
import io.github.classgraph.ClassGraph
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.internal.pooledScan
import net.corda.core.node.services.IdentityService
import java.util.UUID

open class MessageProcessorImpl(
    private val startFlowDelegate: (FlowLogic<*>, ServicebusClient, UUID) -> FlowInvocationResult,
    private val retrieveStateDelegate: (UniqueIdentifier) -> StateQueryResult,
    private val identityService: IdentityService? = null
) : MessageProcessor {
    override fun invoke(message: BusRequest, client: ServicebusClient, messageLockTokenId: UUID): List<BusResponse> = when (message) {
        is BusRequest.InvokeFlowWithoutInputStates ->
            processInvocationMessage(message.requestId, null, message, true, client, messageLockTokenId)
        is BusRequest.InvokeFlowWithInputStates    ->
            processInvocationMessage(message.requestId, message.linearId, message, false, client, messageLockTokenId)
        is BusRequest.QueryFlowState               -> {
            // Message can be safely ACKd
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

        return try {
            val linearIdParameter = linearId?.let { mapOf("linearId" to linearId.toString()) } ?: emptyMap()
            val flowLogic = deriveFlowLogic(invocable.workflowName, invocable.parameters + linearIdParameter)
            val result = startFlowDelegate(flowLogic, client, messageLockTokenId)
            val lid = result.linearId ?: linearId ?: error("Unable to derive linear ID after flow invocation")

            val fromName = result.fromName ?: error("Unable to retrieve invoking party after flow invocation")
            val transactionHash = result.hash ?: error("Unable to derive transaction hash after flow invocation")

            listOf(
                *result.toNames.map { to ->
                    InvocationState(
                        requestId = requestId,
                        linearId = lid,
                        parameters = invocable.parameters,
                        caller = fromName,
                        flowClass = flowLogic::class,
                        fromName = fromName,
                        toName = to,
                        transactionHash = transactionHash
                    )
                }.toTypedArray(),
                BusResponse.FlowOutput(
                    ingressType = ingressType,
                    requestId = requestId,
                    linearId = lid,
                    fields = result.fields,
                    isNewContract = isNew,
                    fromName = fromName,
                    toNames = result.toNames,
                    transactionHash = transactionHash
                ),
                BusResponse.Confirmation.Committed(
                    requestId = requestId,
                    linearId = lid,
                    ingressType = ingressType
                ),
                BusResponse.Confirmation.Submitted(
                    requestId = requestId,
                    linearId = lid,
                    ingressType = ingressType
                )
            )
        } catch (exception: Throwable) {
            listOf(FlowError(ingressType, requestId, exception, linearId))
        }
    }

    private fun processQueryMessage(requestId: String, linearId: UniqueIdentifier): BusResponse = try {
        retrieveStateDelegate(linearId).let { result ->
            BusResponse.StateOutput(
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
            linearId = linearId,
            cause = exception
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

    companion object {
        private val flowClassList: List<String> by lazy {
            val scanResult = ClassGraph().enableAllInfo().pooledScan()
            val flowClasses = scanResult.getSubclasses(FlowLogic::class.java.name).map { it.name }
            flowClasses
        }
    }
}