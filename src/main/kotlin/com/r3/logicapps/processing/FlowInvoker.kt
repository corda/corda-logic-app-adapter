package com.r3.logicapps.processing

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.client.jackson.JacksonSupport
import net.corda.client.jackson.StringToMethodCallParser
import net.corda.core.flows.FlowLogic
import net.corda.core.internal.packageName
import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

object FlowInvoker {
    fun getFlowStatementFromString(
        clazz: Class<out FlowLogic<*>>,
        arguments: Map<String, String>
    ): FlowStatement {
        val om: ObjectMapper = JacksonSupport.createNonRpcMapper(JsonFactory())
        val inputData = om.writerWithDefaultPrettyPrinter().writeValueAsString(arguments).santitized()
        val parser = StringToMethodCallParser(clazz, om)
        val errors = ArrayList<String>()

        val classPackage = clazz.packageName
        for (ctor in clazz.constructors) {
            var paramNamesFromConstructor: List<String>? = null

            fun getPrototype(): List<String> {
                val argTypes = ctor.genericParameterTypes.map { maybeAbbreviateGenericType(it, classPackage) }
                return paramNamesFromConstructor!!.zip(argTypes).map { (name, type) -> "$name: $type" }
            }

            try {
                paramNamesFromConstructor = parser.paramNamesFromConstructor(ctor)
                val args = parser.parseArguments(clazz.name, paramNamesFromConstructor.zip(ctor.genericParameterTypes), inputData)
                if (args.size != ctor.genericParameterTypes.size) {
                    errors.add("${getPrototype()}: Wrong number of arguments (${args.size} provided, ${ctor.genericParameterTypes.size} needed)")
                    continue
                }
                return FlowStatement(clazz = clazz, ctor = ctor, arguments = args)
            } catch (e: StringToMethodCallParser.UnparseableCallException.MissingParameter) {
                errors.add("${getPrototype()}: missing parameter ${e.paramName}")
            } catch (e: StringToMethodCallParser.UnparseableCallException.TooManyParameters) {
                errors.add("${getPrototype()}: too many parameters")
            } catch (e: StringToMethodCallParser.UnparseableCallException.ReflectionDataMissing) {
                val argTypes = ctor.genericParameterTypes.map { it.typeName }
                errors.add("$argTypes: <constructor missing parameter reflection data>")
            } catch (e: StringToMethodCallParser.UnparseableCallException) {
                val argTypes = ctor.genericParameterTypes.map { it.typeName }
                errors.add("$argTypes: ${e.message}")
            }
        }
        return FlowStatement(errors = errors)
    }

    private fun maybeAbbreviateGenericType(type: Type, extraRecognisedPackage: String): String {
        val packagesToAbbreviate = listOf("java.", "net.corda.core.", "kotlin.", extraRecognisedPackage)

        fun shouldAbbreviate(typeName: String) = packagesToAbbreviate.any { typeName.startsWith(it) }
        fun abbreviated(typeName: String) = if (shouldAbbreviate(typeName)) typeName.split('.').last() else typeName

        fun innerLoop(type: Type): String = when (type) {
            is ParameterizedType -> {
                val args: List<String> = type.actualTypeArguments.map(::innerLoop)
                abbreviated(type.rawType.typeName) + '<' + args.joinToString(", ") + '>'
            }
            is GenericArrayType -> innerLoop(type.genericComponentType) + "[]"
            is Class<*> -> if (type.isArray) {
                abbreviated(type.simpleName)
            } else {
                abbreviated(type.name).replace('$', '.')
            }
            else -> type.toString()
        }

        return innerLoop(type)
    }

    private fun String.santitized(): String = trim().removePrefix("{").removeSuffix("}")
}
