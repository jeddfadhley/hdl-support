package dev.sal.hdl.lint

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import dev.sal.hdl.settings.HdlSettingsConfigurable
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Finds lint executables and reports when they're missing.
 *
 * If a tool can't be found, the user gets one balloon per IDE session and the plugin stops looking
 * for that tool. Changing the settings calls [reset], which lets it try again.
 */
object ToolLocator {
    /** Executables we've given up on, keyed by tool name. */
    private val missing: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * Some places tools are commonly installed that may not be on the PATH an IDE gets when it's
     * launched from the macOS Dock or a desktop launcher.
     */
    private val FALLBACK_DIRS = listOf("/opt/homebrew/bin", "/usr/local/bin", "/usr/bin")

    /** Returns true once a tool has been reported missing in this session. */
    fun hasGivenUp(toolName: String): Boolean = toolName in missing

    /**
     * @param toolName executable name without extension, e.g. `verilator`
     * @param configuredPath path from settings; blank means search PATH
     */
    fun find(toolName: String, configuredPath: String): Path? {
        if (configuredPath.isNotBlank()) {
            val path = Path.of(configuredPath.trim())
            return path.takeIf { Files.isRegularFile(it) && Files.isExecutable(it) }
        }
        PathEnvironmentVariableUtil.findExecutableInPathOnAnyOS(toolName)?.let { return it.toPath() }
        return FALLBACK_DIRS.map { Path.of(it, toolName) }.firstOrNull { Files.isExecutable(it) }
    }

    /** Shows the "tool not found" balloon once per session and stops further attempts. */
    fun reportMissing(project: Project, toolName: String, displayName: String, configuredPath: String) {
        if (!missing.add(toolName)) return
        val where = if (configuredPath.isBlank()) "on PATH" else "at <code>${configuredPath.trim()}</code>"
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(
                "$displayName not found",
                "Couldn't find <code>$toolName</code> $where, so $displayName lint is off for this session. " +
                    "Highlighting still works.",
                NotificationType.INFORMATION,
            )
            .addAction(NotificationAction.createSimpleExpiring("Configure…") {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, HdlSettingsConfigurable::class.java)
            })
            .notify(project)
    }

    /** Forgets which tools were missing. Called when the settings change. */
    fun reset() {
        missing.clear()
    }

    /** Must match the `notificationGroup` id in plugin.xml. */
    const val NOTIFICATION_GROUP = "HDL Support"
}
