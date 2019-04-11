package com.r3.logicapps.processing

import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC

@StartableByRPC
class SimpleFlow : FlowLogic<SimpleFlow>() {
    override fun call(): SimpleFlow = this
}