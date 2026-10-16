package dev.sal.hdl.lint

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.util.execution.ParametersListUtil
import dev.sal.hdl.settings.HdlSettings
import java.nio.file.Path

/** Runs `ghdl -s` (syntax check) on VHDL files and shows the results as squiggles. */
class GhdlAnnotator : HdlLintAnnotator() {
    override val toolName = "ghdl"
    override val displayName = "GHDL"

    override fun isEnabled(settings: HdlSettings.State) = settings.ghdlEnabled

    override fun configuredPath(settings: HdlSettings.State) = settings.ghdlPath

    override fun buildCommand(tool: Path, target: Path, original: Path, settings: HdlSettings.State) =
        GeneralCommandLine(tool.toString()).apply {
            val std = settings.ghdlStd.takeIf { it in HdlSettings.GHDL_STANDARDS } ?: "08"
            addParameters("-s", "--std=$std")
            addParameters(ParametersListUtil.parse(settings.ghdlExtraArgs))
            addParameter(target.toString())
        }

    override fun parseOutput(output: String) = GhdlOutputParser.parse(output)
}
