package com.r3.logicapps.processing

import com.r3.logicapps.BusRequest
import com.r3.logicapps.BusResponse
import com.r3.logicapps.Invocable
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic

open class MessageProcessorImpl(
    private val startFlowDelegate: (FlowLogic<*>) -> FlowInvocationResult
) : MessageProcessor {
    override fun invoke(message: BusRequest): BusResponse = when (message) {
        is BusRequest.InvokeFlowWithoutInputStates -> processInvocationMessage(message.requestId, null, message, true)
        is BusRequest.InvokeFlowWithInputStates -> processInvocationMessage(
            message.requestId,
            message.linearId,
            message,
            false
        )
        is BusRequest.QueryFlowState -> {
            TODO("Handler for QueryFlowState not implemented")
        }
    }

    private fun processInvocationMessage(
        requestId: String,
        linearID: UniqueIdentifier?,
        invocable: Invocable,
        isNew: Boolean
    ): BusResponse {
        return try {
            val flowLogic = deriveFlowLogic(invocable.workflowName, invocable.parameters)
            val result = startFlowDelegate(flowLogic)
            BusResponse.FlowOutput(
                ingressType = invocable::class,
                requestId = requestId,
                linearId = result.linearId ?: linearID ?: error("Unable to derive linear ID after flow invocation"),
                fields = result.fields,
                isNewContract = isNew
            )
        } catch (exception: Throwable) {
            BusResponse.FlowError(
                ingressType = invocable::class,
                requestId = requestId,
                linearId = linearID,
                exception = exception
            )
        }
    }

    private fun deriveFlowLogic(flowName: String, parameters: Map<String, String>): FlowLogic<*> {
        val clazz = Class.forName(flowName) ?: error("Unable to find '$flowName' on the class path")
        val flowLogic = clazz.asSubclass(FlowLogic::class.java) ?: error("$flowName is not a subclass of FlowLogic")
        TODO()
    }
}