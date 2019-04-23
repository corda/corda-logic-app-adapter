package com.r3.logicapps.workbench

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter.Indenter

// The default pretty printer uses platform dependent EOL characters
class PlatformIndependentIndenter : Indenter {
    private val NEW_LINE = "\n"
    private val INDENT = "\t"

    override fun isInline() = false

    override fun writeIndentation(g: JsonGenerator, level: Int) {
        g.writeRaw(NEW_LINE)
        g.writeRaw(INDENT.repeat(level))
    }
}