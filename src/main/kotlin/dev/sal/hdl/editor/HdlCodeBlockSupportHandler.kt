package dev.sal.hdl.editor

import com.intellij.codeInsight.highlighting.CodeBlockSupportHandler
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import dev.sal.hdl.psi.HdlKeywordBlock

/**
 * Keyword matching for `begin`/`end`, `module`/`endmodule`, `if`/`elsif`/`else`/`end if` and the
 * other keyword pairs. With the caret on one of the keywords, the platform highlights all of them,
 * Ctrl+Shift+M (Move Caret to Matching Brace) jumps between them, and Ctrl+[ / Ctrl+] move to the
 * start and end of the enclosing block.
 *
 * The keywords come from [HdlKeywordBlock.markerKeywords], so the same handler serves both languages.
 */
class HdlCodeBlockSupportHandler : CodeBlockSupportHandler {
    override fun getCodeBlockMarkerRanges(elementAtCursor: PsiElement): List<TextRange> {
        val block = elementAtCursor.parent as? HdlKeywordBlock ?: return emptyList()
        val markers = block.markerKeywords()
        if (elementAtCursor !in markers) return emptyList()
        return markers.map { it.textRange }
    }

    override fun getCodeBlockRange(elementAtCursor: PsiElement): TextRange {
        var block = PsiTreeUtil.getParentOfType(elementAtCursor, HdlKeywordBlock::class.java, false)
        // Skip elements that are only sometimes blocks, such as a VHDL declaration without a body.
        while (block != null && block.markerKeywords().isEmpty()) {
            block = PsiTreeUtil.getParentOfType(block, HdlKeywordBlock::class.java, true)
        }
        return block?.textRange ?: TextRange.EMPTY_RANGE
    }
}
