package com.r3.logicapps.workbench

import com.oneeyedmen.okeydoke.junit.ApprovalsRule
import com.r3.logicapps.BusRequest.InvokeFlowWithoutInputStates
import com.r3.logicapps.BusResponse.Confirmation.Committed
import com.r3.logicapps.BusResponse.Confirmation.Submitted
import com.r3.logicapps.BusResponse.Error.CorrelatableError
import com.r3.logicapps.BusResponse.Error.FlowError
import com.r3.logicapps.BusResponse.Error.GenericError
import com.r3.logicapps.BusResponse.FlowOutput
import com.r3.logicapps.BusResponse.InvocationState
import com.r3.logicapps.BusResponse.StateOutput
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.CordaX500Name
import org.junit.Rule
import org.junit.Test

class WorkbenchAdapterEgressTests {

    @Rule
    @JvmField
    val approval: ApprovalsRule = ApprovalsRule.fileSystemRule("src/test/resources")
    private val adapter = WorkbenchAdapterImpl(1)

    @Test
    fun `generates a valid service bus message for a flow output`() {
        val actual = adapter.transformEgress(
            FlowOutput(
                InvokeFlowWithoutInputStates::class,
                "81a87eb0-b5aa-4d53-a39f-a6ed0742d90d",
                UniqueIdentifier.fromString("f1a27656-3b1a-4469-8e37-04d9e2764bf6"),
                mapOf(
                    "state" to "Created",
                    "owner" to "O=Alice Ltd., L=Shanghai, C=CN"
                ),
                false,
                CordaX500Name.parse("O=Member 1, L=London, C=GB"),
                listOf(
                    CordaX500Name.parse("O=Member 2, L=Manchester, C=GB"),
                    CordaX500Name.parse("O=Member 3, L=Bristol, C=GB")
                ),
                SecureHash.allOnesHash
            )
        )

        approval.assertApproved(actual)
    }

    @Test
    fun `generates a valid service bus message for flow error output`() {
        val actual = adapter.transformEgress(
            FlowError(
                ingressType = InvokeFlowWithoutInputStates::class,
                requestId = "7d4ce6d9-554c-4bd0-acc8-b04cdef298f9",
                cause = IllegalStateException("Boooom!"),
                linearId = UniqueIdentifier.fromString("27b3b7ad-10ce-4bd4-a72c-1bf215709a21")
            )
        )

        approval.assertApproved(actual)
    }

    @Test
    fun `generates a valid service bus message for generic error output`() {
        val actual = adapter.transformEgress(
            GenericError(IllegalStateException("Whaam!"))
        )

        approval.assertApproved(actual)
    }

    @Test
    fun `generates a valid service bus message for correlatable error output`() {
        val actual = adapter.transformEgress(
            CorrelatableError(
                cause = IllegalStateException("Boooom!"),
                requestId = "7d4ce6d9-554c-4bd0-acc8-b04cdef298f9"
            )
        )

        approval.assertApproved(actual)
    }

    @Test
    fun `generates a valid service bus message for state queries`() {
        val actual = adapter.transformEgress(
            StateOutput(
                requestId = "7d4ce6d9-554c-4bd0-acc8-b04cdef298f9",
                linearId = UniqueIdentifier.fromString("27b3b7ad-10ce-4bd4-a72c-1bf215709a21"),
                fields = mapOf(
                    "lorem" to "ipsum",
                    "dolor" to "sit amet"
                ),
                isNewContract = false
            )
        )

        approval.assertApproved(actual)
    }

    @Test
    fun `generated a valid submitted message`() {
        val actual = adapter.transformEgress(
            Submitted(
                ingressType = InvokeFlowWithoutInputStates::class,
                requestId = "7d4ce6d9-554c-4bd0-acc8-b04cdef298f9",
                linearId = UniqueIdentifier.fromString("27b3b7ad-10ce-4bd4-a72c-1bf215709a21")
            )
        )

        approval.assertApproved(actual)
    }

    @Test
    fun `generates a valid committed message`() {
        val actual = adapter.transformEgress(
            Committed(
                ingressType = InvokeFlowWithoutInputStates::class,
                requestId = "7d4ce6d9-554c-4bd0-acc8-b04cdef298f9",
                linearId = UniqueIdentifier.fromString("27b3b7ad-10ce-4bd4-a72c-1bf215709a21")
            )
        )

        approval.assertApproved(actual)
    }

    @Test
    fun `generates a valid event message`() {
        val actual = adapter.transformEgress(
            InvocationState(
                requestId = "7d4ce6d9-554c-4bd0-acc8-b04cdef298f9",
                linearId = UniqueIdentifier.fromString("27b3b7ad-10ce-4bd4-a72c-1bf215709a21"),
                parameters = mapOf(
                    "one" to "eins",
                    "two" to "zwei",
                    "three" to "drei"
                ),
                caller = CordaX500Name.parse("O=Member 1, L=London, C=GB"),
                flowClass = NonSenseFlow::class,
                transactionHash = SecureHash.allOnesHash
            )
        )

        approval.assertApproved(actual)
    }

    private class NonSenseFlow : FlowLogic<String>() {
        override fun call(): String = TODO()
    }
}
