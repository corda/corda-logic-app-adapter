package com.r3.logicapps.processing

import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name

data class FlowInvocationResult(
    val linearId: UniqueIdentifier? = null,
    val fields: Map<String, String> = emptyMap(),
    val exception: Throwable? = null,
    val fromName: CordaX500Name? = null,
    val toNames: List<CordaX500Name> = emptyList(),
    val hash: SecureHash?
)