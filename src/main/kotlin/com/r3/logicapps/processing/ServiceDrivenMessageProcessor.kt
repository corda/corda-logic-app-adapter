package com.r3.logicapps.processing

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import net.corda.client.jackson.JacksonSupport
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.LinearState
import net.corda.core.node.AppServiceHub
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow

class ServiceDrivenMessageProcessor(appServiceHub: AppServiceHub) : MessageProcessorImpl(
    startFlowDelegate = { flowLogic ->
        val handle = appServiceHub.startFlow(flowLogic)
        try {
            // TODO moritzplatt 2019-04-11 -- allow for configuring a timeout
            val transaction = handle.returnValue.getOrThrow() as? SignedTransaction
                ?: throw IllegalArgumentException("Only flows returning `SignedTransaction` are supported")

            transaction.coreTransaction.outputStates.toFlowInvocationResult()
        } catch (exception: Throwable) {
            FlowInvocationResult(exception = exception)
        }
    }
)

fun List<ContractState>.toFlowInvocationResult(): FlowInvocationResult {
    val mapper = JacksonSupport.createNonRpcMapper(JsonFactory())

    if (size > 1)
        throw IllegalArgumentException("Only flows with at most a single output state are supported")

    val linearState = singleOrNull()?.let {
        it as? LinearState ?: throw IllegalArgumentException("Only linear output states are supported")
    }

    val fields = linearState?.let {
        mapper.valueToTree<ObjectNode>(it).removeLinearId().flattenWithDotNotation()
    } ?: emptyMap()

    return FlowInvocationResult(
        linearId = linearState?.linearId,
        fields = fields
    )
}

private fun ObjectNode.removeLinearId(): ObjectNode {
    remove("linearId")
    return this
}
