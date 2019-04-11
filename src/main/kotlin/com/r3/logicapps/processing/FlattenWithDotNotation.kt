package com.r3.logicapps.processing

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode

fun JsonNode.flattenWithDotNotation(): Map<String, String> {
    val map = mutableMapOf<String, String>()
    flattenWithDotNotation("", this, map)
    return map
}

private fun flattenWithDotNotation(prefix: String, currentNode: JsonNode, acc: MutableMap<String, String>) {
    when {
        currentNode.isArray  -> {
            val arrayNode = currentNode as ArrayNode
            val node = arrayNode.elements()
            var index = 1
            while (node.hasNext()) {
                flattenWithDotNotation(if (!prefix.isEmpty()) "$prefix[$index]" else index.toString(), node.next(), acc)
                index += 1
            }
        }
        currentNode.isObject -> currentNode.fields().forEachRemaining { entry ->
            flattenWithDotNotation(
                if (!prefix.isEmpty()) prefix + "." + entry.key else entry.key,
                entry.value,
                acc
            )
        }
        else                 -> acc[prefix] = currentNode.asText()
    }
}
