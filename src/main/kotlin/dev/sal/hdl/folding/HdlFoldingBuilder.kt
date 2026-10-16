package dev.sal.hdl.folding

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import dev.sal.hdl.psi.HdlKeywordBlock

/**
 * Code folding shared by both languages. It folds:
 *
 * - keyword blocks ([HdlKeywordBlock]: modules, entities, processes, `begin`/`end`, `case`, generate,
 *   functions, ...) from the end of their header line to their closing keyword, so
 *   `always @(posedge clk) begin ... end` stays readable when folded;
 * - port and parameter lists, between their brackets;
 * - block comments that span several lines.
 *
 * @param lists element types of bracketed lists, such as port lists
 * @param blockComment the block comment token type
 */
abstract class HdlFoldingBuilder(
    private val lists: TokenSet,
    private val blockComment: IElementType,
) : FoldingBuilderEx(), DumbAware {

    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        val result = ArrayList<FoldingDescriptor>()
        PsiTreeUtil.processElements(root) { element ->
            val range = when {
                element is HdlKeywordBlock -> blockRange(element, document)
                element.elementType in lists -> listRange(element)
                element.elementType == blockComment -> element.textRange
                else -> null
            }
            if (range != null && !range.isEmpty && spansLines(range, document)) {
                result.add(FoldingDescriptor(element.node, range))
            }
            true
        }
        return result.toTypedArray()
    }

    /** From the end of the block's header line to the start of its closing keyword. */
    private fun blockRange(block: HdlKeywordBlock, document: Document): TextRange? {
        val closing = block.closingKeyword() ?: return null
        val start = document.getLineEndOffset(document.getLineNumber(block.headerEnd()))
        val end = closing.textRange.startOffset
        return if (start < end) TextRange(start, end) else null
    }

    /** Inside the list's outer brackets: `#( ... )` in Verilog, `port ( ... );` in VHDL. */
    private fun listRange(list: PsiElement): TextRange? {
        val children = generateSequence(list.firstChild) { it.nextSibling }.toList()
        val open = children.firstOrNull { it.text == "(" } ?: return null
        val close = children.lastOrNull { it.text == ")" } ?: return null
        val start = open.textRange.endOffset
        val end = close.textRange.startOffset
        return if (start < end) TextRange(start, end) else null
    }

    private fun spansLines(range: TextRange, document: Document): Boolean =
        document.getLineNumber(range.startOffset) != document.getLineNumber(range.endOffset)

    override fun getPlaceholderText(node: ASTNode): String =
        if (node.elementType == blockComment) "/*...*/" else "..."

    override fun isCollapsedByDefault(node: ASTNode): Boolean = false
}
