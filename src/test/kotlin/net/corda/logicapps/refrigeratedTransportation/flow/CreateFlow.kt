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
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.logicapps.refrigeratedTransportation.RefrigerationContract
import net.corda.logicapps.refrigeratedTransportation.Shipment

/**
 * A wrapper compatible with Azure Workbench, which
 * needs a single list of params and a TxnResult / TxnResultTyped
 * return type
 */
@InitiatingFlow
@StartableByRPC
class CreateFlow(
    private val device: Party,
    private val supplyChainOwner: Party,
    private val supplyChainObserver: Party,
    private val minHumidity: Int,
    private val maxHumidity: Int,
    private val minTemperature: Int,
    private val maxTemperature: Int
) : FlowLogic<SignedTransaction>() {

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {

        val state = Shipment(
            owner = ourIdentity,
            device = device,
            supplyChainObserver = supplyChainObserver,
            supplyChainOwner = supplyChainOwner,
            minHumidity = minHumidity,
            maxHumidity = maxHumidity,
            minTemperature = minTemperature,
            maxTemperature = maxTemperature,
            linearId = UniqueIdentifier()
        )

        // simplest way of finding a notary
        val notary = serviceHub.networkMapCache.notaryIdentities.first()

        progressTracker.currentStep = GENERATING_TRANSACTION

        // build txn
        val cmd = Command(RefrigerationContract.Commands.Create(), state.participants.map { it -> it.owningKey })
        val builder = TransactionBuilder(notary = notary)
            .addOutputState(state, RefrigerationContract.ID)
            .addCommand(cmd)

        // verify and sign
        progressTracker.currentStep = VERIFYING_TRANSACTION
        builder.verify(serviceHub)

        progressTracker.currentStep = SIGNING_TRANSACTION
        val ptx = serviceHub.signInitialTransaction(builder)

        // make sure everyone else signs
        progressTracker.currentStep = GATHERING_SIGS
        val signers = (state.participants - ourIdentity)
        val sessions = signers.map { initiateFlow(it) }
        val stx = subFlow(CollectSignaturesFlow(ptx, sessions))

        // complete and notarise
        progressTracker.currentStep = FINALISING_TRANSACTION
        return subFlow(FinalityFlow(stx))
    }

    companion object {
        object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on new IOU.")
        object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
        object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
        object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty's signature.") {
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }

        object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(
            GENERATING_TRANSACTION,
            VERIFYING_TRANSACTION,
            SIGNING_TRANSACTION,
            GATHERING_SIGS,
            FINALISING_TRANSACTION
        )
    }
}

/**
 *
 */
@InitiatedBy(CreateFlow::class)
class CreateFlowResponder(val flowSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val signedTransactionFlow = object : SignTransactionFlow(flowSession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val output = stx.tx.outputs.single().data
                "This must be a Shipment " using (output is Shipment)
            }
        }
        subFlow(signedTransactionFlow)
    }
}


