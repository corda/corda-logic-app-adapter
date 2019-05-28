Corda Logic App Connector
=========================

Logic App Connectors ‘provide quick access to events, data, and actions across services, protocols, and platforms’.
The Corda Logic App Connector will support the following _Actions_: 

 1. Create a new Instance of a Contract (M1)
 2. Execute a Function on a Contract (M1)
 3. Read State from a Contract (M1)
 4. Deploy a new Contract (M3)
 
Logic app terminology and Corda terminology do not match well.
Some terms have contradictory meanings in both.
The following table maps use case descriptions from both perspectives.
 
| Logic App Connector Terminology     | Corda Terminology                                                                                                                              | Milestone |
|-------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------|-----------|
| Create a new Instance of a Contract | Invoke a flow returning a `SignedTransaction` that does not consume input states but has exactly one output state                              | M1        |
| Execute a function on a contract    | Invoke a flow taking a `LinearState` as input that is obtained by querying for unconsumed states as identified by the `LinearStateId` provided | M1        |
| Read State from a contract          | Return the latest unconsumed `LinearState` associated with a `LineraStateId` provided.                                                         | M1        |
| Deploy a new Contract               | Deploy a CorDapp                                                                                                                               | M3        |
 
As input parameters it will accept a JSON representation of the data necessary for flow invocation.

The connector will provide the following _Triggers_:
 
 1. An Event Occurs (M2)

| Logic App Connector Terminology | Corda Terminology                                                 | Milestone |
|---------------------------------|-------------------------------------------------------------------|-----------|
| An Event Occurs                 | A new `LinearState` has been observed on the node’s state machine | M2        |
 
The connector will run as a `Corda Service` and as such does not require any connection details but will use intra-process communication.

![Logic App Connector](components.svg)

The colour coding in above figure illustrates the four different classes of components:

 1. _Grey_; External Components
 2. _Blue_; Key Components contained in this repository.
 3. _Red_; External components that are assumed to be deployed independent of any of the connector components.
 4. _Green_; External components that are necessary to serialise and deserialise flow invocation messages in the format dictated by Corda.

Components
----------

### Logic App Connector/Flow Connector

Logic App Connector/Flow Connector are two types of external components that behave the same way from the perspective of the key components.
They will allow end users to place messages targeted at Corda on a service bus. 

### Service Bus

The Service bus allows for ‘highly reliable cloud messaging service between applications and services’.

The mapping between Service Bus components and Corda is as follows:
_Messages_ on a service bus can target multiple Corda _Nodes_.
_Nodes_ are targeted via _Queues_, where one _Queue_ is directed at one Corda _Node_. 

| Corda Concept | Azure Service Bus Concept |
|---------------|---------------------------|
| Set of Nodes  | Service Bus               |
| Node          | Queue                     |

The service bus as well as the queues need to be created manually before the queue consumer/producer are started.

### Service Bus Consumer (M1)

The service bus consumer will consume a message targeted at a queue they have responsibility for (and are thus subscribed to) and pass the message consumed to the message processor.
All errors detected will be surfaced as error messages (similar to the output outlined below).

