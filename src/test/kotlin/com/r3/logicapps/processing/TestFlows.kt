// classes are used via reflection only and might appear as unused in IDEs
@file:Suppress("unused")

package com.r3.logicapps.processing

import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByService

@StartableByService
class SimpleFlow : FlowLogic<SimpleFlow>() {
    override fun call(): SimpleFlow = this
}

@StartableByService
class SimpleFlowWithInput(val a: String, val b: Int, val c: Float, val d: Boolean) : FlowLogic<SimpleFlowWithInput>() {
    override fun call(): SimpleFlowWithInput = this
}