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

### Ingress Messages (Messages _to_ Corda)

### Create Contract Request

This invokes a flow without an input state.

```json
{
  "messageName": "CreateContractRequest",
  "requestId": "4b2c9336-6d16-48b1-99fc-dbafc82cbc1e",
  "workflowName": "RefrigeratedTransportation",
  "parameters": [
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

### Create Contract Action Request

Invoke a flow identified by a linear state ID.

```json
{
  "messageName": "CreateContractActionRequest",
  "requestId": "eca2ce04-6bae-4383-b144-71c3208b6082",
  "contractLedgerIdentifier": "{{ The linear ID of the input state. }}",
  "workflowFunctionName": "IngestTelemetry",
  "parameters": [
    {
      "name": "humidity",
      "value": "95"
    },
    {
      "name": "temperature",
      "value": "32"
    },
    {
      "name": "timestamp",
      "value": "1555337003"
    }
  ],
  "messageSchemaVersion": "1.0.0"
}
```

### Read Contract Request

Read an unconsumed linear state as identified by its linear ID.

```json
{
  "messageName": "ReadContractRequest",
  "requestId": "9c2e532f-15bb-4eb8-ae58-34722c5776f4",
  "contractLedgerIdentifier": "3aa6120b-b809-4cdc-9a19-81546482b313",
  "messageSchemaVersion": "1.0.0"
}
```

### Egress Messages (Messages _from_ Corda)

#### Approval Messages

Two messages are sent as response to a successful flow invocation.

##### Create Contract Update (Submitted)

Message Examples:

 - [WorkbenchAdapterEgressTests.generates a valid committed message.approved](src/test/resources/com/r3/logicapps/workbench/WorkbenchAdapterEgressTests.generates%20a%20valid%20committed%20message.approved)

##### Create Contract Update (Committed)

Message Examples:

 - [WorkbenchAdapterEgressTests.generates a valid committed message.approved](src/test/resources/com/r3/logicapps/workbench/WorkbenchAdapterEgressTests.generates%20a%20valid%20committed%20message.approved)

### Event Message

This message is sent in response to a flow invocation.
It contains the parameters passed to the flow constructor.

Message Examples:

 - [WorkbenchAdapterEgressTests.generates a valid event message.approved](src/test/resources/com/r3/logicapps/workbench/WorkbenchAdapterEgressTests.generates%20a%20valid%20event%20message.approved)

### Contract Message

This message is sent in response to a flow invocation.
It contains the parameters of the output state of the transaction.

This is also returned in response to a "Read Contract Request".

Message Examples:

 - [WorkbenchAdapterEgressTests.generates a valid service bus message for state queries.approved](src/test/resources/com/r3/logicapps/workbench/WorkbenchAdapterEgressTests.generates%20a%20valid%20service%20bus%20message%20for%20state%20queries.approved)
 - [WorkbenchAdapterEgressTests.generates a valid service bus message for a flow output.approved](src/test/resources/com/r3/logicapps/workbench/WorkbenchAdapterEgressTests.generates%20a%20valid%20service%20bus%20message%20for%20a%20flow%20output.approved)

### Error

Any errors are returned with the message name of the message that caused them.

Message Examples:

 - [WorkbenchAdapterEgressTests.generates a valid service bus message for correlatable error output.approved](src/test/resources/com/r3/logicapps/workbench/WorkbenchAdapterEgressTests.generates%20a%20valid%20service%20bus%20message%20for%20correlatable%20error%20output.approved)
 - [WorkbenchAdapterEgressTests.generates a valid service bus message for flow error output.approved](src/test/resources/com/r3/logicapps/workbench/WorkbenchAdapterEgressTests.generates%20a%20valid%20service%20bus%20message%20for%20flow%20error%20output.approved)
 - [WorkbenchAdapterEgressTests.generates a valid service bus message for generic error output.approved](src/test/resources/com/r3/logicapps/workbench/WorkbenchAdapterEgressTests.generates%20a%20valid%20service%20bus%20message%20for%20generic%20error%20output.approved)
 

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

The workbench format uses 64 bit Integers to denote some unique properties (i.e. `contractId`, `transactionId`).
This assumes enumerability of attributes.
The adapter is not designed to maintain state so these attributes can't reasonably be enumerated (i.e. the total number of invoked flows is not trackable).
For this reason, numeric attributes representing unique IDs are directly derived from the underlying data.

| Parameter                | Value                                                                                                            |
|--------------------------|------------------------------------------------------------------------------------------------------------------|
| Contract ID              | The 64 leftmost bits of the SHA256 hash of the underlying linear state's linear ID.                              |
| Block ID, Transaction ID | The 64 leftmost bits of the underlying state's transaction hash (SHA 256).                                       |
| Caller ID                | The 64 leftmost bits of the SHA-256 hash of the X509 name of the primary identity of the node invoking the flow. |

All numbers are emitted as unsigned 64 Bit Integers (i.e. 0..18446744073709551615).
While using truncated message digests has been discussed (i.e. NIST 800-107), it is unclear (to the author) whether this approach still provides the guarantees of a cryptographic hash function.
Users should not rely on the output in the same way they would rely on the output of a cryptographic hash function.
Furthermore, the small range makes of output values makes collisions [more likely](https://preshing.com/20110504/hash-collision-probabilities#small-collision-probabilities).
This suggests users should employ their own methods of de-duplication if necessary. 
