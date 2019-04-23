package com.r3.logicapps

import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
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
    ) : BusRequest(), Invocable

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
    ) : BusRequest(), Invocable, Identifiable

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
     * A response to invoking a flow that mirrors the ingress type
     *
     * @param ingressType The type of the [BusRequest] that triggered the invocation generating this response.
     * @param requestId A simple correlation ID, generated in the ingress message
     * @param linearId The linear ID of the output state of the flow invoked
     * @param fields A flattened serialisation of the fields of the output state of the transaction or an empty array if the transaction did not have outputs. Flattening is to follow the rules JSON property access notation using dots for named properties and bracket for array positions.
     * @param isNewContract `true` if the transaction had no input states
     * @param fromName The X509 name of the party that invoked the transaction (i.e. the node's legal identity)
     * @param toNames The participants to the output state of the transactions
     * @param transactionHash The hash of the transaction, if available
     */
    data class FlowOutput(
        override val ingressType: KClass<*>,
        override val requestId: String,
        override val linearId: UniqueIdentifier,
        override val fields: Map<String, String>,
        val isNewContract: Boolean,
        val fromName: CordaX500Name,
        val toNames: List<CordaX500Name>,
        val transactionHash: SecureHash
    ) : BusResponse(), Identifiable, WithIngressType, WithOutput

    /**
     * "ContractMessage": A response to a state query
     *
     * @param requestId A simple correlation ID, generated in the ingress message
     * @param linearId The linear ID of the state
     * @param fields flattened serialisation of the fields of the output state of the transaction or an empty array if the transaction did not have outputs. Flattening is to follow the rules JSON property access notation using dots for named properties and bracket for array positions
     * @param isNewContract `true` if the transaction had no input states
     */
    data class StateOutput(
        override val requestId: String,
        override val linearId: UniqueIdentifier,
        override val fields: Map<String, String>,
        val isNewContract: Boolean
    ) : BusResponse(), Identifiable, WithOutput

    sealed class Error : BusResponse() {
        abstract val cause: Throwable

        /**
         * An unexpected error that occurs irrespective of the validity of user input
         *
         * @param requestId A simple correlation ID, generated in the ingress message
         * @param cause The exception that was thrown during the processing of the [BusRequest].
         */
        data class GenericError(
            override val requestId: String,
            override val cause: Throwable
        ) : Error()

        /**
         * An error returned when the invocation of the flow failed in a predictable way, i.e. due to inappropriate user inputs
         *
         * @param ingressType The type of the [BusRequest] that triggered the invocation generating this response.
         * @param requestId A simple correlation ID, generated in the ingress message
         * @param cause The exception that was thrown during the processing of the [BusRequest].
         * @param linearId The linear id of the input state to the erroneous transaction, if it had one
         */
        data class FlowError(
            override val ingressType: KClass<*>,
            override val requestId: String,
            override val cause: Throwable,
            val linearId: UniqueIdentifier?
        ) : Error(), WithIngressType

    }

    /**
     * A legacy message to be sent whenever a "CreateContractRequest" or a "CreateContractActionRequest" has been
     * received.
     *
     * For legacy reasons, both messages have to be sent.
     */
    sealed class Confirmation : BusResponse(), Identifiable, WithIngressType {
        data class Submitted(
            override val requestId: String,
            override val linearId: UniqueIdentifier,
            override val ingressType: KClass<*>
        ) : Confirmation()

        data class Committed(
            override val requestId: String,
            override val linearId: UniqueIdentifier,
            override val ingressType: KClass<*>
        ) : Confirmation()
    }

    /**
     * "EventMessage"
     */
    data class InvocationState(
        override val requestId: String,
        override val linearId: UniqueIdentifier,
        override val parameters: Map<String, String>,
        val caller: CordaX500Name,
        val flowClass: KClass<*>,
        val fromName: CordaX500Name,
        val toName: CordaX500Name,
        val transactionHash: SecureHash
    ) : BusResponse(), Identifiable, Parameterised
}

/**
 * Is associated with a workflow ID
 */
interface Associated {
    val workflowName: String
}

/**
 * Holds a parameter map
 */
interface Parameterised {
    val parameters: Map<String, String>
}

/**
 * Is associated with a workflow ID and holds a parameter map
 */
interface Invocable : Associated, Parameterised

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