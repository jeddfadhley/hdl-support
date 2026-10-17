package dev.sal.hdl.editor

import com.intellij.lexer.Lexer
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import dev.sal.hdl.psi.HdlKeywordBlock
import dev.sal.hdl.verilog.VerilogLexerAdapter
import dev.sal.hdl.verilog.VerilogTokenTypes
import dev.sal.hdl.vhdl.VhdlLexerAdapter
import dev.sal.hdl.vhdl.VhdlTokenTypes

/** A token of the document, without whitespace and comments. */
data class HdlToken(val type: IElementType, val text: String, val start: Int, val end: Int) {
    /** The keyword in lower case, or null if this isn't a keyword. */
    fun keyword(keywordType: IElementType): String? = if (type == keywordType) text.lowercase() else null
}

/** What to insert after Enter: the closer goes on its own line, [openerStart] decides its indentation. */
data class BlockClose(val closer: String, val openerStart: Int)

/**
 * Decides whether pressing Enter at the end of a line that opens a block should add the closing
 * keyword: `begin` gets `end`, `module m(...);` gets `endmodule`, `if ... then` gets `end if;`.
 *
 * The closer is only added if the block isn't closed already. Verilog counts openers against
 * closers in the whole file, because a new `begin` would otherwise borrow the `end` of the block
 * around it. VHDL closers say what they close (`end if`, `end process`), so VHDL asks the parser
 * whether the block has its `end`.
 */
object HdlBlockCloser {

    // ---- Verilog ----

    /** Blocks whose header ends with `;`. The keyword is the first one in the statement. */
    private val VERILOG_HEADERS = mapOf(
        "module" to "endmodule", "macromodule" to "endmodule", "interface" to "endinterface",
        "program" to "endprogram", "package" to "endpackage", "class" to "endclass",
        "function" to "endfunction", "task" to "endtask", "checker" to "endchecker",
        "covergroup" to "endgroup", "primitive" to "endprimitive",
    )

    private val VERILOG_CASES = setOf("case", "casex", "casez")
    private val VERILOG_JOINS = setOf("join", "join_any", "join_none")

    /** Keywords that turn a header into a prototype or reference with no body: `extern function f();`. */
    private val VERILOG_NO_BODY = setOf("extern", "pure", "import", "export", "typedef")

    fun verilog(text: CharSequence, lineStart: Int, lineEnd: Int): BlockClose? {
        val tokens = significantTokens(VerilogLexerAdapter(), text, VerilogTokenTypes.COMMENTS)
        val last = tokens.indexOfLast { it.end <= lineEnd }
        if (last < 0 || tokens[last].start < lineStart) return null

        // Find the keyword that opens the block from the last token on the line.
        var i = last
        // `begin : label` and `fork : label` open the block too.
        if (i >= 2 && tokens[i - 1].text == ":" && verilogKeyword(tokens[i - 2]) in setOf("begin", "fork")) i -= 2
        val opener = when {
            verilogKeyword(tokens[i]) in setOf("begin", "fork", "generate", "randcase") -> i
            // `case (state)`, `unique casez (sel)`
            tokens[i].type == VerilogTokenTypes.RPAREN ->
                matchingOpen(tokens, i)?.let { it - 1 }?.takeIf { verilogKeyword(tokens.getOrNull(it)) in VERILOG_CASES }
            // `module m(...);`, `function int f(int a);`, possibly over several lines.
            tokens[i].type == VerilogTokenTypes.SEMICOLON -> verilogHeader(tokens, i)
            else -> null
        } ?: return null
        val closer = verilogCloserAt(tokens, opener) ?: return null

        // Only add the closer if the file has more blocks of this kind than closers for them.
        val opened = tokens.indices.count { verilogCloserAt(tokens, it) == closer }
        val closers = if (closer == "join") VERILOG_JOINS else setOf(closer)
        val closed = tokens.count { verilogKeyword(it) in closers }
        if (opened <= closed) return null
        return BlockClose(closer, tokens[opener].start)
    }

    private fun verilogKeyword(token: HdlToken?): String? = token?.keyword(VerilogTokenTypes.KEYWORD)

