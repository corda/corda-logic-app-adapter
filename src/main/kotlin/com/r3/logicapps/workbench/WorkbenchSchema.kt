package com.r3.logicapps.workbench

import org.everit.json.schema.Schema
import org.everit.json.schema.loader.SchemaLoader
import org.json.JSONObject
import org.json.JSONTokener

sealed class WorkbenchSchema(fileName: String) {
    val underlying: Schema

    init {
        underlying = WorkbenchSchema::class.java.getResourceAsStream("/schemata/$fileName").use { inputStream ->
            val rawSchema = JSONObject(JSONTokener(inputStream))
            SchemaLoader.load(rawSchema)
        }
    }

    object FlowInvocationRequestSchema : WorkbenchSchema("flow-invocation-request.schema.json")
    object FlowUpdateRequestSchema : WorkbenchSchema("flow-update-request.schema.json")
    object FlowStateRequestSchema : WorkbenchSchema("flow-state-request.schema.json")

    object FlowInvocationResponseSchema : WorkbenchSchema("flow-invocation-response.schema.json")
}