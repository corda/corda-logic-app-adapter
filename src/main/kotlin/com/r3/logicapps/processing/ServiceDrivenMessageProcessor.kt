package com.r3.logicapps.processing

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import net.corda.client.jackson.JacksonSupport
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.LinearState
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.Vault.StateStatus.CONSUMED
import net.corda.core.node.services.Vault.StateStatus.UNCONSUMED
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria.LinearStateQueryCriteria
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
    },
    retrieveStateDelegate = { linearId ->
        val unconsumed = LinearStateQueryCriteria(
            status = UNCONSUMED,
            linearId = listOf(linearId)
        )

        val consumed = LinearStateQueryCriteria(
            status = CONSUMED,
            linearId = listOf(linearId)
        )

        val consumedStateAndRefs = appServiceHub.vaultService.queryBy<LinearState>(consumed).states

        val unconsumedStateAndRef = appServiceHub.vaultService.queryBy<LinearState>(unconsumed).states.firstOrNull()
            ?: throw IllegalArgumentException("No state with ID found")

        val linearState = unconsumedStateAndRef.state.data

        StateQueryResult(
            fields = linearState.fields(),
            isNewContract = consumedStateAndRefs.isEmpty()
        )
    }
)

fun List<ContractState>.toFlowInvocationResult(): FlowInvocationResult {
    if (size > 1)
        throw IllegalArgumentException("Only flows with at most a single output state are supported")

    val linearState = singleOrNull()?.let {
        it as? LinearState ?: throw IllegalArgumentException("Only linear output states are supported")
    }

    return FlowInvocationResult(
        linearId = linearState?.linearId,
        fields = linearState?.fields() ?: emptyMap()
    )
}

private fun LinearState.fields(): Map<String, String> = JacksonSupport
    .createNonRpcMapper(JsonFactory())
    .valueToTree<ObjectNode>(this)
    .removeLinearId()
    .flattenWithDotNotation()

private fun ObjectNode.removeLinearId(): ObjectNode {
    remove("linearId")
    return this
}
