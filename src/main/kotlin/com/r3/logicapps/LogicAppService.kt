package com.r3.logicapps

import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService

@CordaService
class LogicAppService(
    private val appServiceHub: AppServiceHub
) {

}