    /** The closer of the block that the keyword at [i] opens, or null if it doesn't open one. */
    private fun verilogCloserAt(tokens: List<HdlToken>, i: Int): String? {
        val word = verilogKeyword(tokens[i]) ?: return null
        return when {
            word == "begin" -> "end"
            word == "fork" -> if (isWaitOrDisableFork(tokens, i)) null else "join"
            word == "generate" -> "endgenerate"
            word in VERILOG_CASES || word == "randcase" -> "endcase"
            // `interface class c;` is a class, opened by the `class` keyword.
            word == "interface" && tokens.getOrNull(i + 1)?.text == "class" -> null
            isVerilogHeader(tokens, i) -> VERILOG_HEADERS.getValue(word)
            else -> null
        }
    }

    private fun isWaitOrDisableFork(tokens: List<HdlToken>, i: Int): Boolean =
        tokens.getOrNull(i - 1)?.text in setOf("wait", "disable")

    /** The index of the header keyword in the statement ending with the `;` at [semicolon], if there is one. */
    private fun verilogHeader(tokens: List<HdlToken>, semicolon: Int): Int? {
        var start = semicolon - 1
        while (start >= 0 && !isStatementBoundary(tokens[start])) start--
        var header = (start + 1 until semicolon).firstOrNull { verilogKeyword(tokens[it]) in VERILOG_HEADERS }
            ?: return null
        if (tokens[header].text == "interface" && tokens.getOrNull(header + 1)?.text == "class") header++
        return header
    }

    /** True if the header keyword at [i] starts a block with a body, rather than a prototype or type. */
    private fun isVerilogHeader(tokens: List<HdlToken>, i: Int): Boolean {
        val word = tokens[i].text
        if (word !in VERILOG_HEADERS) return false
        // `virtual interface bus_if vif;` declares a variable. `virtual class c;` is a class.
        if (word == "interface" && tokens.getOrNull(i - 1)?.text == "virtual") return false
        // Look back to the start of the statement for extern, pure, import, export or typedef.
        var j = i - 1
        while (j >= 0 && !isStatementBoundary(tokens[j])) {
            if (tokens[j].keyword(VerilogTokenTypes.KEYWORD) in VERILOG_NO_BODY) return false
            j--
        }
        return true
    }

    private fun isStatementBoundary(token: HdlToken): Boolean {
        if (token.type == VerilogTokenTypes.SEMICOLON || token.type == VerilogTokenTypes.DIRECTIVE) return true
        val word = token.keyword(VerilogTokenTypes.KEYWORD) ?: return false
        return word == "begin" || word.startsWith("end") || word in VERILOG_JOINS
    }

    // ---- VHDL ----

    /** Keywords after which a new statement starts, besides `;`. */
    private val VHDL_BOUNDARIES = setOf("begin", "then", "else", "loop", "generate")

    /** Blocks whose `end` must say what it closes (IEEE 1076-2008): `end process`, not `end`. */
    private val VHDL_REQUIRED_TRAILERS =
        setOf("process", "block", "if", "case", "loop", "generate", "component", "record", "protected")

