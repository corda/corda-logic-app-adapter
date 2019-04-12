package com.r3.logicapps.workbench

import java.util.Arrays
import java.util.Objects

// net/corda/cliutils/ExceptionsErrorCodeFunctions.kt
fun Throwable.errorCode(hashedFields: (Throwable) -> Array<out Any?> = { throwable: Throwable -> throwable.defaultHashedFields() }): Int =
    staticLocationBasedHash(hashedFields)

private fun Throwable.defaultHashedFields(): Array<out Any?> =
    arrayOf(this::class.java.name, stackTrace?.customHashCode(3) ?: 0)

private fun StackTraceElement.defaultHashedFields(): Array<out Any?> = arrayOf(className, methodName)

private fun Array<StackTraceElement?>.customHashCode(maxElementsToConsider: Int = this.size): Int =
    Arrays.hashCode(take(maxElementsToConsider).map { it?.customHashCode() ?: 0 }.toIntArray())

private fun StackTraceElement.customHashCode(hashedFields: (StackTraceElement) -> Array<out Any?> = { stackTraceElement: StackTraceElement -> stackTraceElement.defaultHashedFields() }): Int =
    Objects.hash(*hashedFields.invoke(this))

private fun Throwable.staticLocationBasedHash(
    hashedFields: (Throwable) -> Array<out Any?>,
    visited: Set<Throwable> = setOf(this)
): Int {

    val cause = this.cause
    val fields = hashedFields.invoke(this)
    return when {
        cause != null && !visited.contains(cause) -> Objects.hash(
            *fields,
            cause.staticLocationBasedHash(hashedFields, visited + cause)
        )
        else                                      -> Objects.hash(*fields)
    }
}