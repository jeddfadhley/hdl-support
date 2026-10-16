package dev.sal.hdl.yosys

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import dev.sal.hdl.lint.ToolLocator
import dev.sal.hdl.verilog.VerilogFile
import dev.sal.hdl.verilog.psi.VerilogDesignUnit

/**
 * Tools | Show Synthesis Stats, also in the editor and project view context menus.
 *
 * Synthesises one module with Yosys and shows its statistics (cells, wires, ...) in the Yosys tool
 * window. The module is the one at the caret; otherwise the file's only module; otherwise the user
 * picks one from a list.
 */
class ShowSynthesisStatsAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.getData(CommonDataKeys.PSI_FILE) is VerilogFile
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.PSI_FILE) as? VerilogFile ?: return
        // Null for files that aren't on disk, such as scratch files and test fixtures.
        val path = file.virtualFile?.let { it.fileSystem.getNioPath(it) }
        if (path == null) {
            notify(project, "Save the file to disk before running Yosys.")
            return
        }
        val modules = modules(file)
        if (modules.isEmpty()) {
            notify(project, "No module found in ${file.name}.")
            return
        }

        val atCaret = e.getData(CommonDataKeys.EDITOR)
            ?.let { file.findElementAt(it.caretModel.offset) }
            ?.let { PsiTreeUtil.getParentOfType(it, VerilogDesignUnit::class.java) }
            ?.name
            ?.takeIf { it in modules }

        // Yosys reads the files from disk, including submodules in other files.
        FileDocumentManager.getInstance().saveAllDocuments()
        when {
            atCaret != null -> YosysRunner.run(project, path, atCaret)
            modules.size == 1 -> YosysRunner.run(project, path, modules.single())
            else -> JBPopupFactory.getInstance()
                .createPopupChooserBuilder(modules)
                .setTitle("Module to Synthesise")
                .setItemChosenCallback { YosysRunner.run(project, path, it) }
                .createPopup()
                .showInBestPositionFor(e.dataContext)
        }
    }

    /** The names of the modules in [file], in source order. */
    private fun modules(file: PsiFile): List<String> =
        PsiTreeUtil.findChildrenOfType(file, VerilogDesignUnit::class.java)
            .filter { it.kind == VerilogDesignUnit.Kind.MODULE }
            .mapNotNull { it.name }

    private fun notify(project: Project, message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(ToolLocator.NOTIFICATION_GROUP)
            .createNotification("Show synthesis stats", message, NotificationType.WARNING)
            .notify(project)
    }
}
