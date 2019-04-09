package com.r3.logicapps

// A message in a format suitable for RPC invocation
sealed class RPCRequest(val parameters: Map<String, String>) {
    // The Workbench `CreateContractRequest`
    class InvokeFlowWithoutInputStates(content: Map<String, String>) : RPCRequest(content)

    // The Workbench `CreateContractActionRequest`
    class InvokeFlowWithInputStates(content: Map<String, String>) : RPCRequest(content)

    // Unspecified Workbench Request
    // TODO moritzplatt 2019-04-09 -- need clarification on "Message Name"
    class QueryFlowState(parameters: Map<String, String>) : RPCRequest(parameters)
}

// TODO moritzplatt 2019-04-09 -- Need to understand the various message types outlined in https://github.com/Azure-Samples/blockchain-devkit/blob/ec4c0500927c5b4c94173c72abdb0d576e291b73/accelerators/corda/service-bus-integration/service-bus-listener/src/test/resources/datasets/refrigeratedTransportation/happyPath/egress/01c-transaction.json
sealed class RPCResponse(val parameters: Map<String, String>) {
    class FlowOutput(parameters: Map<String, String>, val transactionId: String) : RPCResponse(parameters)
}