    /**
     * VHDL. [file] must be committed: the parser decides whether the block already has its `end`.
     * Labels are repeated in the closer (`end process seq;`), and the closer's keywords follow the
     * case of the opener (`PROCESS` gets `END PROCESS;`).
     */
    fun vhdl(file: PsiFile, text: CharSequence, lineStart: Int, lineEnd: Int): BlockClose? {
        val keyword = VhdlTokenTypes.KEYWORD
        val tokens = significantTokens(VhdlLexerAdapter(), text, VhdlTokenTypes.COMMENTS)
        val last = tokens.indexOfLast { it.end <= lineEnd }
        if (last < 0 || tokens[last].start < lineStart) return null

        // The statement starts after the previous `;` or after a keyword that ends a header. An `is`
        // just before the last token belongs to the statement: `type t is record`.
        var start = last - 1
        while (start >= 0 && tokens[start].type != VhdlTokenTypes.SEMICOLON &&
            tokens[start].keyword(keyword) !in VHDL_BOUNDARIES &&
            !(tokens[start].keyword(keyword) == "is" && start < last - 1)
        ) {
            start--
        }
        val statement = tokens.subList(start + 1, last + 1)
        if (statement.isEmpty()) return null

        // An optional `label :`.
        val label = if (statement.size > 2 && statement[1].text == ":" && statement[0].type == VhdlTokenTypes.IDENTIFIER) {
            statement[0].text
        } else {
            null
        }
        val body = if (label != null) statement.drop(2) else statement
        val first = body.firstOrNull() ?: return null
        val firstWord = first.keyword(keyword) ?: return null
        val lastWord = statement.last().keyword(keyword)
        val words = body.mapNotNull { it.keyword(keyword) }
        val name = body.getOrNull(1)?.takeIf { it.type == VhdlTokenTypes.IDENTIFIER }?.text

        // The closer's keywords, and the name repeated after them.
        val (closer, closerName) = when {
            firstWord == "entity" && lastWord == "is" && name != null -> "end entity" to name
            firstWord == "architecture" && lastWord == "is" && name != null -> "end architecture" to name
            firstWord == "configuration" && lastWord == "is" && name != null -> "end configuration" to name
            firstWord == "package" && lastWord == "is" && words.getOrNull(1) == "body" ->
                "end package body" to (body.getOrNull(2)?.takeIf { it.type == VhdlTokenTypes.IDENTIFIER }?.text ?: return null)
            firstWord == "package" && lastWord == "is" && name != null -> "end package" to name
            firstWord in setOf("process", "postponed") && "process" in words &&
                (lastWord == "process" || lastWord == "is" || statement.last().type == VhdlTokenTypes.RPAREN) ->
                "end process" to label
            firstWord == "block" -> "end block" to label
            firstWord == "if" && lastWord == "then" -> "end if" to label
            firstWord == "case" && lastWord == "is" -> "end case" to label
            lastWord == "loop" && firstWord in setOf("for", "while", "loop") -> "end loop" to label
            lastWord == "generate" && firstWord in setOf("for", "if", "case") -> "end generate" to label
            firstWord == "component" -> "end component" to null
            firstWord in setOf("function", "pure", "impure") && lastWord == "is" -> "end function" to null
            firstWord == "procedure" && lastWord == "is" -> "end procedure" to null
            firstWord == "type" && lastWord == "record" -> "end record" to null
            firstWord == "type" && lastWord == "protected" -> "end protected" to null
            else -> return null
        }

        // Ask the parser whether the block around the opening keyword already has its `end`. The parser
        // gives a bare `end;` to the innermost block, but `end process`, `end if` and the others are
        // required, so a bare `end` there really closes the architecture or package around it.
        val leaf = file.findElementAt(first.start) ?: return null
        val block = PsiTreeUtil.getParentOfType(leaf, HdlKeywordBlock::class.java) ?: return null
        val end = block.closingKeyword()
        val trailer = closer.split(" ")[1]
        if (end != null) {
            val next = PsiTreeUtil.nextCodeLeaf(end)?.text?.lowercase()
            if (trailer !in VHDL_REQUIRED_TRAILERS || next == trailer || next == "postponed") return null
        }

        val keywords = if (first.text.none { it.isLowerCase() }) closer.uppercase() else closer
        return BlockClose(keywords + (closerName?.let { " $it" } ?: "") + ";", statement.first().start)
    }

    // ---- Shared ----

    private fun significantTokens(lexer: Lexer, text: CharSequence, comments: TokenSet): List<HdlToken> {
        val result = ArrayList<HdlToken>()
        lexer.start(text)
        while (true) {
            val type = lexer.tokenType ?: break
            if (type != TokenType.WHITE_SPACE && type !in comments) {
                val tokenText = text.subSequence(lexer.tokenStart, lexer.tokenEnd).toString()
                result.add(HdlToken(type, tokenText, lexer.tokenStart, lexer.tokenEnd))
            }
            lexer.advance()
        }
        return result
    }

    /** The index of the Verilog `(` that matches the `)` at [close]. */
    private fun matchingOpen(tokens: List<HdlToken>, close: Int): Int? {
        var depth = 0
        for (j in close downTo 0) {
            when (tokens[j].type) {
                VerilogTokenTypes.RPAREN -> depth++
                VerilogTokenTypes.LPAREN -> if (--depth == 0) return j
            }
        }
        return null
    }
}
