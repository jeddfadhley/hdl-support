package dev.sal.hdl

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** Typing `"` pairs quotes, steps over closing quotes, and Backspace removes an empty pair. */
class HdlQuoteHandlerTest : BasePlatformTestCase() {

    // ---- Verilog ----

    fun testVerilogPairs() = type("a.v", "initial \$display(<caret>);", "\"", "initial \$display(\"<caret>\");")

    fun testVerilogStepsOverClosingQuote() =
        type("a.v", "initial \$display(\"hi<caret>\");", "\"", "initial \$display(\"hi\"<caret>);")

    fun testVerilogTypingAWholeString() = type("a.v", "x = <caret>;", "\"hi\"", "x = \"hi\"<caret>;")

    fun testVerilogEscapedQuoteIsNotClosing() =
        type("a.v", "x = \"a\\<caret>\";", "\"", "x = \"a\\\"<caret>\";")

    fun testVerilogNoPairInComment() = type("a.v", "// say <caret>", "\"", "// say \"<caret>")

    fun testVerilogNoPairWhenLineHasOpenString() =
        type("a.v", "x = <caret>hi\";", "\"", "x = \"<caret>hi\";")

    fun testVerilogBackspaceRemovesEmptyPair() {
        myFixture.configureByText("a.v", "x = \"<caret>\";")
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)
        myFixture.checkResult("x = <caret>;")
    }

    // ---- VHDL ----

    fun testVhdlPairs() = type("a.vhd", "s <= <caret>;", "\"", "s <= \"<caret>\";")

    fun testVhdlBitStringPairs() = type("a.vhd", "s <= x<caret>;", "\"", "s <= x\"<caret>\";")

    fun testVhdlBitStringStepsOverClosingQuote() =
        type("a.vhd", "s <= x\"FF<caret>\";", "\"", "s <= x\"FF\"<caret>;")

    fun testVhdlDoubledQuoteIsNotClosing() =
        type("a.vhd", "report \"say \"\"hi\"\"<caret>\";", "\"", "report \"say \"\"hi\"\"\"<caret>;")

    fun testVhdlTickIsNotPaired() = type("a.vhd", "s <= <caret>;", "'", "s <= '<caret>;")

    private fun type(fileName: String, before: String, typed: String, after: String) {
        myFixture.configureByText(fileName, before)
        myFixture.type(typed)
        myFixture.checkResult(after)
    }
}
