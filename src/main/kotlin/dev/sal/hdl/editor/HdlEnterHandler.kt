package dev.sal.hdl.editor

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import dev.sal.hdl.verilog.VerilogLanguage
import dev.sal.hdl.vhdl.VhdlLanguage

/**
 * Adds the closing keyword when Enter is pressed at the end of a line that opens a block:
 *
 * ```
 * always_ff @(posedge clk) begin|     →     always_ff @(posedge clk) begin
 *                                               |
 *                                           end
 * ```
 *
 * [HdlBlockCloser] decides what to add, and adds nothing if the block is already closed.
 */
class HdlEnterHandler : EnterHandlerDelegateAdapter() {
    override fun postProcessEnter(file: PsiFile, editor: Editor, dataContext: DataContext): Result {
        if (file.language != VerilogLanguage && file.language != VhdlLanguage) return Result.Continue
        val document = editor.document
        val text = document.charsSequence
        val caret = editor.caretModel.offset
        val line = document.getLineNumber(caret)
        if (line == 0) return Result.Continue

        // Only when Enter split the line at its end, so the new line holds nothing but indentation.
        val lineStart = document.getLineStartOffset(line)
        val lineEnd = document.getLineEndOffset(line)
        if (text.subSequence(caret, lineEnd).isNotBlank()) return Result.Continue

        val previousStart = document.getLineStartOffset(line - 1)
        val previousEnd = document.getLineEndOffset(line - 1)
        val close = if (file.language == VerilogLanguage) {
            HdlBlockCloser.verilog(text, previousStart, previousEnd)
        } else {
            PsiDocumentManager.getInstance(file.project).commitDocument(document)
            HdlBlockCloser.vhdl(file, text, previousStart, previousEnd)
        } ?: return Result.Continue

        // The closer lines up with the line the block starts on; the caret goes one level in.
        val openerLine = document.getLineNumber(close.openerStart)
        val openerLineStart = document.getLineStartOffset(openerLine)
        val baseIndent = text.subSequence(openerLineStart, document.getLineEndOffset(openerLine))
            .takeWhile { it == ' ' || it == '\t' }
            .toString()
        val options = CodeStyle.getIndentOptions(file)
        val indent = baseIndent + if (options.USE_TAB_CHARACTER) "\t" else " ".repeat(options.INDENT_SIZE)

        document.replaceString(lineStart, lineEnd, "$indent\n$baseIndent${close.closer}")
        editor.caretModel.moveToOffset(lineStart + indent.length)
        return Result.Stop
    }
}
