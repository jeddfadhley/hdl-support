package dev.sal.hdl

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** Enter at the end of a line that opens a block adds the matching closer. */
class HdlEnterHandlerTest : BasePlatformTestCase() {

    // ---- Verilog ----

    fun testVerilogBegin() = verilog(
        "module m;\n  always @(posedge clk) begin<caret>\nendmodule\n",
        "module m;\n  always @(posedge clk) begin\n      <caret>\n  end\nendmodule\n",
    )

    fun testVerilogLabelledBegin() = verilog(
        "module m;\n  initial begin : init<caret>\nendmodule\n",
        "module m;\n  initial begin : init\n      <caret>\n  end\nendmodule\n",
    )

    fun testVerilogBeginAlreadyClosed() = verilog(
        "module m;\n  initial begin<caret>\n  end\nendmodule\n",
        "module m;\n  initial begin\n  <caret>\n  end\nendmodule\n",
    )

    fun testVerilogNestedBeginBorrowsNoEnd() = verilog(
        "module m;\n  always @* begin\n    if (a) begin<caret>\n  end\nendmodule\n",
        "module m;\n  always @* begin\n    if (a) begin\n        <caret>\n    end\n  end\nendmodule\n",
    )

    fun testVerilogModule() = verilog(
        "module counter(input clk);<caret>\n",
        "module counter(input clk);\n    <caret>\nendmodule\n",
    )

    fun testVerilogModuleHeaderOverSeveralLines() = verilog(
        "module counter (\n  input clk,\n  output q\n);<caret>\n",
        "module counter (\n  input clk,\n  output q\n);\n    <caret>\nendmodule\n",
    )

    fun testVerilogModuleAlreadyClosed() = verilog(
        "module m;<caret>\nendmodule\n",
        "module m;\n<caret>\nendmodule\n",
    )

    fun testVerilogCase() = verilog(
        "module m;\n  always_comb\n    unique case (state)<caret>\nendmodule\n",
        "module m;\n  always_comb\n    unique case (state)\n        <caret>\n    endcase\nendmodule\n",
    )

    fun testVerilogFork() = verilog(
        "module m; initial fork<caret>\nendmodule\n",
        "module m; initial fork\n    <caret>\njoin\nendmodule\n",
    )

    fun testVerilogWaitForkIsNotABlock() = verilog(
        "module m; initial begin wait fork;<caret>\nend endmodule\n",
        "module m; initial begin wait fork;\n<caret>\nend endmodule\n",
    )

    fun testVerilogFunction() = verilog(
        "module m;\n  function automatic int f(int a);<caret>\nendmodule\n",
        "module m;\n  function automatic int f(int a);\n      <caret>\n  endfunction\nendmodule\n",
    )

    fun testVerilogExternFunctionHasNoBody() = verilog(
        "class c;\n  extern function void f();<caret>\nendclass\n",
        "class c;\n  extern function void f();\n  <caret>\nendclass\n",
    )

    fun testVerilogVirtualInterfaceIsNotABlock() = verilog(
        "class c;\n  virtual interface bus_if vif;<caret>\nendclass\n",
        "class c;\n  virtual interface bus_if vif;\n  <caret>\nendclass\n",
    )

    fun testVerilogMiddleOfLineDoesNothing() = verilog(
        "module m;\n  initial begin<caret> x = 1;\nendmodule\n",
        "module m;\n  initial begin\n   <caret>x = 1;\nendmodule\n",
    )

    // ---- VHDL ----

    fun testVhdlEntity() = vhdl(
        "entity counter is<caret>\n",
        "entity counter is\n    <caret>\nend entity counter;\n",
    )

    fun testVhdlArchitecture() = vhdl(
        "entity e is end;\narchitecture rtl of e is<caret>\n",
        "entity e is end;\narchitecture rtl of e is\n    <caret>\nend architecture rtl;\n",
    )

    fun testVhdlLabelledProcess() = vhdl(
        "architecture a of e is\nbegin\n  seq : process (clk)<caret>\nend;\n",
        "architecture a of e is\nbegin\n  seq : process (clk)\n      <caret>\n  end process seq;\nend;\n",
    )

    fun testVhdlIfInsideProcess() = vhdl(
        "architecture a of e is\nbegin\n  process (clk) begin\n    if rising_edge(clk) then<caret>\n  end process;\nend;\n",
        "architecture a of e is\nbegin\n  process (clk) begin\n    if rising_edge(clk) then\n        <caret>\n    end if;\n  end process;\nend;\n",
    )

    fun testVhdlIfAlreadyClosed() = vhdl(
        "architecture a of e is begin process begin\n  if x then<caret>\n  end if;\nend process; end;\n",
        "architecture a of e is begin process begin\n  if x then\n  <caret>\n  end if;\nend process; end;\n",
    )

    fun testVhdlCase() = vhdl(
        "architecture a of e is begin process begin\n  case state is<caret>\nend process; end;\n",
        "architecture a of e is begin process begin\n  case state is\n      <caret>\n  end case;\nend process; end;\n",
    )

    fun testVhdlForGenerate() = vhdl(
        "architecture a of e is\nbegin\n  gen : for i in 0 to 3 generate<caret>\nend;\n",
        "architecture a of e is\nbegin\n  gen : for i in 0 to 3 generate\n      <caret>\n  end generate gen;\nend;\n",
    )

    fun testVhdlRecord() = vhdl(
        "package p is\n  type pair is record<caret>\nend package;\n",
        "package p is\n  type pair is record\n      <caret>\n  end record;\nend package;\n",
    )

    fun testVhdlProcessAlreadyClosed() = vhdl(
        "architecture a of e is\nbegin\n  process (clk)<caret>\n  end process;\nend;\n",
        "architecture a of e is\nbegin\n  process (clk)\n  <caret>\n  end process;\nend;\n",
    )

    fun testVhdlUpperCase() = vhdl(
        "ENTITY Counter IS<caret>\n",
        "ENTITY Counter IS\n    <caret>\nEND ENTITY Counter;\n",
    )

    fun testVhdlElsifAddsNothing() = vhdl(
        "architecture a of e is begin process begin\n  if x then\n    y <= 1;\n  elsif z then<caret>\n  end if;\nend process; end;\n",
        "architecture a of e is begin process begin\n  if x then\n    y <= 1;\n  elsif z then\n  <caret>\n  end if;\nend process; end;\n",
    )

    private fun verilog(before: String, after: String) = check("a.sv", before, after)

    private fun vhdl(before: String, after: String) = check("a.vhd", before, after)

    private fun check(fileName: String, before: String, after: String) {
        myFixture.configureByText(fileName, before)
        myFixture.type('\n')
        val text = myFixture.editor.document.text
        val caret = myFixture.editor.caretModel.offset
        assertEquals(after, text.substring(0, caret) + "<caret>" + text.substring(caret))
    }
}
