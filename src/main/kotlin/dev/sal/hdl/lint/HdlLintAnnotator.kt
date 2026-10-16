package dev.sal.hdl.lint

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import dev.sal.hdl.settings.HdlSettings
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Shared plumbing for running a command-line linter and turning its output into editor annotations.
 *
 * The platform calls the three steps in order:
 * 1. [collectInformation] in a read action: snapshot the file path and any unsaved text.
 * 2. [doAnnotate] on a background thread: run the tool. This is the only place a process is started.
 * 3. [apply] in a read action: convert [LintMessage]s into annotations.
 */
abstract class HdlLintAnnotator : ExternalAnnotator<HdlLintAnnotator.Input, List<LintMessage>>() {

    /**
     * @property file the file on disk
     * @property unsavedText the editor's text when it differs from disk, otherwise null
     */
    class Input(val project: Project, val file: Path, val unsavedText: String?)

    /** Executable name, e.g. `verilator`. */
    protected abstract val toolName: String

    /** Name shown to the user, e.g. `Verilator`. */
    protected abstract val displayName: String

    protected abstract fun isEnabled(settings: HdlSettings.State): Boolean

    protected abstract fun configuredPath(settings: HdlSettings.State): String

    /** Whether this file should be linted at all (header files, for example, usually can't be linted alone). */
    protected open fun shouldLint(file: Path): Boolean = true

    /**
     * @param target the file to pass to the tool; a temporary copy when the editor has unsaved changes
     * @param original the file on disk, for finding neighbouring sources
     */
    protected abstract fun buildCommand(
        tool: Path,
        target: Path,
        original: Path,
        settings: HdlSettings.State,
    ): GeneralCommandLine

    protected abstract fun parseOutput(output: String): List<LintMessage>

    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): Input? {
        val settings = HdlSettings.getInstance().state
        if (!isEnabled(settings) || ToolLocator.hasGivenUp(toolName)) return null
        val virtualFile = file.virtualFile ?: return null
        if (!virtualFile.isInLocalFileSystem) return null
        val path = virtualFile.toNioPath()
        if (!shouldLint(path)) return null
        val document = editor.document
        val unsaved = FileDocumentManager.getInstance().isDocumentUnsaved(document)
        return Input(file.project, path, if (unsaved) document.text else null)
    }

    override fun doAnnotate(input: Input): List<LintMessage> {
        val settings = HdlSettings.getInstance().state
        val tool = ToolLocator.find(toolName, configuredPath(settings))
        if (tool == null) {
            ToolLocator.reportMissing(input.project, toolName, displayName, configuredPath(settings))
            return emptyList()
        }

        var tempDir: Path? = null
        try {
            // Unsaved text goes into a temp directory under the original file name, because tools
            // like Verilator complain when the file name doesn't match the module name.
            val target = if (input.unsavedText != null) {
                tempDir = Files.createTempDirectory("hdl-lint")
                tempDir.resolve(input.file.fileName).also {
                    Files.writeString(it, input.unsavedText, StandardCharsets.UTF_8)
                }
            } else {
                input.file
            }

            val workDir = input.file.parent
            val command = buildCommand(tool, target, input.file, settings)
                .withWorkingDirectory(workDir)
                .withCharset(StandardCharsets.UTF_8)
            LOG.debug { "Running ${command.commandLineString}" }

            val output = CapturingProcessHandler(command).runProcess(settings.timeoutSeconds.coerceAtLeast(1) * 1000)
            if (output.isTimeout) {
                LOG.info("$displayName timed out after ${settings.timeoutSeconds}s on ${input.file}")
                return emptyList()
            }
            return parseOutput(output.stdout + "\n" + output.stderr)
                .filter { isSameFile(it.file, target, workDir) }
        } catch (e: ExecutionException) {
            LOG.warn("Couldn't run $displayName", e)
            return emptyList()
        } catch (e: IOException) {
            LOG.warn("Couldn't write a temporary copy of ${input.file}", e)
            return emptyList()
        } finally {
            tempDir?.let { FileUtil.delete(it) }
        }
    }

    override fun apply(file: PsiFile, messages: List<LintMessage>, holder: AnnotationHolder) {
        if (messages.isEmpty()) return
        val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return
        if (document.lineCount == 0) return
        val text = document.charsSequence

        for (message in messages) {
            val line = (message.line - 1).coerceIn(0, document.lineCount - 1)
            val range = LintRanges.highlightRange(
                text,
                document.getLineStartOffset(line),
                document.getLineEndOffset(line),
                message.column,
            )
            val severity = when (message.severity) {
                LintSeverity.ERROR -> HighlightSeverity.ERROR
                LintSeverity.WARNING -> HighlightSeverity.WARNING
            }
            val description = if (message.code != null) "${message.message} (${message.code})" else message.message
            val tooltip = buildString {
                append("<html>").append(StringUtil.escapeXmlEntities(message.message))
                append("<br/><small>").append(displayName)
                if (message.code != null) append(": ").append(StringUtil.escapeXmlEntities(message.code))
                append("</small></html>")
            }
            val builder = holder.newAnnotation(severity, description).range(range).tooltip(tooltip)
            // A blank line has nothing to underline, so mark the end of the line instead.
            if (range.isEmpty) builder.afterEndOfLine()
            builder.create()
        }
    }

    /** Tools print paths as they were given or relative to the working directory; compare them normalised. */
    private fun isSameFile(reported: String, target: Path, workDir: Path): Boolean = try {
        workDir.resolve(reported).normalize() == target.normalize()
    } catch (_: Exception) {
        false
    }

    private companion object {
        val LOG = logger<HdlLintAnnotator>()
    }
}
