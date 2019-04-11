package com.r3.logicapps

import net.corda.core.contracts.UniqueIdentifier
import kotlin.reflect.KClass

// A message in a format suitable for message processing
sealed class BusRequest : Correlatable {
    /**
     * "CreateContractRequest"
     * @param requestId A simple correlation ID, generated at the source, opaque to the key components.
     * @param workflowName The name of the flow to be invoked. Preferably in fully qualified form, i.e. containing the relevant package name.
     * @param parameters A flat array of objects representing key-value pairs. The name is expected to equal the flow invocation parameter name. The value provided represents the value to be passed to the flow invocation logic. Note that—in the current implementation—the type of value chosen is irrelevant. All flows will be invoked using strings in line with Corda's `InteractiveShell.runFlowFromString` method
     */
    data class InvokeFlowWithoutInputStates(
        override val requestId: String,
        override val workflowName: String,
        override val parameters: Map<String, String>
    ) : BusRequest(), Parameterised, Associated

    /**
     * "CreateContractActionRequest"
     * @param requestId A simple correlation ID, generated at the source, opaque to the key components
     * @param linearId The linear ID of the input state. This will be used to populate the `linearId` parameter of the flow to be invoked.
     * @param parameters A flat array of objects representing key-value pairs. The name is expected to equal the flow invocation parameter name. The value provided represents the value to be passed to the flow invocation logic. Note that—in the current implementation—the type of value chosen is irrelevant. All flows will be invoked using strings in line with Corda's `InteractiveShell.runFlowFromString` method
     */
    data class InvokeFlowWithInputStates(
        override val requestId: String,
        override val linearId: UniqueIdentifier,
        override val workflowName: String,
        override val parameters: Map<String, String>
    ) : BusRequest(), Parameterised, Identifiable, Associated

    /**
     * "ReadContractRequest"
     * @param requestId A simple correlation ID, generated at the source, opaque to the key components
     * @param linearId The linear ID of an unconsumed state to be queried
     */
    data class QueryFlowState(
        override val requestId: String,
        override val linearId: UniqueIdentifier
    ) : BusRequest(), Identifiable
}

sealed class BusResponse : Correlatable {
    /**
     * "ContractMessage"
     * @param ingressType The type of the [BusRequest] that triggered the invocation generating this response.
     * @param requestId A simple correlation ID, generated in the ingress message
     * @param linearId The linear ID of the output state of the flow invoked
     * @param fields A flattened serialisation of the fields of the output state of the transaction or an empty array if the transaction did not have outputs. Flattening is to follow the rules JSON property access notation using dots for named properties and bracket for array positions.
     * @param isNewContract `true` if the transaction had no input states
     */
    data class FlowOutput(
        override val ingressType: KClass<*>,
        override val requestId: String,
        override val linearId: UniqueIdentifier,
        override val fields: Map<String, String>,
        val isNewContract: Boolean
    ) : BusResponse(), Identifiable, WithIngressType, WithOutput

    /**
     * "ContractMessage"
     * @param ingressType The type of the [BusRequest] that triggered the invocation generating this response.
     * @param requestId A simple correlation ID, generated in the ingress message
     * @param exception The exception that was thrown during the processing of the [BusRequest].
     */
    data class FlowError(
        override val ingressType: KClass<*>,
        override val requestId: String,
        val exception: Throwable
    ) : BusResponse(), WithIngressType
}

/**
 * Is associated with a workflow ID
 */
private interface Associated {
    val workflowName: String
}

/**
 * Can be correlated using a message ID
 */
private interface Correlatable {
    val requestId: String
}

/**
 * Identified by a linear ID
 */
private interface Identifiable {
    val linearId: UniqueIdentifier
}

/**
 * Holds a parameter map
 */
private interface Parameterised {
    val parameters: Map<String, String>
}

/**
 * Holds a field map
 */
private interface WithOutput {
    val fields: Map<String, String>
}

/**
 * Holds the type of the request
 */
private interface WithIngressType {
    val ingressType: KClass<*>
}