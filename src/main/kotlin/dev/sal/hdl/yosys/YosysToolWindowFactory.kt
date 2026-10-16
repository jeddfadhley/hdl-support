package dev.sal.hdl.yosys

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory

/**
 * The Yosys tool window. It's hidden until the first Show Synthesis Stats run, and each run adds
 * its own tab (see [YosysRunner]), so there's no content to create up front.
 */
class YosysToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun shouldBeAvailable(project: Project): Boolean = false

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {}
}
