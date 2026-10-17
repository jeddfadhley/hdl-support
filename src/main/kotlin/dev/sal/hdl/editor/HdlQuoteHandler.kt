package dev.sal.hdl.editor

import com.intellij.codeInsight.editorActions.QuoteHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import dev.sal.hdl.verilog.VerilogTokenTypes
import dev.sal.hdl.vhdl.VhdlTokenTypes

/**
 * Typing `"` inserts a pair of quotes, typing `"` before a closing quote steps over it, and
 * Backspace between an empty pair deletes both.
 *
 * Both lexers return an unterminated string as one token running to the end of the line, so
 * whether a string is closed can be read from its token text. The quote isn't always the first
 * character: VHDL bit strings start with their base (`x"FF"`, `12UX"F0"`), so the opening quote is
 * the first `"` in the token. Single quotes aren't paired; in VHDL a tick usually starts an
 * attribute (`clk'event`), not a character literal.
 */
abstract class HdlQuoteHandler(private val stringTokens: TokenSet) : QuoteHandler {

    /** Whether [text], a string token starting at its opening quote, has its closing quote. */
    protected abstract fun isTerminated(type: IElementType, text: CharSequence): Boolean

    override fun isOpeningQuote(iterator: HighlighterIterator, offset: Int): Boolean =
        openingQuoteOffset(iterator) == offset

    override fun isClosingQuote(iterator: HighlighterIterator, offset: Int): Boolean {
        val opening = openingQuoteOffset(iterator) ?: return false
        return offset == iterator.end - 1 && offset > opening &&
            isTerminated(iterator.tokenType, tokenText(iterator, opening))
    }

    override fun isInsideLiteral(iterator: HighlighterIterator): Boolean = iterator.tokenType in stringTokens

    /** True if any string from here to the end of the line is unterminated, so a closing quote is needed. */
    override fun hasNonClosedLiteral(editor: Editor, iterator: HighlighterIterator, offset: Int): Boolean {
        val document = editor.document
        val lineEnd = document.getLineEndOffset(document.getLineNumber(offset))
        while (!iterator.atEnd() && iterator.start < lineEnd) {
            val opening = openingQuoteOffset(iterator)
            if (opening != null && !isTerminated(iterator.tokenType, tokenText(iterator, opening))) return true
            iterator.advance()
        }
        return false
    }

    /** The document offset of the token's opening quote, or null if the token isn't a string. */
    private fun openingQuoteOffset(iterator: HighlighterIterator): Int? {
        if (iterator.tokenType !in stringTokens) return null
        val text = iterator.document.charsSequence.subSequence(iterator.start, iterator.end)
        val index = text.indexOf('"')
        return if (index < 0) null else iterator.start + index
    }

    private fun tokenText(iterator: HighlighterIterator, from: Int): CharSequence =
        iterator.document.charsSequence.subSequence(from, iterator.end)
}

/** Verilog strings escape with a backslash: `"say \"hi\""`. */
class VerilogQuoteHandler : HdlQuoteHandler(VerilogTokenTypes.STRINGS) {
    override fun isTerminated(type: IElementType, text: CharSequence): Boolean {
        var i = 1
        while (i < text.length) {
            when (text[i]) {
                '\\' -> i += 2
                '"' -> return i == text.length - 1
                else -> i++
            }
        }
        return false
    }
}

/** VHDL strings escape a quote by doubling it: `"say ""hi"""`. Bit strings have no escapes. */
class VhdlQuoteHandler : HdlQuoteHandler(VhdlTokenTypes.STRINGS) {
    override fun isTerminated(type: IElementType, text: CharSequence): Boolean {
        var i = 1
        while (i < text.length) {
            if (text[i] == '"') {
                if (type == VhdlTokenTypes.STRING && i + 1 < text.length && text[i + 1] == '"') {
                    i += 2
                    continue
                }
                return i == text.length - 1
            }
            i++
        }
        return false
    }
}
