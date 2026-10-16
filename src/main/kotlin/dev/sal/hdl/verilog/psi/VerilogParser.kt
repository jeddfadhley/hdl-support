package dev.sal.hdl.verilog.psi

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import dev.sal.hdl.psi.HdlParsing
import dev.sal.hdl.verilog.VerilogTokenTypes as T
import dev.sal.hdl.verilog.psi.VerilogElementTypes as E

/** Builds the Verilog/SystemVerilog PSI tree. See [VerilogParsing] for what it recognises. */
class VerilogParser : PsiParser {
    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        val file = builder.mark()
        VerilogParsing(builder).parseFile()
        file.done(root)
        return builder.treeBuilt
    }
}

/**
 * An outline parser for Verilog and SystemVerilog (see [HdlParsing]).
 *
 * It builds nodes for design units, port and parameter lists, declarations, instances, processes,
 * functions and tasks, and keyword blocks such as `begin`/`end` and `case`/`endcase`. Other
 * statements are skipped up to their `;`.
 *
 * Recovery: every construct pushes its end keyword onto a stack. When the parser meets an end
 * keyword that belongs to an enclosing construct, the inner constructs stop without consuming it,
 * so a missing `end` doesn't swallow the rest of the module.
 */
internal class VerilogParsing(builder: PsiBuilder) :
    HdlParsing(builder, T.KEYWORD, caseSensitive = true, OPENERS, CLOSERS, T.COMMENTS) {

    private val terminators = ArrayList<Set<String>>()

    fun parseFile() {
        while (!eof) parseItem()
    }

    /** True at an end keyword expected by the current construct or one that encloses it. */
    private fun atTerminator(): Boolean {
        val word = keyword() ?: return false
        return terminators.any { word in it }
    }

    private fun <R> withTerminators(words: Set<String>, body: () -> R): R {
        terminators.add(words)
        try {
            return body()
        } finally {
            terminators.removeAt(terminators.lastIndex)
        }
    }

    /** Parses items until an end keyword of this or an enclosing construct. */
    private fun parseItems() {
        while (!eof && !atTerminator()) parseItem()
    }

    /** Parses the statement after `always`, `if (...)`, `for (...)` and so on, if there is one. */
    private fun parseStatementBody() {
        if (!eof && !atTerminator()) parseItem()
    }

    /** One module item or statement. Always consumes at least one token. */
    private fun parseItem() {
        val word = keyword()
        when {
            at(T.DIRECTIVE) -> parseDirective()
            at(T.SEMICOLON) -> advance()
            at(NAMES) -> parseNameItem()
            word != null -> parseKeywordItem(word)
            atText("@") || atText("#") || atText("##") -> {
                parseTimingControl()
                parseStatementBody()
            }
            else -> parseStatement()
        }
    }

    private fun parseKeywordItem(word: String) {
        when (word) {
            in END_KEYWORDS -> advance() // A stray end keyword that nothing is waiting for.
            in DESIGN_UNIT_KEYWORDS -> parseDesignUnit(mark())
            in QUALIFIERS -> parseQualified()
            "function", "task" -> parseSubprogram(mark())
            in PROCESS_KEYWORDS -> {
                val marker = mark()
                advance()
                parseStatementBody()
                marker.done(E.PROCESS)
            }
            "generate" -> parseKeywordBlock(E.GENERATE, setOf("endgenerate"))
            "begin" -> parseKeywordBlock(E.BLOCK, setOf("end"))
            "fork" -> parseKeywordBlock(E.BLOCK, setOf("join", "join_any", "join_none"))
            in CONSTRUCT_ENDS -> parseConstruct(word)
            in CASE_KEYWORDS -> parseCase()
            "if" -> parseIf()
            "for", "foreach", "while", "repeat" -> {
                advance()
                if (at(T.LPAREN)) skipGroup(::atTerminator)
                parseStatementBody()
            }
            "forever" -> {
                advance()
                parseStatementBody()
            }
            "do" -> {
                advance()
                parseStatementBody()
                if (atKeyword("while")) parseStatement()
            }
            "unique", "unique0", "priority" -> {
                advance()
                parseStatementBody()
            }
            in DECLARATION_KEYWORDS -> parseStatement(mark(), E.DECLARATION)
            else -> parseStatement()
        }
    }

    // ---- Design units ----

    /** `module m #(...) (...); ... endmodule : m`, and the other design unit keywords. */
    private fun parseDesignUnit(marker: PsiBuilder.Marker) {
        var word = keyword() ?: "module"
        advance()
        if (word == "interface" && atKeyword("class")) {
            word = "class"
            advance()
        }
        val end = DESIGN_UNIT_ENDS.getValue(word)
        withTerminators(setOf(end)) {
            parseUnitHeader(hasPorts = word in UNITS_WITH_PORTS)
            parseItems()
        }
        finishKeywordBlock(end)
        marker.done(E.DESIGN_UNIT)
    }

    /** Everything from the unit's name to the `;` that ends its header. */
    private fun parseUnitHeader(hasPorts: Boolean) {
        // Only the first #(...) and (...) belong to this unit. In `class c extends base #(T);`
        // the #(T) parameterises the base class.
        var sawParameters = false
        var sawPorts = false
        while (!eof && !atTerminator()) {
            when {
                at(T.SEMICOLON) -> {
                    advance()
                    return
                }
                atText("#") && peekType(1) == T.LPAREN && !sawParameters && !sawPorts -> {
                    parseList(E.PARAMETER_LIST, E.PARAMETER)
                    sawParameters = true
                }
                at(T.LPAREN) && hasPorts && !sawPorts -> {
                    parseList(E.PORT_LIST, E.PORT)
                    sawPorts = true
                }
                atKeyword("import") -> parseStatement()
                atKeyword("extends") || atKeyword("implements") -> {
                    advance()
                    if (at(NAMES)) wrapToken(E.UNIT_REFERENCE)
                    sawParameters = true
                }
                at(OPENERS) -> skipGroup(::atTerminator)
                else -> consumeToken()
            }
        }
    }

    /** A bracketed, comma-separated list: `#(...)` of parameters or `(...)` of ports. */
    private fun parseList(listType: IElementType, entryType: IElementType) {
        val list = mark()
        if (atText("#")) advance()
        advance() // (
        while (!eof && !at(T.RPAREN) && !atTerminator()) {
            val entry = mark()
            var empty = true
            while (!eof && !at(T.COMMA) && !at(T.RPAREN) && !atTerminator()) {
                if (at(OPENERS)) skipGroup(::atTerminator) else consumeToken()
                empty = false
            }
            if (empty) entry.drop() else entry.done(entryType)
            if (at(T.COMMA)) advance()
        }
        if (at(T.RPAREN)) advance()
        list.done(listType)
    }

    /** `virtual class`, `static function`, `rand bit x;`, `extern function f();` and so on. */
    private fun parseQualified() {
        val marker = mark()
        var prototype = false
        while (keyword() in QUALIFIERS) {
            if (keyword() == "pure" || keyword() == "extern") prototype = true
            advance()
        }
        when {
            atKeyword("class") -> parseDesignUnit(marker)
            (atKeyword("function") || atKeyword("task")) && !prototype -> parseSubprogram(marker)
            // A prototype (`extern function ...;`) has no body and no end keyword.
            prototype -> {
                marker.drop()
                parseStatement()
            }
            else -> parseStatement(marker, E.DECLARATION)
        }
    }

    /** `function ... endfunction` or `task ... endtask`, with its header, declarations and statements. */
    private fun parseSubprogram(marker: PsiBuilder.Marker) {
        val end = "end" + keyword()
        advance()
        withTerminators(setOf(end)) {
            parseStatement() // The header, up to its `;`.
            parseItems()
        }
        finishKeywordBlock(end)
        marker.done(E.SUBPROGRAM)
    }

    // ---- Keyword blocks and statements ----

    /** `begin [: label] ... end [: label]`, `fork ... join`, `generate ... endgenerate`. */
    private fun parseKeywordBlock(type: IElementType, ends: Set<String>) {
        val marker = mark()
        advance()
        parseEndLabel()
        withTerminators(ends) { parseItems() }
        if (keyword() in ends) finishKeywordBlock(keyword()!!)
        marker.done(type)
    }

    /** `covergroup`, `property`, `sequence`, `clocking`, `specify`, `table`, `randsequence`. */
    private fun parseConstruct(word: String) {
        val marker = mark()
        val end = CONSTRUCT_ENDS.getValue(word)
        advance()
        withTerminators(setOf(end)) {
            when (word) {
                "specify", "table" -> {}
                "randsequence" -> if (at(T.LPAREN)) skipGroup(::atTerminator)
                else -> parseStatement() // The header, up to its `;`.
            }
            parseItems()
        }
        finishKeywordBlock(end)
        marker.done(E.CONSTRUCT)
    }

    /** Consumes the end keyword (if it's there) and an optional `: label` after it. */
    private fun finishKeywordBlock(end: String) {
        if (atKeyword(end)) {
            advance()
            parseEndLabel()
        }
    }

    /** `: label` after `begin`, `end`, `endmodule` and the like. */
    private fun parseEndLabel() {
        if (atText(":") && (peekType(1) in NAMES || peekText(1) == "new")) {
            advance()
            advance()
        }
    }

    /** `case (expr) item: statement ... endcase`, including `casex`, `casez` and `randcase`. */
    private fun parseCase() {
        val marker = mark()
        advance()
        if (at(T.LPAREN)) skipGroup(::atTerminator)
        if (atKeyword("inside") || atKeyword("matches")) advance()
        withTerminators(setOf("endcase")) {
            while (!eof && !atTerminator()) {
                // A case item: expressions up to `:`, then one statement.
                if (atKeyword("default")) advance()
                while (!eof && !atTerminator() && !atText(":") && !at(T.SEMICOLON)) {
                    if (at(OPENERS)) skipGroup(::atTerminator) else consumeToken()
                }
                when {
                    atText(":") -> {
                        advance()
                        parseStatementBody()
                    }
                    at(T.SEMICOLON) -> advance()
                    // `default` without a colon goes straight to its statement.
                    else -> parseStatementBody()
                }
            }
        }
        finishKeywordBlock("endcase")
        marker.done(E.CASE)
    }

    private fun parseIf() {
        advance()
        if (at(T.LPAREN)) skipGroup(::atTerminator)
        parseStatementBody()
        if (atKeyword("else")) {
            advance()
            parseStatementBody()
        }
    }

    /** `@(posedge clk)`, `@*`, `@ev`, `#10`, `#(5)`, `` #`DELAY ``, `##1` */
    private fun parseTimingControl() {
        val event = atText("@")
        advance()
        when {
            at(T.LPAREN) || at(T.LBRACKET) -> skipGroup(::atTerminator)
            event && atText("*") -> advance()
            event && at(NAMES) -> {
                advance()
                while (at(T.DOT) && peekType(1) in NAMES) {
                    advance()
                    advance()
                }
            }
            !event && (at(T.NUMBER) || at(NAMES) || at(T.DIRECTIVE)) -> advance()
        }
    }

    /** A statement or module item that starts with a name: a label, an instance, a declaration or anything else. */
    private fun parseNameItem() {
        if (peekText(1) == ":") {
            // A label: `name: statement`
            advance()
            advance()
            parseStatementBody()
            return
        }
        when (classifyNameItem()) {
            NameItem.INSTANCE -> parseInstance()
            NameItem.DECLARATION -> parseStatement(mark(), E.DECLARATION)
            NameItem.OTHER -> parseStatement()
        }
    }

    private enum class NameItem { INSTANCE, DECLARATION, OTHER }

    /**
     * Looks ahead to tell `type name;` (a declaration) and `module [#(...)] name (...)` (an instance)
     * apart from ordinary statements such as `a <= b;` or `f(x);`.
     */
    private fun classifyNameItem(): NameItem {
        val ahead = lookahead()
        ahead.next()
        // Package-scoped type: pkg::type_t
        while (ahead.text == "::") {
            ahead.next()
            if (ahead.type !in NAMES) return NameItem.OTHER
            ahead.next()
        }
        if (ahead.text == "#") {
            ahead.next()
            ahead.skipGroup() // #(...) or #8
        }
        while (ahead.type == T.LBRACKET) ahead.skipGroup()
        if (ahead.type !in NAMES) return NameItem.OTHER
        ahead.next()
        while (ahead.type == T.LBRACKET) ahead.skipGroup()
        return when {
            ahead.type == T.LPAREN -> NameItem.INSTANCE
            ahead.type == T.SEMICOLON || ahead.type == T.COMMA || ahead.text == "=" -> NameItem.DECLARATION
            else -> NameItem.OTHER
        }
    }

    /** `module_name [#(...)] instance_name [range] (...) [, ...];` */
    private fun parseInstance() {
        val marker = mark()
        wrapToken(E.UNIT_REFERENCE)
        parseStatement()
        marker.done(E.INSTANCE)
    }

    /**
     * Skips a statement up to and including its `;`, wrapping it in [type] if given.
     *
     * It stops early, without consuming, at an end keyword of an enclosing construct, and (after the
     * first token) at keywords that can only start a new item, so a missing `;` does limited damage.
     * A `{ ... }` group that follows a name or `)` is a block body (`constraint c { ... }`,
     * `coverpoint x { ... }`), which ends the statement with no `;`.
     */
    private fun parseStatement(marker: PsiBuilder.Marker? = null, type: IElementType? = null) {
        val m = marker ?: type?.let { mark() }
        var depth = 0
        var first = true
        var previous: IElementType? = null
        var braceBodyDepth = -1
        while (!eof) {
            if (atTerminator()) break
            if (!first && depth == 0 && keyword() in STATEMENT_STOPS) break
            if (at(T.SEMICOLON) && depth == 0) {
                advance()
                break
            }
            val current = tokenType
            when {
                at(OPENERS) -> {
                    if (current == T.LBRACE && depth == 0 && (previous in NAMES || previous == T.RPAREN)) {
                        braceBodyDepth = 0
                    }
                    depth++
                    advance()
                }
                at(CLOSERS) -> {
                    if (depth > 0) depth--
                    advance()
                    if (depth == braceBodyDepth && current == T.RBRACE) break
                }
                else -> consumeToken()
            }
            previous = current
            first = false
        }
        if (type != null) m?.done(type) else m?.drop()
    }

    /** `pkg::name` makes `pkg` a reference to a package (or class). */
    override fun consumeToken() {
        if (at(NAMES) && peekText(1) == "::") wrapToken(E.UNIT_REFERENCE) else advance()
    }

    /**
     * Compiler directives. Those that take the rest of the line (`` `define ``, `` `include ``,
     * `` `timescale ``, `` `ifdef ``, ...) consume it. A macro call consumes its arguments.
     */
    private fun parseDirective() {
        val name = builder.tokenText?.removePrefix("`") ?: ""
        val start = builder.currentOffset
        advance()
        when (name) {
            in LINE_DIRECTIVES -> {
                val lineEnd = lineEnd(start)
                while (!eof && builder.currentOffset < lineEnd) advance()
            }
            in BARE_DIRECTIVES -> {}
            else -> if (at(T.LPAREN)) skipGroup(::atTerminator)
        }
    }

    /** Offset of the end of the line containing [offset]. A backslash before the newline continues the line. */
    private fun lineEnd(offset: Int): Int {
        val text = builder.originalText
        var i = offset
        while (i < text.length) {
            if (text[i] == '\n') {
                var j = i - 1
                if (j >= offset && text[j] == '\r') j--
                if (j < offset || text[j] != '\\') return i
            }
            i++
        }
        return text.length
    }

    private companion object {
        val NAMES = TokenSet.create(T.IDENTIFIER, T.ESCAPED_IDENTIFIER)
        val OPENERS = TokenSet.create(T.LPAREN, T.LBRACKET, T.LBRACE)
        val CLOSERS = TokenSet.create(T.RPAREN, T.RBRACKET, T.RBRACE)

        // Keyword groups from IEEE 1364-2005 and IEEE 1800-2017.

        val DESIGN_UNIT_ENDS = mapOf(
            "module" to "endmodule", "macromodule" to "endmodule", "interface" to "endinterface",
            "program" to "endprogram", "package" to "endpackage", "class" to "endclass",
            "primitive" to "endprimitive", "checker" to "endchecker", "config" to "endconfig",
        )
        val DESIGN_UNIT_KEYWORDS = DESIGN_UNIT_ENDS.keys

        /** Design units whose header can have a port list. */
        val UNITS_WITH_PORTS = setOf("module", "macromodule", "interface", "program", "primitive", "checker")

        val CONSTRUCT_ENDS = mapOf(
            "covergroup" to "endgroup", "property" to "endproperty", "sequence" to "endsequence",
            "clocking" to "endclocking", "specify" to "endspecify", "table" to "endtable",
            "randsequence" to "endsequence",
        )

        val END_KEYWORDS = DESIGN_UNIT_ENDS.values.toSet() + CONSTRUCT_ENDS.values + setOf(
            "end", "endcase", "endfunction", "endtask", "endgenerate", "join", "join_any", "join_none",
        )

        val CASE_KEYWORDS = setOf("case", "casex", "casez", "randcase")

        val PROCESS_KEYWORDS = setOf("always", "always_ff", "always_comb", "always_latch", "initial", "final")

        /** Keywords that may come before `class`, `function`, `task` or a declaration. */
        val QUALIFIERS = setOf(
            "virtual", "static", "automatic", "local", "protected", "extern", "pure", "rand", "randc", "const",
        )

        /** Keywords that start a net, variable, parameter or type declaration. */
        val DECLARATION_KEYWORDS = setOf(
            "wire", "reg", "logic", "bit", "byte", "shortint", "int", "longint", "integer", "time", "real",
            "shortreal", "realtime", "string", "chandle", "event", "tri", "tri0", "tri1", "triand", "trior",
            "trireg", "wand", "wor", "uwire", "supply0", "supply1", "parameter", "localparam", "specparam",
            "genvar", "typedef", "input", "output", "inout", "ref", "var", "struct", "union", "enum",
            "nettype", "interconnect",
        )

        /** Keywords that can't appear inside a statement, so meeting one means the `;` is missing. */
        val STATEMENT_STOPS = PROCESS_KEYWORDS + END_KEYWORDS + setOf(
            "module", "macromodule", "package", "program", "generate", "begin", "assign",
        )

        /** Directives whose arguments run to the end of the line (IEEE 1800-2017 clause 22). */
        val LINE_DIRECTIVES = setOf(
            "define", "undef", "include", "timescale", "ifdef", "ifndef", "elsif", "default_nettype",
            "line", "pragma", "begin_keywords", "unconnected_drive",
        )

        /** Directives with no arguments. */
        val BARE_DIRECTIVES = setOf(
            "else", "endif", "resetall", "celldefine", "endcelldefine", "nounconnected_drive", "end_keywords",
            "undefineall",
        )
    }
}
