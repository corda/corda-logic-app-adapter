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

The blue components are contained in this repository.

Message Format
--------------

### Ingress Messages (Messages _Consumed_ by the Adapter)

### Egress Messages (Messages _Produced_ by the Adapter)

Caveats
-------

### Compatibility

The CorDapp makes use of Corda 4 features. 

### Threading and Concurrency

The Message Processor will run in a single-threaded mode to maintain order of transactions on the bus.
This means, new messages will only be consumed off the bus once a flow invocation has terminated (successfully or otherwise).

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

### Numeric Attributes

The workbench format uses numbers to denote some unique properties (i.e. `contractId`, `transactionId`).
This assumes enumerability of attributes. 
Since the adapter is not designed to maintain state, these numeric attributes are directly derived from the underlying data.
This means that numbers can get very large (e.g. 128 bit Integers for UUID data).
