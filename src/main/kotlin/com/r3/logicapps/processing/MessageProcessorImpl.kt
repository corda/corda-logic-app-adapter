package com.r3.logicapps.processing

import com.r3.logicapps.BusRequest
import com.r3.logicapps.BusRequest.QueryFlowState
import com.r3.logicapps.BusResponse
import com.r3.logicapps.Invocable
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.node.services.IdentityService

open class MessageProcessorImpl(
    private val startFlowDelegate: (FlowLogic<*>) -> FlowInvocationResult,
    private val retrieveStateDelegate: (UniqueIdentifier) -> StateQueryResult,
    private val identityService: IdentityService? = null
) : MessageProcessor {
    override fun invoke(message: BusRequest): BusResponse = when (message) {
        is BusRequest.InvokeFlowWithoutInputStates ->
            processInvocationMessage(message.requestId, null, message, true)
        is BusRequest.InvokeFlowWithInputStates    ->
            processInvocationMessage(message.requestId, message.linearId, message, false)
        is BusRequest.QueryFlowState               ->
            processQueryMessage(message.requestId, message.linearId)
    }

    private fun processInvocationMessage(
        requestId: String,
        linearId: UniqueIdentifier?,
        invocable: Invocable,
        isNew: Boolean
    ): BusResponse {
        return try {
            val linearIdParameter = linearId?.let { mapOf("linearId" to linearId.toString()) } ?: emptyMap()
            val flowLogic = deriveFlowLogic(invocable.workflowName, invocable.parameters + linearIdParameter)
            val result = startFlowDelegate(flowLogic)
            BusResponse.FlowOutput(
                ingressType = invocable::class,
                requestId = requestId,
                linearId = result.linearId ?: linearId ?: error("Unable to derive linear ID after flow invocation"),
                fields = result.fields,
                isNewContract = isNew,
                fromName = result.fromName ?: error("Unable to retrieve invoking party after flow invocation"),
                toNames = result.toNames,
                transactionHash = result.hash ?: error("Unable to derive transaction hash after flow invocation")
            )
        } catch (exception: Throwable) {
            BusResponse.FlowError(invocable::class, requestId, linearId, exception)
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
        BusResponse.FlowError(
            ingressType = QueryFlowState::class,
            requestId = requestId,
            linearId = linearId,
            exception = exception
        )
    }

    private fun deriveFlowLogic(flowName: String, parameters: Map<String, String>): FlowLogic<*> {
        val clazz = try {
            Class.forName(flowName)
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
}