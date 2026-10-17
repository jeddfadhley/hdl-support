package dev.sal.hdl

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** Live templates expand with Tab in code, keep the indentation of their line, and stay out of comments. */
class HdlLiveTemplateTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)
    }

    // ---- Verilog ----

    fun testVerilogModuleTakesFileName() = expand(
        "counter.v", "mod<caret>",
        "module counter (\n    input wire clk\n);\n    <caret>\nendmodule",
    )

    fun testVerilogAlwaysFfKeepsIndentation() = expand(
        "a.sv", "module m;\n    aff<caret>\nendmodule\n",
        "module m;\n    always_ff @(posedge clk) begin\n        <caret>\n    end\nendmodule\n",
    )

    fun testVerilogResetMirrorsVariable() = expand(
        "a.sv", "affr<caret>",
        "always_ff @(posedge clk or negedge rst_n) begin\n    if (!rst_n) begin\n        \n" +
            "    end else begin\n        <caret>\n    end\nend",
    )

    fun testVerilogTestbenchKeepsSystemTasks() {
        myFixture.configureByText("tb_counter.sv", "tb<caret>")
        expandAndAccept()
        val text = myFixture.editor.document.text
        assertTrue(text, "\$dumpfile(\"tb_counter.vcd\");" in text)
        assertTrue(text, "\$dumpvars(0, tb_counter);" in text)
        assertTrue(text, "#1000 \$finish;" in text)
    }

    fun testVerilogNotInComment() = notExpanded("a.v", "// aff<caret>")

    fun testVerilogTemplatesNotInVhdl() = notExpanded("a.vhd", "aff<caret>")

    // ---- VHDL ----

    fun testVhdlClockedProcess() = expand(
        "a.vhd", "architecture rtl of e is\nbegin\n    proc<caret>\nend;\n",
        "architecture rtl of e is\nbegin\n    process (clk)\n    begin\n        if rising_edge(clk) then\n" +
            "            <caret>\n        end if;\n    end process;\nend;\n",
    )

    fun testVhdlEntityTakesFileName() = expand(
        "alu.vhd", "ent<caret>",
        "entity alu is\n    port (\n        clk : in std_logic\n    );\nend entity alu;<caret>",
    )

    fun testVhdlNotInComment() = notExpanded("a.vhd", "-- proc<caret>")

    fun testVhdlTemplatesNotInVerilog() = notExpanded("a.v", "proc<caret>")

    private fun expand(fileName: String, before: String, after: String) {
        myFixture.configureByText(fileName, before)
        expandAndAccept()
        myFixture.checkResult(after)
    }

    private fun notExpanded(fileName: String, text: String) {
        myFixture.configureByText(fileName, text)
        myFixture.performEditorAction(IdeActions.ACTION_EXPAND_LIVE_TEMPLATE_BY_TAB)
        assertNull(TemplateManagerImpl.getTemplateState(myFixture.editor))
        assertFalse(myFixture.editor.document.text, "always_ff" in myFixture.editor.document.text)
        assertFalse(myFixture.editor.document.text, "process" in myFixture.editor.document.text)
    }

    /** Expands the abbreviation before the caret and accepts every variable's default. */
    private fun expandAndAccept() {
        myFixture.performEditorAction(IdeActions.ACTION_EXPAND_LIVE_TEMPLATE_BY_TAB)
        val state = TemplateManagerImpl.getTemplateState(myFixture.editor)
        assertNotNull("template didn't expand", state)
        WriteCommandAction.runWriteCommandAction(project) { state!!.gotoEnd(false) }
    }
}
