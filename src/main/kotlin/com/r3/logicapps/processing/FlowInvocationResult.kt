package com.r3.logicapps.processing

import net.corda.core.contracts.UniqueIdentifier

data class FlowInvocationResult(
    val linearId: UniqueIdentifier? = null,
    val fields: Map<String, String> = emptyMap(),
    val exception: Throwable? = null
)