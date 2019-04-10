package com.r3.logicapps.workbench

import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService

@CordaService
class WorkbenchService(
    private val appServiceHub: AppServiceHub
) {

}