package dev.sal.hdl.lint

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl

/**
 * Runs the real annotators end to end. Each test quietly passes when its tool isn't installed,
 * so `./gradlew test` works without Verilator or GHDL.
 */
class LintAnnotatorIntegrationTest : BasePlatformTestCase() {
    // The annotators only lint files on the local disk, so use a real temp directory.
    override fun createTempDirTestFixture(): TempDirTestFixture = TempDirTestFixtureImpl()

    private fun toolMissing(name: String): Boolean {
        if (ToolLocator.find(name, "") != null) return false
        println("Skipping: $name is not installed")
        return true
    }

    private fun lint(fileName: String, text: String): List<HighlightInfo> {
        myFixture.configureByText(fileName, text)
        return myFixture.doHighlighting()
            .filter { it.severity == HighlightSeverity.ERROR || it.severity == HighlightSeverity.WARNING }
    }

    private fun HighlightInfo.line(): Int = myFixture.editor.document.getLineNumber(startOffset) + 1

    fun testVerilatorUnusedSignalWarning() {
        if (toolMissing("verilator")) return
        val infos = lint(
            "warn.v",
            """
            module warn (
                input  wire       clk,
                output reg  [7:0] count
            );
                wire spare;
                always @(posedge clk) count <= count + 1'b1;
            endmodule
            """.trimIndent() + "\n",
        )
        val warning = infos.single { it.description.contains("spare") }
        assertEquals(HighlightSeverity.WARNING, warning.severity)
        assertEquals(5, warning.line())
        assertEquals("spare", warning.text)
        assertTrue(warning.toolTip!!.contains("UNUSEDSIGNAL"))
    }

    fun testVerilatorSyntaxError() {
        if (toolMissing("verilator")) return
        val infos = lint(
            "bad.v",
            """
            module bad (input wire clk, output reg q);
                always @(posedge clk) begin
                    q <= 1'b0
                    q <= 1'b1;
                end
            endmodule
            """.trimIndent() + "\n",
        )
        val error = infos.first { it.severity == HighlightSeverity.ERROR }
        assertEquals(4, error.line())
    }

    fun testGhdlSyntaxError() {
        if (toolMissing("ghdl")) return
        val infos = lint(
            "bad.vhd",
            """
            library ieee;
            use ieee.std_logic_1164.all;
            entity bad is
              port (clk : in std_logic);
            end entity bad;
            architecture rtl of bad is
              signal s : std_logic
            begin
            end architecture;
            """.trimIndent() + "\n",
        )
        val error = infos.single()
        assertEquals(HighlightSeverity.ERROR, error.severity)
        assertEquals(7, error.line())
        assertEquals("std_logic", error.text)
    }

    fun testGhdlCleanFileHasNoProblems() {
        if (toolMissing("ghdl")) return
        val infos = lint("ok.vhd", "entity ok is\nend entity;\narchitecture a of ok is\nbegin\nend architecture;\n")
        assertEmpty(infos)
    }
}