This component leverages the [Azure Java SDK](https://github.com/Azure/azure-service-bus-java). 

### Workbench Message Adapter (M1)

The workbench message adapter translates the established ‘Workbench’-based format to the basic message format and vice versa.

### Message Processor (M1)

The ‘Message Processor’ is the main contribution in this design.
Once received from the adapter, it will translate a message in the basic message format into a suitable flow invocation format.
Since Corda makes use of type safe binary serialisation for flow invocation, assembling the message necessary is not trivial.
While there are ways of constructing the message necessary to invoke the flow without the ‘flow logic’ class being present, this would introduce the challenge of having to describe the types of flow logic constructor parameters in a different way.

Having the target CorDapp available at runtime, the ‘Message Processor’ uses core Corda functionality (primarily `appServiceHub.startTrackedFlow`) to start the flow.

The out put state of the transaction will—once received—be passed to the service bus producer.

### Recorded Transaction Observer (M2)

This component observes all transactions and will return all recorded transactions—regardless of whether they were invoked through one of the key components and regardless of which role the node observed plays in the transaction.

### Service Bus Publisher (M1)

The services bus producer will place the flow response as received by the Workbench Message Adapter on the service bus, maintaining the correlation ID received initially.

Composition
-----------

Service Bus Consumer, Workbench Message Adapter, Message Processor and Service Bus Publisher (‘Key Components’) form a deployment unit and can be viewed as a single component from external perspective.

Compatibility
-------------

Format compatibility between Corda and Corda Enterprise is subject to the published [compatibility limitations](https://docs.corda.r3.com/version-compatibility.html).
The Message Processor will utilise Corda 4 functionality. 

Threading and Concurrency
-------------------------

The Message Processor will run in a single-threaded mode to maintain order of transactions on the bus.
This means, new messages will only be consumed off the bus once a flow invocation has terminated (successfully or otherwise).

Message Formats
---------------

All formats are informed by work done during the development of the [Corda Blockchain Dev Kit](https://github.com/Azure-Samples/blockchain-devkit/blob/ec4c0500927c5b4c94173c72abdb0d576e291b73/accelerators/corda/service-bus-integration/service-bus-listener/src/test/resources/datasets/refrigeratedTransportation/happyPath/ingress/01-create.json).  
Note the caveats that apply to the type of transactions that can be invoked outlined later.

### Create a new Instance of a Contract

#### Service Bus Ingress Format (`CreateContractRequest`)

This message is sent to invoke a flow that has no input states—a ‘new contract’—in Workbench terminology.

```json
{
  "messageName": "CreateContractRequest",
  "requestId": "00000000-0000-0000-0000-000000000000",
  "workflowName": "RefrigeratedTransportation",
  "parameters": [
    {
      "name": "device",
      "value": "O=Alice, OU=Device 01, L=London, C=GB"
    },
    {
      "name": "supplyChainOwner",
      "value": "O=Bob, L=London, C=GB"
    },
    {
      "name": "supplyChainObserver",
      "value": "O=Charlie, L=London, C=GB"
    },
    {
      "name": "minHumidity",
      "value": "12"
    },
    {
      "name": "maxHumidity",
      "value": "45"
    },
    {
      "name": "minTemperature",
      "value": "-20"
    },
    {
      "name": "maxTemperature",
      "value": "-7"
    }
  ],
  "messageSchemaVersion": "1.0.0"
}
```

The previous example of a message to be consumed from the bus highlights how some of the key concepts are communicated:

 - `messageName`: Always `CreateContractRequest`
 - `requestId`: A simple correlation ID, generated at the source, opaque to the key components.
 - `workflowName`: The name of the flow to be invoked. Preferably in fully qualified form, i.e. containing the relevant package name.
 - `parameters`: A flat array of objects representing key-value pairs. The name is expected to equal the flow invocation parameter name. The value provided represents the value to be passed to the flow invocation logic. Note that—in the current implementation—the type of value chosen is irrelevant. All flows will be invoked using strings in line with Corda's `InteractiveShell.runFlowFromString` method.
 - `messageSchemaVersion`: Always `1.0.0`

This format is formally specified in [`flow-invocation-request.schema.json`](src/main/resources/schemata/flow-invocation-request.schema.json).

#### Service Bus Egress Format (`ContractMessage`)

Normally, each transaction in Corda is a proposal to mark zero or more existing states as historic (the inputs), while creating zero or more new states (the outputs). 
In case flows compatible with this adapter, a more strict model has to apply in which at most one state is used as input/output.
This format is equally modelled after a message format [established earlier](https://github.com/Azure-Samples/blockchain-devkit/blob/ec4c0500927c5b4c94173c72abdb0d576e291b73/accelerators/corda/service-bus-integration/service-bus-listener/src/test/resources/datasets/refrigeratedTransportation/happyPath/egress/01c-transaction.json):

```json
{
  "messageName": "ContractMessage",
  "blockId": 999,
  "blockHash": "B450A0F77BD14239398E5BD57C0FBBA26BB937FD3C19203C1B5937B7D34DA54A",
  "requestId": "00000000-0000-0000-0000-000000000000",
  "additionalInformation": {},
  "contractLedgerIdentifier": "767fd6e0-79cf-4b5e-a9e1-d32e140647c7",
  "contractProperties": [
    {
      "name": "state",
      "value": "Created"
    },
    {
      "name": "owner",
      "value": "O=Alice, L=London, C=GB"
    },
    {
      "name": "initiatingCounterparty",
      "value": "O=Alice, L=London, C=GB"
    },
    {
      "name": "counterparty",
      "value": "O=Alice, L=London, C=GB"
    },
    {
      "name": "previousCounterparty",
      "value": "null"
    },
    {
      "name": "device",
      "value": "OU=Device 01, O=Alice, L=London, C=GB"
    },
    {
      "name": "supplyChainOwner",
      "value": "O=Bob, L=London, C=GB"
    },
    {
      "name": "supplyChainObserver",
      "value": "O=Charlie, L=London, C=GB"
    },
    {
      "name": "minHumidity",
      "value": "12"
    },
    {
      "name": "maxHumidity",
      "value": "45"
    },
    {
      "name": "minTemperature",
      "value": "-20"
    },
    {
      "name": "maxTemperature",
      "value": "-7"
    },
    {
      "name": "complianceSensorType",
      "value": "null"
    },
    {
      "name": "complianceSensorReading",
      "value": "null"
    },
    {
      "name": "complianceStatus",
      "value": "true"
    },
    {
      "name": "complianceDetail",
      "value": "null"
    },
    {
      "name": "lastSensorUpdateTimestamp",
      "value": "null"
    }
  ],
  "modifyingTransactions": [
    {
      "from": "O=Alice, L=London, C=GB",
      "to": [
        "OU=Device 01, O=Alice, L=London, C=GB",
        "O=Bob, L=London, C=GB"
      ],
      "transactionId": 999,
      "transactionHash": "B450A0F77BD14239398E5BD57C0FBBA26BB937FD3C19203C1B5937B7D34DA54A"
    }
  ],
  "contractId": 1,
  "connectionId": 1,
  "messageSchemaVersion": "1.0.0",
  "isNewContract": true
}
```

Here, the following concepts are used to provide outcomes of the flow invocation:

 - `messageName`: Always `ContractMessage`
 - `requestId`: A simple correlation ID, generated in the ingress message.
 - `contractLedgerIdentifier`: The _linear ID_ of the output state of the flow invoked
 - `contractProperties`: A flattened serialisation of the parameters of the output state of the transaction or the empty array if the transaction did not have outputs. Flattening is to follow the rules JSON property access notation using dots for named properties and bracket for array positions.
 - `messageSchemaVersion`: Always `1.0.0`
 - `isNewContract`: `true` if this transaction had no input states.
 
This format is formally specified in [`flow-invocation-response.schema.json`](src/main/resources/schemata/flow-invocation-response.schema.json).
 
 
#### Service Bus Egress Format for Errors

Any errors that occur during flow invocation will be surfaced as error messages on the bus.
If the input that lead to the error referenced a `contractLedgerIdentifier`, this `contractLedgerIdentifier` will be part of the message to allow for correlation.

```json
{
  "messageName": "CreateContractUpdate",
  "requestId": "50adfa06-b41f-40b9-a44f-4319f5837b35",
  "additionalInformation": {
    "errorCode" : 561675734,
    "errorMessage": "Not a valid message for schema class com.r3.logicapps.workbench.WorkbenchSchema$FlowInvocationRequestSchema: #: 4 schema violations found"
  },
  "contractLedgerIdentifier": "c2523b50-85cd-4242-b5e5-f75e80d1fbfd",
  "status": "Failure",
  "messageSchemaVersion": "1.0.0"
}
```

 - `messageName`: The message name of the request leading to the error
 - `requestId`: A simple correlation ID, generated in the ingress message
 - `additionalInformation`.`errorCode`: A unique numeric error code for the exception caught
 - `additionalInformation`.`errorMessage`: A description of the error that occurred
 - `contractLedgerIdentifier` (optional): The linear ID of the input state (if the transaction had one).
 - `status`: Always `Failure`
 - `messageSchemaVersion`: Always `1.0.0`

This format is formally specified in [`flow-error-response.schema.json`](src/main/resources/schemata/flow-error-response.schema.json).

### Execute a Function on a Contract

#### Service Bus Ingress Format (`CreateContractActionRequest`)

This message is sent to invoke a flow that has one input state—an ‘existing contract’—in Workbench terminology.
Note that there are certain requirements on the constructor parameters for this type of flow.

```json
{
  "messageName": "CreateContractActionRequest",
  "requestId": "3dc1e227-d0da-4756-bf24-553256ef38db",
  "contractLedgerIdentifier": "767fd6e0-79cf-4b5e-a9e1-d32e140647c7",
  "workflowFunctionName": "TransferResponsibility",
  "parameters": [
    {
      "name": "newCounterparty",
      "value": "O=Charlie, L=London, C=GB"
    }
  ],
  "messageSchemaVersion": "1.0.0"
}
```

 - `messageName`: Always `CreateContractActionRequest`
 - `requestId`: A simple correlation ID, generated at the source, opaque to the key components
 - `contractLedgerIdentifier`: The _linear ID_ of the input state. This will be used to populate the `linearId` parameter of the flow to be invoked.
 - `workflowFunctionName`: The name of the flow to be invoked. Preferably in fully qualified form, i.e. containing the relevant package name.
 - `parameters`: A flat array of objects representing key-value pairs. The name is expected to equal the flow invocation parameter name. The value provided represents the value to be passed to the flow invocation logic. Note that—in the current implementation—the type of value chosen is irrelevant. All flows will be invoked using strings in line with Corda's `InteractiveShell.runFlowFromString` method.
 - `messageSchemaVersion`: Always `1.0.0`
 
This format is formally specified in [`flow-update-request.schema.json`](src/main/resources/schemata/flow-update-request.schema.json).

#### Service Bus Egress Format (`ContractMessage`)

The response generated by the previous message is structurally identical to the  [`flow-invocation-response.schema.json`](src/main/resources/schemata/flow-invocation-response.schema.json) describe above.

### Read State from a contract

This message format is not part of the original set of [Azure Workbench Messages](https://docs.microsoft.com/en-us/azure/blockchain/workbench/messages-overview).
The format—including the message name—is a proposal.

#### Service Bus Ingress Format (`ReadContractRequest`)

```json
{
  "messageName": "ReadContractRequest",
  "requestId": "9c2e532f-15bb-4eb8-ae58-34722c5776f4",
  "contractLedgerIdentifier": "767fd6e0-79cf-4b5e-a9e1-d32e140647c7",
  "messageSchemaVersion": "1.0.0"
}
```

 - `messageName`: Always `ReadContractRequest`
 - `requestId`: A simple correlation ID, generated at the source, opaque to the key components
 - `contractLedgerIdentifier`: The linear ID of an unconsumed state
 - `messageSchemaVersion`: Always `1.0.0`
 
 This format is formally specified in [`flow-state-request.schema.json`](src/main/resources/schemata/flow-state-request.schema.json).

#### Service Bus Egress Format (`ContractMessage`)

The response generated by the previous message is structurally identical to the  [`flow-invocation-response.schema.json`](src/main/resources/schemata/flow-invocation-response.schema.json) describe above.

### ~~Deploy a new Contract~~

Out of scope for M1.

### ~~An Event Occurs~~

Out of scope for M1.

Caveats
-------

### Flows and Transactions

All flow logic to be invoked using this connector need to implement `FlowLogic<SignedTransaction>`.
To be startable by the Logic App Service, all flow logic needs to be annotated by `@StartableByService`.

To support the query requirements, only transactions that evolve single linear states are supported in the adapter.
All flows invoked must have up to one linear state as input and up to one linear state as output and must not take any other inputs or outputs.
Flows that take a linear state as input need to specify `linearState` as constructor parameter. 

### Serialisation/Deserialisation

The same limitations that apply to the interactive shell apply to inputs to the Logic App connector.
Specifically, around the serialisation rules (specifically the lack of support for polymorphism) and types of flow that are supported. 

In addition to the limitation of having to be `LinearStates`, any output states used have to be JSON serializable.   
Some types may not be JSON serializable using the underlying Jackson serializer.
Those are not supported.

### Durability and Delivery Guarantees

The Message Processor will treat a message as delivered whenever a flow response has been received.
This introduces a problematic ‘at-least-once delivered’ behaviour for the Message Processor.
While this only applies in an edge case—i.e. in a scenario where the Message Processor crashes during flow invocation—this should be targeted in a future release by holding state for correlation IDs received in persistent storage and allowing to query the node for whether the flow has been invoked properly.

The transaction observer is not durable, i.e. messages will not be listened to while the listener is shut down. 

### Uniqueness of attributes

The workbench format uses integers to denote some unique properties (i.e. `contractId`, `transactionId`).
This assumes enumerability of attributes.
Some of those attributes, e.g. `transactionHash`, are _not_ enumerable in Corda.
To make these attributes conform to numeric output, the input domain is mapped to a numeric hash.
This domain is comparably small and bears the risk of hash collisions.
For that reason, equality in `contractId`, `transactionId` or caller `id` is only an indication but _not_ a guarantee equality of the underlying states.
