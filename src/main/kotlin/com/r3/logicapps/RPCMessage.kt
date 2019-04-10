package com.r3.logicapps

import net.corda.core.contracts.UniqueIdentifier

// A message in a format suitable for RPC invocation
sealed class RPCRequest : Correlatable {
    /**
     * "CreateContractRequest"
     * @param requestId A simple correlation ID, generated at the source, opaque to the key components.
     * @param workflowName The name of the flow to be invoked. Preferably in fully qualified form, i.e. containing the relevant package name.
     * @param parameters A flat array of objects representing key-value pairs. The name is expected to equal the flow invocation parameter name. The value provided represents the value to be passed to the flow invocation logic. Note that—in the current implementation—the type of value chosen is irrelevant. All flows will be invoked using strings in line with Corda's `InteractiveShell.runFlowFromString` method
     */
    data class FlowInvocationRequest(
        override val requestId: String,
        val workflowName: String,
        override val parameters: Map<String, String>
    ) : RPCRequest(), Parametrised

    /**
     * "CreateContractActionRequest"
     * @param requestId A simple correlation ID, generated at the source, opaque to the key components
     * @param linearId The linear ID of the input state. This will be used to populate the `linearId` parameter of the flow to be invoked.
     * @param parameters A flat array of objects representing key-value pairs. The name is expected to equal the flow invocation parameter name. The value provided represents the value to be passed to the flow invocation logic. Note that—in the current implementation—the type of value chosen is irrelevant. All flows will be invoked using strings in line with Corda's `InteractiveShell.runFlowFromString` method
     */
    data class InvokeFlowWithInputStates(
        override val requestId: String,
        override val linearId: UniqueIdentifier,
        override val parameters: Map<String, String>
    ) : RPCRequest(), Parametrised, Identifiable

    //
    /**
     * "ReadContractRequest"
     * @param requestId A simple correlation ID, generated at the source, opaque to the key components
     * @param linearId The linear ID of an unconsumed state to be queried
     */
    data class QueryFlowState(
        override val requestId: String,
        override val linearId: UniqueIdentifier
    ) : RPCRequest(), Identifiable
}

sealed class RPCResponse : Correlatable {
    /**
     * "ContractMessage"
     * @param requestId A simple correlation ID, generated in the ingress message
     * @param linearId The linear ID of the output state of the flow invoked
     * @param parameters A flattened serialisation of the parameters of the output state of the transaction or the empty array if the transaction did not have outputs. Flattening is to follow the rules JSON property access notation using dots for named properties and bracket for array positions.
     * @param isNewContract `true` if the transaction had no input states
     */
    data class FlowOutput(
        override val requestId: String,
        override val linearId: UniqueIdentifier,
        override val parameters: Map<String, String>,
        val isNewContract: Boolean
    ) : RPCResponse(), Identifiable, Parametrised
}

/**
 * Can be correlated using a message ID
 */
private interface Correlatable {
    val requestId: String
}

/**
 * Holds a parameter map
 */
private interface Parametrised {
    val parameters: Map<String, String>
}

/**
 * Identified by a linear ID
 */
private interface Identifiable {
    val linearId: UniqueIdentifier
}