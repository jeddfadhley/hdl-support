package dev.sal.hdl.vhdl

import com.intellij.lang.Language

/**
 * VHDL (IEEE 1076). The language is case-insensitive.
 *
 * The ID is namespaced so it can't clash with another HDL plugin that registers plain "VHDL".
 */
object VhdlLanguage : Language("HDL.VHDL") {
    @Suppress("unused")
    private fun readResolve(): Any = VhdlLanguage

    override fun getDisplayName(): String = "VHDL"

    override fun isCaseSensitive(): Boolean = false
}
