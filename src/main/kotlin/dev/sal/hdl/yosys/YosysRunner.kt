package dev.sal.hdl.yosys

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import dev.sal.hdl.lint.ToolLocator
import dev.sal.hdl.settings.HdlSettings
import java.nio.charset.StandardCharsets
import java.nio.file.Path

/** Runs Yosys on one module and streams its output into a tab of the Yosys tool window. */
object YosysRunner {
    /** Must match the `toolWindow` id in plugin.xml. */
    private const val TOOL_WINDOW_ID = "Yosys"

    /** Call on the EDT. The process is started on a background thread. */
    fun run(project: Project, file: Path, top: String) {
        val settings = HdlSettings.getInstance().state
        val tool = ToolLocator.find("yosys", settings.yosysPath)
        if (tool == null) {
            ToolLocator.reportMissingNow(project, "yosys", "Yosys", settings.yosysPath)
            return
        }
        val includeDirs = HdlSettings.splitDirectories(settings.verilatorIncludeDirs)
        val script = YosysScript.build(file, top, includeDirs, settings.yosysSynthCommand)
        val command = GeneralCommandLine(tool.toString(), "-p", script)
            .withWorkingDirectory(file.parent)
            .withCharset(StandardCharsets.UTF_8)

        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return
        val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
        val content = ContentFactory.getInstance().createContent(console.component, top, false)
        content.setDisposer(console)
        toolWindow.contentManager.addContent(content)
        toolWindow.contentManager.setSelectedContent(content)
        toolWindow.isAvailable = true
        toolWindow.activate(null)
        console.print("${command.commandLineString}\n\n", ConsoleViewContentType.SYSTEM_OUTPUT)
        for (dir in YosysScript.unsupportedIncludeDirs(includeDirs)) {
            console.print(
                "Skipping include directory \"$dir\": Yosys can't read directory names containing spaces.\n",
                ConsoleViewContentType.ERROR_OUTPUT,
            )
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val handler = KillableColoredProcessHandler(command)
                // Closing the tab stops Yosys if it's still running.
                if (!Disposer.tryRegister(console, Disposable { handler.destroyProcess() })) {
                    handler.destroyProcess()
                    return@executeOnPooledThread
                }
                ProcessTerminatedListener.attach(handler)
                console.attachToProcess(handler)
                handler.startNotify()
            } catch (e: ExecutionException) {
                console.print("Couldn't run Yosys: ${e.message}\n", ConsoleViewContentType.ERROR_OUTPUT)
            }
        }
    }
}
