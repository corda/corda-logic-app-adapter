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
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.logicapps.refrigeratedTransportation.RefrigerationContract
import net.corda.logicapps.refrigeratedTransportation.Shipment

@InitiatingFlow
@StartableByRPC
class TransferResponsibilityFlow(private val linearId: UniqueIdentifier, private val newCounterparty: Party) :
    FlowLogic<SignedTransaction>() {

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


        if (ourIdentity != shipment.initiatingCounterparty) {
            throw IllegalArgumentException("Transfer can only initiated by the initiatingCounterparty")
        }

        if (newCounterparty == shipment.counterparty) {
            throw IllegalArgumentException("Transfer cannot be to the same counterparty")
        }

        progressTracker.currentStep = GENERATING_TRANSACTION
        val cmd = Command(RefrigerationContract.Commands.Transfer(),
            shipment.participants.map { it -> it.owningKey })
        val builder = TransactionBuilder(notary = notary)
            .addInputState(inputStateAndRef)
            .addOutputState(shipment.transferResponsibility(newCounterparty), RefrigerationContract.ID)
            .addCommand(cmd)


        progressTracker.currentStep = VERIFYING_TRANSACTION
        builder.verify(serviceHub)


        progressTracker.currentStep = SIGNING_TRANSACTION
        val ptx = serviceHub.signInitialTransaction(builder)


        progressTracker.currentStep = GATHERING_SIGS
        val sessions = (shipment.participants - ourIdentity).map { initiateFlow(it) }.toSet()
        val stx = subFlow(CollectSignaturesFlow(ptx, sessions, Companion.GATHERING_SIGS.childProgressTracker()))


        progressTracker.currentStep = FINALISING_TRANSACTION
        val finalTx = subFlow(FinalityFlow(stx, Companion.FINALISING_TRANSACTION.childProgressTracker()))



        return finalTx
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

@InitiatedBy(TransferResponsibilityFlow::class)
class TransferResponsibilityFlowResponder(val flowSession: FlowSession) : FlowLogic<Unit>() {
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