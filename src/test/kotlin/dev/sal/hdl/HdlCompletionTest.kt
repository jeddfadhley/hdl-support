package dev.sal.hdl

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.sal.hdl.completion.HdlKeywords
import dev.sal.hdl.verilog.VerilogLexerAdapter
import dev.sal.hdl.verilog.VerilogTokenTypes
import dev.sal.hdl.vhdl.VhdlLexerAdapter
import dev.sal.hdl.vhdl.VhdlTokenTypes

/** Code completion for Verilog and VHDL. */
class HdlCompletionTest : BasePlatformTestCase() {

    // ---- Keyword lists ----

    fun testKeywordListsMatchTheLexers() {
        assertTrue(HdlKeywords.VERILOG.size > 200)
        for (word in HdlKeywords.VERILOG) assertEquals(word, VerilogTokenTypes.KEYWORD, verilogToken(word))
        assertTrue(HdlKeywords.VHDL.size > 100)
        for (word in HdlKeywords.VHDL) assertEquals(word, VhdlTokenTypes.KEYWORD, vhdlToken(word))
        assertTrue(HdlKeywords.VHDL_STD_TYPES.isNotEmpty())
        for (word in HdlKeywords.VHDL_STD_TYPES) assertEquals(word, VhdlTokenTypes.STD_TYPE, vhdlToken(word))
    }

    // ---- Verilog ----

    fun testVerilogKeyword() {
        myFixture.configureByText("a.sv", "module m; always_c<caret> endmodule\n")
        myFixture.completeBasic()
        myFixture.checkResult("module m; always_comb<caret> endmodule\n")
    }

    fun testVerilogKeywordsWithSharedPrefix() {
        myFixture.configureByText("a.sv", "module m; always<caret> endmodule\n")
        val items = complete()
        assertContainsElements(items, "always", "always_comb", "always_ff", "always_latch")
    }

    fun testVerilogDeclaredNames() {
        myFixture.configureByText(
            "a.v",
            """
            module m #(parameter WIDTH = 8) (input clk, output [7:0] count_out);
              reg [7:0] count_reg;
              wire count_next;
              assign count_out = cou<caret>;
            endmodule
            """.trimIndent(),
        )
        assertContainsElements(complete(), "count_out", "count_reg", "count_next")
    }

    fun testVerilogNamesComeBeforeKeywords() {
        myFixture.configureByText("a.v", "module m; wire enable_x; assign y = en<caret>; endmodule\n")
        val items = complete()
        assertTrue(items.toString(), items.indexOf("enable_x") < items.indexOf("endmodule"))
    }

    fun testVerilogModuleFromAnotherFile() {
        myFixture.addFileToProject("counter.v", "module Counter(input clk); endmodule\n")
        myFixture.configureByText("tb.sv", "module tb; Coun<caret> endmodule\n")
        myFixture.completeBasic()
        myFixture.checkResult("module tb; Counter<caret> endmodule\n")
    }

    fun testVerilogPortsOfInstantiatedModule() {
        myFixture.addFileToProject("counter.v", "module counter(input clk, input rst_n, output [3:0] q); endmodule\n")
        myFixture.configureByText("tb.sv", "module tb; logic local_sig; counter u (.<caret>); endmodule\n")
        val items = complete()
        assertSameElements(items, "clk", "rst_n", "q")
    }

    fun testVerilogSystemTask() {
        myFixture.configureByText("a.sv", "module m; initial \$disp<caret> endmodule\n")
        val items = complete()
        assertContainsElements(items, "\$display", "\$displayb", "\$displayh")
        assertDoesntContain(items, "always")
    }

    fun testVerilogSystemTaskInserted() {
        myFixture.configureByText("a.sv", "module m; localparam W = \$clo<caret>; endmodule\n")
        myFixture.completeBasic()
        myFixture.checkResult("module m; localparam W = \$clog2<caret>; endmodule\n")
    }

