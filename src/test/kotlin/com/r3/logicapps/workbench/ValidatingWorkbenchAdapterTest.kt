package com.r3.logicapps.workbench

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.isA
import com.natpryce.hamkrest.throws
import org.junit.Test

class ValidatingWorkbenchAdapterTest {

    @Test
    fun `Transforming unknown messages fails`() {
        val json = """{
            |   "messageName" : "NonSensicalRequest"
            |}""".trimMargin()

        assertThat(
            { ValidatingWorkbenchAdapter().transformIngress(json) },
            throws(isA<IllegalArgumentException>(has(Exception::message, equalTo("Unknown message name"))))
        )
    }
}