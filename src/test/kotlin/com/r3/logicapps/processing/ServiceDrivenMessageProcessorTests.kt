package com.r3.logicapps.processing

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.testing.core.TestIdentity
import org.junit.Test

class ServiceDrivenMessageProcessorTests {

    @Test
    fun `returns a flow invocation result for valid output states`() {
        val party1 = TestIdentity(CordaX500Name.parse("O=Member 1, L=London, C=GB"))
        val party2 = TestIdentity(CordaX500Name.parse("O=Member 2, L=London, C=GB"))

        val example = DummyState(
            foo = "bobble",
            bar = 42,
            baz = 77.7,
            nested = Nested("seven", "eleven"),
            linearId = UniqueIdentifier.fromString("6c99cc50-cb00-4932-94cf-98b3f344755a"),
            participants = listOf(
                Party(party1.name, party1.publicKey),
                Party(party2.name, party2.publicKey)
            )
        )

        val actual = listOf(example).toFlowInvocationResult(
            fromName = CordaX500Name.parse("O=Member 1, L=London, C=GB"),
            toNames = listOf(CordaX500Name.parse("O=Member 2, L=London, C=GB")),
            transactionHash = SecureHash.allOnesHash
        )

        val expected = FlowInvocationResult(
            linearId = UniqueIdentifier.fromString("6c99cc50-cb00-4932-94cf-98b3f344755a"),
            fields = mapOf(
                "foo" to "bobble",
                "bar" to "42",
                "baz" to "77.7",
                "nested.lorem" to "seven",
                "nested.ipsum" to "eleven",
                "participants[1]" to "O=Member 1, L=London, C=GB",
                "participants[2]" to "O=Member 2, L=London, C=GB"
            ),
            exception = null,
            fromName = CordaX500Name.parse("O=Member 1, L=London, C=GB"),
            toNames = listOf(CordaX500Name.parse("O=Member 2, L=London, C=GB")),
            hash = SecureHash.allOnesHash
        )

        assertThat(actual, equalTo(expected))
    }

    private class Nested(
        val lorem: String,
        val ipsum: String
    )

    private class DummyState(
        val foo: String,
        val bar: Int,
        val baz: Double,
        val nested: Nested,
        override val linearId: UniqueIdentifier,
        override val participants: List<AbstractParty>
    ) : LinearState
}