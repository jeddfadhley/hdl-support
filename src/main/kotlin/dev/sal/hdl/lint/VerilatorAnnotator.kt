package dev.sal.hdl.lint

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.util.execution.ParametersListUtil
import dev.sal.hdl.settings.HdlSettings
import java.nio.file.Path

/** Runs `verilator --lint-only -Wall` on Verilog/SystemVerilog files and shows the results as squiggles. */
class VerilatorAnnotator : HdlLintAnnotator() {
    override val toolName = "verilator"
    override val displayName = "Verilator"

    override fun isEnabled(settings: HdlSettings.State) = settings.verilatorEnabled

    override fun configuredPath(settings: HdlSettings.State) = settings.verilatorPath

    /** Header files (`.vh`, `.svh`) are meant to be included, not compiled on their own. */
    override fun shouldLint(file: Path): Boolean {
        val name = file.fileName.toString().lowercase()
        return name.endsWith(".v") || name.endsWith(".sv")
    }

    override fun buildCommand(tool: Path, target: Path, original: Path, settings: HdlSettings.State) =
        GeneralCommandLine(tool.toString()).apply {
            addParameters("--lint-only", "-Wall")
            for (dir in HdlSettings.splitDirectories(settings.verilatorIncludeDirs)) addParameter("-I$dir")
            // Let Verilator find modules instantiated from sibling files.
            addParameters("-y", original.parent.toString())
            addParameters(ParametersListUtil.parse(settings.verilatorExtraArgs))
            addParameter(target.toString())
        }

    override fun parseOutput(output: String) = VerilatorOutputParser.parse(output)
}
