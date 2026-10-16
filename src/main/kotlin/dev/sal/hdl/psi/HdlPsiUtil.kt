package dev.sal.hdl.psi

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.elementType

/** A construct that opens and closes with keywords, such as `begin`/`end` or `process`/`end process`. */
interface HdlKeywordBlock : PsiElement {
    /** The keywords to highlight together when the caret is on one of them, in source order. */
    fun markerKeywords(): List<PsiElement>

    /** The keyword that closes the block (`end`, `endmodule`), or null if it's missing. */
    fun closingKeyword(): PsiElement?
}

object HdlPsiUtil {
    /** Direct children, without whitespace and comments. */
    fun significantChildren(element: PsiElement): List<PsiElement> =
        generateSequence(element.firstChild) { it.nextSibling }
            .filter { it !is PsiWhiteSpace && it !is PsiComment }
            .toList()

    /** Direct children that aren't inside brackets, without the brackets themselves, whitespace and comments. */
    fun topLevelChildren(element: PsiElement, openers: TokenSet, closers: TokenSet): List<PsiElement> {
        val result = ArrayList<PsiElement>()
        var depth = 0
        for (child in significantChildren(element)) {
            when (child.elementType) {
                in openers -> depth++
                in closers -> if (depth > 0) depth--
                else -> if (depth == 0) result.add(child)
            }
        }
        return result
    }

    /** Splits [elements] at each element of type [separator], dropping the separators. */
    fun split(elements: List<PsiElement>, separator: IElementType): List<List<PsiElement>> {
        val result = ArrayList<List<PsiElement>>()
        var current = ArrayList<PsiElement>()
        for (element in elements) {
            if (element.elementType == separator) {
                result.add(current)
                current = ArrayList()
            } else {
                current.add(element)
            }
        }
        result.add(current)
        return result
    }

    /** The source text between two offsets of [element]'s file, on one line with runs of whitespace collapsed. */
    fun collapsedText(element: PsiElement, start: Int, end: Int): String {
        if (end <= start) return ""
        return element.containingFile.text.substring(start, end).replace(WHITESPACE, " ").trim()
    }

    /** Shortens [text] to [max] characters, ending with an ellipsis if it was cut. */
    fun abbreviate(text: String, max: Int = 60): String =
        if (text.length <= max) text else text.take(max - 1).trimEnd() + "…"

    private val WHITESPACE = Regex("\\s+")
}
