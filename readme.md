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
 
The connector will require a Connection that specifies the target address of the running node as well as RPC credentials to be used with the node.

![Logic App Connector](components.svg)

The colour coding in above figure illustrates the four different classes of components:

 1. _Grey_; External Components that are not to be delivered by R3.
 2. _Blue_; Key Components that are to be delivered by R3. Likely to be delivered in a single deployment unit but structured as different modules internally.
 3. _Red_; External components that are assumed to be deployed independent of any of the connector components.
 4. _Green_; External components that are necessary to serialise and deserialise RPC messages in the format dictated by Corda.

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
Different _CorDapps_ deployed to a node are targeted using _Topics_.

| Corda Concept | Azure Service Bus Concept |
|---------------|---------------------------|
| Set of Nodes  | Service Bus               |
| Node          | Queue                     |
| Flow          | Topic                     |

The service bus as well as the queues need to be created manually before the queue consumer/producer are started.

### Service Bus Consumer (M1)

The service bus consumer will consume a message targeted at a channel and topic they have responsibility for (and are thus subscribed to) and pass the message consumed to the RPC invoker.
We distinguish between the following error cases that can occur following the consumption of a message:

 1. A message is malformed and cannot be parsed.
 2. A flow cannot be invoked using a message because an error occurred during RPC invocation (i.e. no connectivity to the node via RPC).
 3. Flow invocation times out.
 4. A flow cannot be invoked because the invoked flow logic throws an exception (e.g. inapplicable parameters passed).

