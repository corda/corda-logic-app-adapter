package com.r3.logicapps.processing

import net.corda.core.flows.FlowLogic
import java.lang.reflect.Constructor

class FlowStatement(
    val clazz: Class<out FlowLogic<*>>? = null,
    val ctor: Constructor<*>? = null,
    val arguments: Array<out Any?>? = null,
    val errors: List<String> = emptyList()
)