package com.r3.logicapps.processing

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.Test

class FlattenWithDotNotationTests {

    @Test
    fun `flattens a JSON structure`() {
        val json = """{
        |  "a": {
        |    "aa": 11,
        |    "aab": {
        |      "aaba": 1121,
        |      "aabb": [
        |        "x",
        |        "y",
        |        [
        |          1,
        |          2,
        |          3,
        |          4,
        |          5,
        |          6,
        |          {
        |            "seven": "sept"
        |          }
        |        ]
        |      ],
        |      "aabc": {
        |        "aabca": "foo"
        |      }
        |    }
        |  }
        |}""".trimMargin()

        val mapper = ObjectMapper()
        val actual = mapper.readTree(json) as ObjectNode

        val expected = mapOf(
            "a.aa" to "11",
            "a.aab.aaba" to "1121",
            "a.aab.aabb[1]" to "x",
            "a.aab.aabb[2]" to "y",
            "a.aab.aabb[3][1]" to "1",
            "a.aab.aabb[3][2]" to "2",
            "a.aab.aabb[3][3]" to "3",
            "a.aab.aabb[3][4]" to "4",
            "a.aab.aabb[3][5]" to "5",
            "a.aab.aabb[3][6]" to "6",
            "a.aab.aabb[3][7].seven" to "sept",
            "a.aab.aabc.aabca" to "foo"
        )

        assertThat(actual.flattenWithDotNotation(), equalTo(expected))
    }
}