Cases 1, 2 and 3 should be retried and ultimately be placed in a dead letter queue, messages relating to 4 should be placed in a dead letter queue immediately when observed.
This component leverages the [Azure Java SDK](https://github.com/Azure/azure-service-bus-java). 

### Workbench Message Adapter (M1)

The workbench message adapter translates the established ‘Workbench’-based format to the basic message format and vice versa.

### RPC Invoker (M1)

The ‘RPC Invoker’ is the main contribution in this design.
Once received from the adapter, it will translate a message in the basic message format into a suitable RPC format.
Since Corda makes use of type safe binary serialisation for flow invocation, assembling the message necessary is not trivial.
While there are ways of constructing the message necessary to invoke the flow without the ‘flow logic’ class being present, this would introduce the challenge of having to describe the types of flow logic constructor parameters in a different way.

Having the target CorDapp available at runtime, the ‘RPC Invoker’ uses core Corda functionality (primarily `net.corda.client.rpc` and `net.corda.tools.shell`) to start the flow.
The topic of the message received from the bus will inform the flow to invoke, the target node will be determined by configuration supplied to the ‘RPC Invoker’ process when started.

Additional configuration parameters on the ‘RPC Invoker’ will allow for setting a timeout when invoking flows.

The flow response will—once received—be passed to the service bus producer.

Corda authentication (via RPC authentication) will be performed by the RPC Invoker with credentials provided when starting the service.

### Recorded Transaction Observer (M2)

This component observes all transactions and will return all recorded transactions—regardless of whether they were invoked through one of the key components and regardless of which role the node observed plays in the transaction.

### Service Bus Publisher (M1)

The services bus producer will place the flow response as received by the Workbench Message Adapter on the service bus, maintaining the correlation ID received initially.

Composition
-----------

Service Bus Consumer, Workbench Message Adapter, RPC Invoker and Service Bus Publisher (‘Key Components’) form a deployment unit and can be viewed as a single component from external perspective.

Compatibility
-------------

RPC format compatibility between Corda and Corda Enterprise is subject to the published [compatibility limitations](https://docs.corda.r3.com/version-compatibility.html).
The RPC invoker will utilise Corda 4 functionality. 
Nodes targeted will run Corda 4 as well.

Threading and Concurrency
-------------------------

The RPC invoker will run in a single-threaded mode to maintain order of transactions on the bus.
This means, new messages will only be consumed off the bus once a flow invocation has terminated (successfully or otherwise).

Message Formats
---------------

### Create a new Instance of a Contract

#### Service Bus Ingress Format

This is the format the messages consumed will have. 
The format is informed by work done during the development of the [Corda Blockchain Dev Kit](https://github.com/Azure-Samples/blockchain-devkit/blob/ec4c0500927c5b4c94173c72abdb0d576e291b73/accelerators/corda/service-bus-integration/service-bus-listener/src/test/resources/datasets/refrigeratedTransportation/happyPath/ingress/01-create.json). 
To ease development on caller side, the ingress format will be closely aligned with this format even though some of the properties present are irrelevant for the underlying RPC call: 

Note the caveats that apply to the type of transactions that can be invoked outlined later.

```json
{
  "messageName": "ContractMessage",
  "requestId": "81a87eb0-b5aa-4d53-a39f-a6ed0742d90d",
  "workflowName": "net.corda.workbench.refrigeratedTransportation.flow.CreateFlow",
  "parameters": [
    {
      "name": "state",
      "value": "Created"
    },
    {
      "name": "owner",
      "value": "O=Alice Ltd., L=Shanghai, C=CN"
    },
    {
      "name": "initiatingCounterparty",
      "value": "O=Bob Ltd., L=Beijing, C=CN"
    },
    {
      "name": "device",
      "value": "O=Charly GmbH, OU=Device01, L=Berlin, C=DE"
    },
    {
      "name": "supplyChainOwner",
      "value": "O=Denise SARL, L=Marseille, C=FR"
    },
    {
      "name": "supplyChainObserver",
      "value": "O=Denise SARL, L=Marseille, C=FR"
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

 - `messageName`: Always `ContractMessage`
 - `requestId`: A simple correlation ID, generated at the source, opaque to the key components.
 - `workflowName`: The name of the flow to be invoked. Preferably in fully qualified form, i.e. containing the relevant package name.
 - `parameters`: An flat array of objects representing key-value pairs. The name is expected to equal the flow invocation parameter name. The value provided represents the value to be passed to the flow invocation logic. Note that—in the current implementation—the type of value chosen is irrelevant. All flows will be invoked using strings in line with Corda's `InteractiveShell.runFlowFromString` method.
 - `messageSchemaVersion`: Always `1.0.0`

This format is formally specified in [`flow-invocation-request.schema.json`](src/main/resources/schemata/flow-invocation-request.schema.json).

#### Service Bus Egress Format

Normally, each transaction in Corda is a proposal to mark zero or more existing states as historic (the inputs), while creating zero or more new states (the outputs). 
In case flows compatible with this adapter, a more strict model has to apply in which at most one state is used as input/output.
This format is equally modelled after a message format [established earlier](https://github.com/Azure-Samples/blockchain-devkit/blob/ec4c0500927c5b4c94173c72abdb0d576e291b73/accelerators/corda/service-bus-integration/service-bus-listener/src/test/resources/datasets/refrigeratedTransportation/happyPath/egress/01c-transaction.json):

```json
{
  "messageName": "ContractMessage",
  "requestId": "81a87eb0-b5aa-4d53-a39f-a6ed0742d90d",
  "additionalInformation": {},
  "contractLedgerIdentifier": "f1a27656-3b1a-4469-8e37-04d9e2764bf6",
  "contractProperties": [
    {
      "name": "state",
      "value": "Created"
    },
    {
      "name": "owner",
      "value": "O=Alice Ltd., L=Shanghai, C=CN"
    }
  ],
  "messageSchemaVersion": "1.0.0",
  "isNewContract": true
}
```

Here, the following concepts are used to provide outcomes of the flow invocation:

 - `messageName`: Always `ContractMessage`
 - `requestId`: A simple correlation ID, generated in the ingress message.
 - `contractLedgerIdentifier`: The _linear ID_ of the input and output state (if a transaction has both an input and an output). The _linear ID_ of the input _or_ output state if the transaction has only one.
 - `contractProperties`: A flattened serialisation of the parameters of the output state of the transaction or the empty array if the transaction did not have outputs. Flattening is to follow the rules JSON property access notation using dots for named properties and bracket for array positions.
 - `messageSchemaVersion`: Always `1.0.0`
 - `isNewContract`: `true` if this transaction had no input states.
 
This format is formally specified in [`flow-invocation-response.schema.json`](src/main/resources/schemata/flow-invocation-response.schema.json).
 
### Execute a Function on a Contract

_To be defined_
  
### Read State from a contract

_To be defined_

### Deploy a new Contract

_To be defined_

### An Event Occurs

_To be defined_

Caveats
-------

To support the query requirements stated, only transactions that evolve single states linearly (sharing a common linearId, i.e. `LinearStates`) are supported in the adapter.
All flows invoked must have up to one linear state as input and up to one linear state as output and must not take any other inputs or outputs.

The same limitations that apply to the interactive shell apply to inputs to the Logic App connector.
Specifically, around the serialisation rules and types of flow that are supported. 

In addition to the limitation of having to be `LinearStates`, any output states used have to be JSON serializable.   
Some types may not be JSON serializable using the underlying Jackson serializer.
Those are not supported.

The RPC Invoker will treat a message as delivered whenever a flow response has been received.
This introduces a problematic ‘at-least-once delivered’ behaviour for the RPC Invoker.
While this only applies in an edge case—i.e. in a scenario where the invoker crashes during flow invocation—this should be targeted in a future release by holding state for correlation IDs received in persistent storage and allowing to query the node for whether the flow has been invoked properly.

The transaction observer is not durable, i.e. messages will not be listened to while the listener is shut down. 