    fun testVerilogDirectivesAndMacros() {
        myFixture.configureByText("a.sv", "`define DATA_WIDTH 8\n`<caret>\n")
        assertContainsElements(complete(), "`define", "`timescale", "`ifdef", "`DATA_WIDTH")
    }

    fun testVerilogDirectiveInserted() {
        myFixture.configureByText("a.sv", "`times<caret>\n")
        myFixture.completeBasic()
        myFixture.checkResult("`timescale<caret>\n")
    }

    fun testVerilogNothingInComments() {
        myFixture.configureByText("a.v", "module m; // alw<caret>\nendmodule\n")
        assertEmpty(complete())
    }

    fun testVerilogNothingInStrings() {
        myFixture.configureByText("a.v", "module m; initial \$display(\"alw<caret>\"); endmodule\n")
        assertEmpty(complete())
    }

    // ---- VHDL ----

    fun testVhdlKeyword() {
        myFixture.configureByText("a.vhd", "archit<caret>\n")
        myFixture.completeBasic()
        myFixture.checkResult("architecture<caret>\n")
    }

    fun testVhdlUpperCaseKeyword() {
        myFixture.configureByText("a.vhd", "ARCHIT<caret>\n")
        myFixture.completeBasic()
        myFixture.checkResult("ARCHITECTURE<caret>\n")
    }

    fun testVhdlStdType() {
        myFixture.configureByText("a.vhd", "entity e is port (clk : in std_ul<caret>); end;\n")
        assertContainsElements(complete(), "std_ulogic", "std_ulogic_vector")
    }

    fun testVhdlDeclaredNamesIgnoreCase() {
        myFixture.configureByText(
            "a.vhd",
            """
            entity e is port (Clk_In : in std_logic); end;
            architecture rtl of e is
              signal Count_Reg : unsigned(3 downto 0);
              signal count_next : unsigned(3 downto 0);
            begin
              x <= cou<caret>;
            end;
            """.trimIndent(),
        )
        assertContainsElements(complete(), "Count_Reg", "count_next")
    }

    fun testVhdlAttributes() {
        myFixture.configureByText("a.vhd", "architecture a of e is begin x <= bus_in'leng<caret>; end;\n")
        myFixture.completeBasic()
        myFixture.checkResult("architecture a of e is begin x <= bus_in'length<caret>; end;\n")
    }

    fun testVhdlAttributesAfterTick() {
        myFixture.configureByText("a.vhd", "architecture a of e is begin x <= s'<caret>; end;\n")
        assertContainsElements(complete(), "event", "length", "range", "high")
    }

    fun testVhdlEntityAfterWork() {
        myFixture.addFileToProject("counter.vhd", "entity counter is port (clk : in bit); end;\n")
        myFixture.configureByText(
            "top.vhd",
            "architecture a of top is begin u : entity work.cou<caret>; end;\n",
        )
        myFixture.completeBasic()
        myFixture.checkResult("architecture a of top is begin u : entity work.counter<caret>; end;\n")
    }

    fun testVhdlPortsOfInstantiatedEntityComeFirst() {
        myFixture.addFileToProject("counter.vhd", "entity counter is port (clk, rst : in bit; q : out bit); end;\n")
        myFixture.configureByText(
            "top.vhd",
            "architecture a of top is begin u : entity work.counter port map (<caret>); end;\n",
        )
        val items = complete()
        assertEquals(items.toString(), setOf("clk", "rst", "q"), items.take(3).toSet())
    }

    fun testVhdlNothingInComments() {
        myFixture.configureByText("a.vhd", "-- archit<caret>\n")
        assertEmpty(complete())
    }

    /** The lookup strings offered at the caret. Fails if completion inserted a single item by itself. */
    private fun complete(): List<String> {
        myFixture.completeBasic()
        return myFixture.lookupElementStrings ?: emptyList()
    }

    private fun verilogToken(text: String) = VerilogLexerAdapter().run { start(text); tokenType }

    private fun vhdlToken(text: String) = VhdlLexerAdapter().run { start(text); tokenType }
}
