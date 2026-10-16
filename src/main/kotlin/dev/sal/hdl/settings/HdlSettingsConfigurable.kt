package dev.sal.hdl.settings

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.toNullableProperty
import dev.sal.hdl.lint.ToolLocator

/** Settings → Tools → HDL Support. */
class HdlSettingsConfigurable : BoundConfigurable("HDL Support") {
    private val settings = HdlSettings.getInstance().state

    override fun createPanel(): DialogPanel = panel {
        group("Verilator (Verilog / SystemVerilog)") {
            row {
                checkBox("Run Verilator lint").bindSelected(settings::verilatorEnabled)
            }
            row("Executable:") {
                textFieldWithBrowseButton(executableChooser("Select Verilator Executable"))
                    .bindText(settings::verilatorPath)
                    .align(AlignX.FILL)
                    .comment("Leave blank to find <code>verilator</code> on PATH.")
            }
            row("Include directories:") {
                textField()
                    .bindText(settings::verilatorIncludeDirs)
                    .align(AlignX.FILL)
                    .comment("Passed as <code>-I&lt;dir&gt;</code>. Separate directories with <code>;</code>.")
            }
            row("Extra arguments:") {
                textField()
                    .bindText(settings::verilatorExtraArgs)
                    .align(AlignX.FILL)
                    .comment("Added after <code>--lint-only -Wall</code>, e.g. <code>-Wno-DECLFILENAME</code>.")
            }
        }
        group("GHDL (VHDL)") {
            row {
                checkBox("Run GHDL syntax check").bindSelected(settings::ghdlEnabled)
            }
            row("Executable:") {
                textFieldWithBrowseButton(executableChooser("Select GHDL Executable"))
                    .bindText(settings::ghdlPath)
                    .align(AlignX.FILL)
                    .comment("Leave blank to find <code>ghdl</code> on PATH.")
            }
            row("VHDL standard:") {
                comboBox(HdlSettings.GHDL_STANDARDS)
                    .bindItem(settings::ghdlStd.toNullableProperty())
                    .comment("Passed as <code>--std=</code>.")
            }
            row("Extra arguments:") {
                textField()
                    .bindText(settings::ghdlExtraArgs)
                    .align(AlignX.FILL)
                    .comment("Added after <code>-s --std=NN</code>, e.g. <code>-fsynopsys</code>.")
            }
        }
        group("General") {
            row("Lint timeout (seconds):") {
                intTextField(1..300).bindIntText(settings::timeoutSeconds)
            }
        }
    }

    override fun apply() {
        super.apply()
        // A new path may fix a "tool not found" problem, so let the locator try again,
        // then re-run highlighting so lint results show up straight away.
        ToolLocator.reset()
        for (project in ProjectManager.getInstance().openProjects) {
            DaemonCodeAnalyzer.getInstance(project).restart()
        }
    }

    private fun executableChooser(title: String) =
        FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor().withTitle(title)
}
