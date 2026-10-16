package dev.sal.hdl.psi

import com.intellij.lang.PsiBuilder
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet

/**
 * Helpers shared by the Verilog and VHDL parsers.
 *
 * Both parsers are "outline" parsers. They recognise the constructs that matter for the structure
 * view, folding, navigation and keyword matching (modules, processes, begin/end blocks and so on)
 * and skip everything else a statement at a time. They never report errors: broken code still
 * gets a tree, and lint squiggles come from Verilator and GHDL instead.
 *
 * Every keyword is a single KEYWORD token, so keywords are matched by their text.
 *
 * @param keywordType the language's KEYWORD token type
 * @param caseSensitive false for VHDL, whose keywords are compared in lower case
 * @param openers opening brackets, used to skip bracketed groups
 * @param closers closing brackets
 * @param comments comment token types, skipped when looking ahead
 */
abstract class HdlParsing(
    protected val builder: PsiBuilder,
    private val keywordType: IElementType,
    private val caseSensitive: Boolean,
    private val openers: TokenSet,
    private val closers: TokenSet,
    private val comments: TokenSet,
) {
    protected val eof: Boolean get() = builder.eof()

    protected val tokenType: IElementType? get() = builder.tokenType

    protected fun at(type: IElementType): Boolean = builder.tokenType == type

    protected fun at(types: TokenSet): Boolean = builder.tokenType in types

    /** The current token's text if it's a keyword (lower case for VHDL), otherwise null. */
    protected fun keyword(): String? =
        if (builder.tokenType == keywordType) normalise(builder.tokenText ?: "") else null

    protected fun atKeyword(word: String): Boolean = keyword() == word

    protected fun atKeyword(words: Set<String>): Boolean = keyword() in words

    /** True at an operator or punctuation token with exactly this text, e.g. `:` but not `::`. */
    protected fun atText(text: String): Boolean = builder.tokenText == text

    protected fun advance() = builder.advanceLexer()

    protected fun mark(): PsiBuilder.Marker = builder.mark()

    /** Wraps the current token in an element of [type]. */
    protected fun wrapToken(type: IElementType) {
        val marker = mark()
        advance()
        marker.done(type)
    }

    /**
     * Consumes a bracketed group starting at an opening bracket, including nested groups.
     * Stops early at end of file, or at a keyword for which [stopAt] returns true.
     */
    protected fun skipGroup(stopAt: () -> Boolean = { false }) {
        var depth = 0
        do {
            when {
                at(openers) -> depth++
                at(closers) -> depth--
                stopAt() -> return
            }
            consumeToken()
        } while (!eof && depth > 0)
    }

    /** Consumes one token. Subclasses override this to wrap some tokens, such as package-scoped names. */
    protected open fun consumeToken() = advance()

    protected fun normalise(text: String): String = if (caseSensitive) text else text.lowercase()

    /** Starts a [Lookahead] at the current token. */
    protected fun lookahead(): Lookahead = Lookahead()

    /** Text of the [n]th significant token ahead (0 is the current one), or null past the end. */
    protected fun peekText(n: Int): String? = lookahead().also { it.skip(n) }.text

    /** Type of the [n]th significant token ahead (0 is the current one), or null past the end. */
    protected fun peekType(n: Int): IElementType? = lookahead().also { it.skip(n) }.type

    /**
     * Walks forward over tokens without consuming them, skipping whitespace and comments.
     * PsiBuilder only offers raw lookahead, so this keeps its own position in the raw token stream.
     */
    protected inner class Lookahead {
        private var raw = 0

        init {
            settle()
        }

        /** Null past the end of the file. */
        val type: IElementType? get() = builder.rawLookup(raw)

        val text: String?
            get() {
                if (type == null) return null
                return builder.originalText.subSequence(builder.rawTokenTypeStart(raw), builder.rawTokenTypeStart(raw + 1))
                    .toString()
            }

        val keyword: String? get() = if (type == keywordType) text?.let(::normalise) else null

        fun next() {
            if (type == null) return
            raw++
            settle()
        }

        fun skip(n: Int) = repeat(n) { next() }

        /** At an opening bracket: moves past the matching closing bracket. Otherwise moves one token. */
        fun skipGroup() {
            var depth = 0
            do {
                when (type) {
                    null -> return
                    in openers -> depth++
                    in closers -> depth--
                }
                next()
            } while (depth > 0)
        }

        private fun settle() {
            while (true) {
                val t = builder.rawLookup(raw) ?: return
                if (t != TokenType.WHITE_SPACE && t !in comments) return
                raw++
            }
        }
    }
}
