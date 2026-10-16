package dev.sal.hdl

import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl

/** Checks the fold regions, written inline as `<fold text='placeholder'>...</fold>`. */
class HdlFoldingTest : BasePlatformTestCase() {

    fun testVerilogModuleBlocksAndLists() = assertFolding(
        "a.v",
        """
        module counter #(<fold text='...'>
            parameter W = 8
        </fold>) (<fold text='...'>
            input clk,
            output reg [W-1:0] q
        </fold>);<fold text='...'>
            always @(posedge clk) begin<fold text='...'>
                q <= q + 1;
            </fold>end
        </fold>endmodule
        """,
    )

    fun testVerilogCaseGenerateFunctionAndComment() = assertFolding(
        "a.sv",
        """
        <fold text='/*...*/'>/*
         * header
         */</fold>
        module m;<fold text='...'>
            generate<fold text='...'>
                if (1) begin : g<fold text='...'>
                    wire w;
                </fold>end
            </fold>endgenerate
            function int f(int x);<fold text='...'>
                case (x)<fold text='...'>
                    0: return 1;
                </fold>endcase
            </fold>endfunction
        </fold>endmodule
        """,
    )

    fun testSingleLineBlocksDoNotFold() = assertFolding("a.v", "module m; initial begin end endmodule")

    fun testUnterminatedBlockDoesNotFold() = assertFolding(
        "a.v",
        """
        module m;
            initial begin
                x = 1;
        """,
    )

    fun testVhdlUnitsProcessesAndStatements() = assertFolding(
        "a.vhd",
        """
        entity e is<fold text='...'>
            port (<fold text='...'>
                clk : in bit
            </fold>);
        </fold>end entity;
        architecture rtl of e is<fold text='...'>
        begin
            p : process (clk) is<fold text='...'>
            begin
                if clk = '1' then<fold text='...'>
                    null;
                </fold>end if;
            </fold>end process;
            g : for i in 0 to 3 generate<fold text='...'>
                x(i) <= '0';
            </fold>end generate;
        </fold>end architecture;
        """,
    )

    private fun assertFolding(fileName: String, expected: String) {
        val markup = expected.trimIndent()
        myFixture.configureByText(fileName, CodeInsightTestFixtureImpl.removeFoldingMarkers(markup))
        EditorTestUtil.buildInitialFoldingsInBackground(myFixture.editor)
        assertEquals(markup, CodeInsightTestFixtureImpl.getFoldingData(myFixture.editor, false))
    }
}
