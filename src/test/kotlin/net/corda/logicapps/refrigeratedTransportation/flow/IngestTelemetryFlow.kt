package net.corda.logicapps.refrigeratedTransportation.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.logicapps.refrigeratedTransportation.RefrigerationContract
import net.corda.logicapps.refrigeratedTransportation.Shipment

@InitiatingFlow
@StartableByRPC
class IngestTelemetryFlow(
    private val linearId: UniqueIdentifier, private val humidity: Int,
    private val temperature: Int
) : FlowLogic<SignedTransaction>() {
    override val progressTracker = tracker()
    @Suspendable
    override fun call(): SignedTransaction {

        val notary = serviceHub.networkMapCache.notaryIdentities.first()


        progressTracker.currentStep = QUERY_VAULT_BY_LINEARID
        val queryCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))
        val items = serviceHub.vaultService.queryBy<Shipment>(queryCriteria).states

        if (items.isEmpty()) {
            throw IllegalArgumentException("Cannot find a shipment for $linearId")
        }

        val inputStateAndRef = items.first()
        val shipment = inputStateAndRef.state.data

        // This flow can only be initiated by the device .
        if (ourIdentity != shipment.device) {
            throw IllegalArgumentException("Telemetry can only be added by the attached device.")
        }

        // previous counterparty shouldn't need to know about changes
        val actualParticipants = HashSet(shipment.participants)
        if (shipment.previousCounterparty != null && shipment.previousCounterparty != shipment.owner) {
            actualParticipants.remove(shipment.previousCounterparty)
        }

        progressTracker.currentStep = GENERATING_TRANSACTION
        val cmd = Command(RefrigerationContract.Commands.Telemetry(), actualParticipants.map { it -> it.owningKey })
        val builder = TransactionBuilder(notary = notary)
        builder.addInputState(inputStateAndRef)
        val shipmentWithTelemetry = shipment.recordTelemtery(humidity = humidity, temperature = temperature)
        builder.addOutputState(shipmentWithTelemetry, RefrigerationContract.ID)
        builder.addCommand(cmd)


        progressTracker.currentStep = VERIFYING_TRANSACTION
        builder.verify(serviceHub)


        progressTracker.currentStep = SIGNING_TRANSACTION
        val ptx = serviceHub.signInitialTransaction(builder)


        progressTracker.currentStep = GATHERING_SIGS
        val sessions = (actualParticipants - ourIdentity).map { initiateFlow(it) }.toSet()
        val stx = subFlow(CollectSignaturesFlow(ptx, sessions, Companion.GATHERING_SIGS.childProgressTracker()))


        progressTracker.currentStep = FINALISING_TRANSACTION
        return subFlow(FinalityFlow(stx, Companion.FINALISING_TRANSACTION.childProgressTracker()))
    }

    companion object {
        object QUERY_VAULT_BY_LINEARID : ProgressTracker.Step("Retrive the shipment from vault")
        object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on new IOU.")
        object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
        object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
        object OBSERVER : ProgressTracker.Step("Let the observer know")
        object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty's signature.") {
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }

        object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(
            QUERY_VAULT_BY_LINEARID,
            GENERATING_TRANSACTION,
            VERIFYING_TRANSACTION,
            SIGNING_TRANSACTION,
            GATHERING_SIGS,
            FINALISING_TRANSACTION,
            OBSERVER
        )
    }
}

@InitiatedBy(IngestTelemetryFlow::class)
class IngestTelemetryFlowResponder(val flowSession: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        val signedTransactionFlow = object : SignTransactionFlow(flowSession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val output = stx.tx.outputs.single().data
                "This must be a shipment transaction" using (output is Shipment)
            }
        }
        subFlow(signedTransactionFlow)
    }
}
