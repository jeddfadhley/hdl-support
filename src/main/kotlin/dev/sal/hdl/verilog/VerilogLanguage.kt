package dev.sal.hdl.verilog

import com.intellij.lang.Language

/**
 * Verilog and SystemVerilog share one language: SystemVerilog is a superset of Verilog,
 * and one lexer handles both.
 *
 * The ID is namespaced so it can't clash with another HDL plugin that registers plain "Verilog".
 */
object VerilogLanguage : Language("HDL.Verilog") {
    @Suppress("unused")
    private fun readResolve(): Any = VerilogLanguage

    override fun getDisplayName(): String = "Verilog/SystemVerilog"

    override fun isCaseSensitive(): Boolean = true
}
