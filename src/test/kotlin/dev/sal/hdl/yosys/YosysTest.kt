package dev.sal.hdl.yosys

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.sal.hdl.lint.ToolLocator
import java.nio.file.Files
import java.nio.file.Path

class YosysTest : BasePlatformTestCase() {

    fun testScript() {
        val script = YosysScript.build(Path.of("/work/rtl/top.sv"), "top", listOf("/work/inc"), "synth_ice40")
        assertEquals(
            "read_verilog -sv -I/work/inc \"/work/rtl/top.sv\"; " +
                "hierarchy -check -top top -libdir .; synth_ice40 -top top; stat",
            script,
        )
    }

    fun testScriptQuotesFileAndDefaultsToSynth() {
        val script = YosysScript.build(Path.of("/my files/a.v"), "a", emptyList(), "  ")
        assertEquals("read_verilog -sv \"/my files/a.v\"; hierarchy -check -top a -libdir .; synth -top a; stat", script)
    }

    fun testIncludeDirectoriesWithSpacesAreLeftOut() {
        val dirs = listOf("/ok", "/not ok")
        assertFalse(YosysScript.build(Path.of("/a.v"), "a", dirs, "synth").contains("not ok"))
        assertEquals(listOf("/not ok"), YosysScript.unsupportedIncludeDirs(dirs))
    }

    fun testActionOnlyForVerilogFiles() {
        myFixture.configureByText("a.v", "module m; endmodule\n")
        assertTrue(myFixture.testAction(ShowSynthesisStatsAction()).isEnabledAndVisible)
        myFixture.configureByText("a.vhd", "entity e is end;\n")
        assertFalse(myFixture.testAction(ShowSynthesisStatsAction()).isEnabledAndVisible)
    }

    /** Runs the real Yosys on a module whose submodule lives in a sibling file. Passes quietly without Yosys. */
    fun testRealYosysFindsSubmoduleInSiblingFile() {
        val yosys = ToolLocator.find("yosys", "")
        if (yosys == null) {
            println("Skipping: yosys is not installed")
            return
        }
        val dir = Files.createTempDirectory("hdl yosys")
        try {
            Files.writeString(
                dir.resolve("inverter.v"),
                "module inverter(input wire a, output wire y); assign y = ~a; endmodule\n",
            )
            val top = dir.resolve("top.v")
            Files.writeString(
                top,
                "module top(input wire [1:0] a, output wire [1:0] y);\n" +
                    "    inverter u0 (.a(a[0]), .y(y[0]));\n" +
                    "    inverter u1 (.a(a[1]), .y(y[1]));\n" +
                    "endmodule\n",
            )
            val command = GeneralCommandLine(yosys.toString(), "-p", YosysScript.build(top, "top", emptyList(), "synth"))
                .withWorkingDirectory(dir)
            val output = CapturingProcessHandler(command).runProcess(60_000)
            assertEquals(output.stdout + output.stderr, 0, output.exitCode)
            assertTrue(output.stdout, output.stdout.contains("=== top ==="))
            assertTrue(output.stdout, output.stdout.contains("=== inverter ==="))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
