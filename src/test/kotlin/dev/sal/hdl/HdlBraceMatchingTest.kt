package dev.sal.hdl

import com.intellij.codeInsight.highlighting.CodeBlockSupportHandler
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** Keyword matching (begin/end, if/elsif/end if, ...) and bracket matching. */
class HdlBraceMatchingTest : BasePlatformTestCase() {

    // ---- Keyword markers highlighted together ----

    fun testVerilogBeginEnd() = assertMarkers(
        "a.v",
        "module m; initial <caret>begin : blk x = 1; end : blk endmodule",
        "begin", "end",
    )

    fun testVerilogModuleFromEndmodule() = assertMarkers(
        "a.v",
        "module m; initial begin end <caret>endmodule",
        "module", "endmodule",
    )

    fun testVerilogCaseAndForkJoin() {
        assertMarkers("a.sv", "module m; always_comb <caret>case (s) 0: x = 1; endcase endmodule", "case", "endcase")
        assertMarkers("a.sv", "module m; initial fork #1 a = 1; <caret>join_any endmodule", "fork", "join_any")
    }

    fun testVerilogNonMarkerKeyword() = assertMarkers("a.v", "module m; <caret>wire w; endmodule")

    fun testVhdlIfElsifElse() = assertMarkers(
        "a.vhd",
        "process begin if a then x; <caret>elsif b then y; else z; end if; end process;",
        "if", "then", "elsif", "then", "else", "end", "if",
    )

    fun testVhdlProcessIgnoresNestedAndInlineKeywords() = assertMarkers(
        "a.vhd",
        "p : <caret>process (clk) begin if c then x <= a when s else b; end if; end process p;",
        "process", "begin", "end", "process",
    )

    fun testVhdlCaseWhen() = assertMarkers(
        "a.vhd",
        "process begin <caret>case s is when A => null; when others => null; end case; end process;",
        "case", "is", "when", "when", "end", "case",
    )

    fun testVhdlPackageBody() = assertMarkers(
        "a.vhd",
        "package body p is end <caret>package body p;",
        "package", "body", "end", "package", "body",
    )

    // ---- Move Caret to Matching Brace (Ctrl+Shift+M) ----
    // As in other languages, the caret lands just after the matching keyword or bracket.

    fun testMatchBraceJumpsFromBeginToEnd() {
        myFixture.configureByText("a.v", "module m; initial <caret>begin x = 1; end endmodule")
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MATCH_BRACE)
        myFixture.checkResult("module m; initial begin x = 1; end<caret> endmodule")
    }

    fun testMatchBraceJumpsBetweenParentheses() {
        myFixture.configureByText("a.vhd", "x <= f<caret>(a, (b));")
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MATCH_BRACE)
        myFixture.checkResult("x <= f(a, (b))<caret>;")
    }

    fun testMatchBraceJumpsBetweenVerilogBraces() {
        myFixture.configureByText("a.v", "assign y = <caret>{a, {2{b}}};")
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MATCH_BRACE)
        myFixture.checkResult("assign y = {a, {2{b}}}<caret>;")
    }

    /** Puts the caret where `<caret>` is and checks which keywords are highlighted together. */
    private fun assertMarkers(fileName: String, text: String, vararg expected: String) {
        val file = myFixture.configureByText(fileName, text)
        val ranges = CodeBlockSupportHandler.findMarkersRanges(file, file.language, myFixture.caretOffset)
        assertEquals(expected.toList(), ranges.map { it.substring(file.text) })
    }
}
