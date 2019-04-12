package net.corda.logicapps.refrigeratedTransportation

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.CommandWithParties
import net.corda.core.contracts.Contract
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

/**
 * The contract for a Refrigeration Item as it moves around.
 *
 * See https://github.com/Azure-Samples/blockchain/blob/master/blockchain-workbench/application-and-smart-contract-samples/refrigerated-transportation/ethereum/RefrigeratedTransportation.sol
 * for reference model under Ethereum.
 */
class RefrigerationContract : Contract {
    // This is used to identify our contract when building a transaction
    companion object {
        @JvmStatic
        val ID = "net.corda.logicapps.refrigeratedTransportation.RefrigerationContract"
    }

    // A transaction is considered valid if the verify() function of the contract of each of the transaction's input
    // and output states does not throw an exception.
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()

        when (command.value) {
            is Commands.Create    -> validateCreate(tx, command)
            is Commands.Telemetry -> validateTelemetry(tx, command)
            is Commands.Transfer  -> validateTransfer(tx, command)
            is Commands.Complete  -> validateComplete(tx, command)
        }
    }

    private fun validateCreate(tx: LedgerTransaction, command: CommandWithParties<Commands>) {
        requireThat {
            // Generic constraints around the Publish Item  transaction.
            "No inputs should be consumed when publishing an Item." using (tx.inputs.isEmpty())
            "Only one output state should be created when publishing an Item." using (tx.outputs.size == 1)

            // Check the signing
            val out = tx.outputsOfType<Shipment>().single()
            val signers = command.signers.toSet()
            "Owner must sign Create transaction." using (signers.contains(out.owner.owningKey))
            "Supply Chain Owner must sign Create transaction." using (signers.contains(out.supplyChainOwner.owningKey))
            //"Incorrect number of signers." using (signers.size == 2)
            // todo - who else should be sigining

            // Some basic business rules
            "Max temp must be > min temp." using (out.maxTemperature > out.minTemperature)
            "Max humidity must be > min humidity." using (out.maxHumidity > out.minHumidity)


            "Must initially be in Created state." using (out.state == StateType.Created)
        }

        // Run the state validations
        tx.outputsOfType<Shipment>().single().isValidForState()

    }

    private fun validateTelemetry(tx: LedgerTransaction, command: CommandWithParties<Commands>) {
        requireThat {
            // Generic constraints around the Telemetry transaction.
            "Only one input should be consumed when recording telemetry." using (tx.inputs.size == 1)
            "Only one output state should be created when recording telemetry." using (tx.outputs.size == 1)

            // Are we in the correct state
            val input = tx.inputsOfType<Shipment>().single()
            val out = tx.outputsOfType<Shipment>().single()
            "Cannot record telemetry in Completed state." using (input.state != StateType.Completed)
            "Cannot record telemetry in OutOfCompliance state." using (input.state != StateType.OutOfCompliance)
            "Cannot transition to Completed state." using (out.state != StateType.Completed)

            // TODO , should also be checking
            // have only the fields affected by telemetry txn changed
            // has the transaction originated from the input device
            //"Only registered device may record telemetry." using (out.device == tx.StateType.OutOfCompliance)

            // Check the signing
            val signers = command.signers.toSet()
            "Owner must sign Telemetry transaction." using (signers.contains(out.owner.owningKey))
            "Device must sign Telemetry transaction." using (signers.contains(out.device.owningKey))
            "Supply Chain Owner must sign Telemetry transaction." using (signers.contains(out.supplyChainOwner.owningKey))
            // todo - who else should be sigining?

        }

        // Run the state validations
        tx.outputsOfType<Shipment>().single().isValidForState()
    }

    private fun validateTransfer(tx: LedgerTransaction, command: CommandWithParties<Commands>) {
        requireThat {
            // Generic constraints around the Telemetry transaction.
            "Only one input should be consumed when recording telemetry." using (tx.inputs.size == 1)
            "Only one output state should be created when recording telemetry." using (tx.outputs.size == 1)

            // Are we in the correct state
            val input = tx.inputsOfType<Shipment>().single()
            val out = tx.outputsOfType<Shipment>().single()
            "Cannot transfer in Completed state." using (input.state != StateType.Completed)
            "Cannot transfer in OutOfCompliance state." using (input.state != StateType.OutOfCompliance)
            "Must be InTransit state after transfer." using (out.state == StateType.InTransit)

            // TODO , should also be checking
            // have only the fields affected by telemetry txn changed
            // has the transaction originated from the input device
            //"Only registered device may record telemetry." using (out.device == tx.StateType.OutOfCompliance)

            // Check the signing
            val signers = command.signers.toSet()
            "Owner must sign Telemetry transaction." using (signers.contains(out.owner.owningKey))
            "Device must sign Telemetry transaction." using (signers.contains(out.device.owningKey))
            "Supply Chain Owner must sign Telemetry transaction." using (signers.contains(out.supplyChainOwner.owningKey))
            // todo - who else should be sigining?
        }

        // Run the state validations
        tx.outputsOfType<Shipment>().single().isValidForState()

    }

    private fun validateComplete(tx: LedgerTransaction, command: CommandWithParties<Commands>) {
        requireThat {
            // Generic constraints around the Telemetry transaction.
            "Only one input should be consumed when completing shipment." using (tx.inputs.size == 1)
            "Only one output state should be created when completing shipment." using (tx.outputs.size == 1)

            // Are we in the correct state
            val input = tx.inputsOfType<Shipment>().single()
            val out = tx.outputsOfType<Shipment>().single()
            "Can only complete if has In Transit state." using (input.state == StateType.InTransit)

            // TODO , should also be checking
            // have only the fields affected by telemetry txn changed
            // has the transaction originated from the input device
            //"Only registered device may record telemetry." using (out.device == tx.StateType.OutOfCompliance)

            // Check the signing
            val signers = command.signers.toSet()
            "Owner must sign Complete transaction." using (signers.contains(out.owner.owningKey))
            "Supply Chain Owner must sign Complete transaction." using (signers.contains(out.supplyChainOwner.owningKey))
            // todo - who else should be sigining?

        }
    }

    // Used to indicate the transaction's intent.
    interface Commands : CommandData {
        /**
         * Create a new shipment. Will result in 'Created' state
         */
        class Create : TypeOnlyCommandData(), Commands

        /**
         * Add Telemetry. Might change to OutOfCompliance state if the reading is bad
         */
        class Telemetry : TypeOnlyCommandData(), Commands

        /**
         * Transfer the shipment .  Will move to 'InTransit' if not already at that state.
         */
        class Transfer : TypeOnlyCommandData(), Commands

        /**
         * Complete the Shipment. Will result in 'Completed' state
         */
        class Complete : TypeOnlyCommandData(), Commands

    }
}




