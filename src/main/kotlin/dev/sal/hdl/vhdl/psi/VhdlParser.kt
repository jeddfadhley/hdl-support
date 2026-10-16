package dev.sal.hdl.vhdl.psi

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import dev.sal.hdl.psi.HdlParsing
import dev.sal.hdl.vhdl.VhdlTokenTypes as T
import dev.sal.hdl.vhdl.psi.VhdlElementTypes as E

/** Builds the VHDL PSI tree. See [VhdlParsing] for what it recognises. */
class VhdlParser : PsiParser {
    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        val file = builder.mark()
        VhdlParsing(builder).parseFile()
        file.done(root)
        return builder.treeBuilt
    }
}

/**
 * An outline parser for VHDL (see [HdlParsing]).
 *
 * It builds nodes for design units, generic and port clauses, declarations, components,
 * subprograms, processes, blocks, generate statements, instances, and `if`/`case`/loop statements.
 * Other statements are skipped up to their `;`.
 *
 * Recovery: in VHDL `end` closes everything, optionally followed by a keyword saying what it
 * closes (`end if`, `end process`). Every construct records the keywords that may follow its own
 * `end`. An `end` followed by a keyword that no open construct expects is a stray and is skipped.
 * One followed by an enclosing construct's keyword closes the inner constructs early, so a missing
 * `end if` doesn't swallow the rest of the architecture.
 */
internal class VhdlParsing(builder: PsiBuilder) :
    HdlParsing(builder, T.KEYWORD, caseSensitive = false, OPENERS, CLOSERS, T.COMMENTS) {

    /** For each open construct, the keywords that may follow its `end`. */
    private val openKinds = ArrayList<Set<String>>()

    fun parseFile() {
        while (!eof) parseLibraryItem()
    }

    private fun <R> inside(kinds: Set<String>, body: () -> R): R {
        openKinds.add(kinds)
        try {
            return body()
        } finally {
            openKinds.removeAt(openKinds.lastIndex)
        }
    }

    private fun peekKeyword(n: Int): String? = lookahead().also { it.skip(n) }.keyword

    /** True at an `end` that closes the innermost construct or an enclosing one, and false at a stray `end`. */
    private fun atClosingEnd(): Boolean {
        if (!atKeyword("end")) return false
        val next = peekKeyword(1)
        return next == null || next !in END_TRAILERS || openKinds.any { next in it }
    }

    /** True at an `end` that closes the innermost construct. */
    private fun atOwnEnd(): Boolean {
        if (!atKeyword("end")) return false
        val next = peekKeyword(1)
        return next == null || next !in END_TRAILERS || next in openKinds.last()
    }

    /** `end [keywords] [name];` */
    private fun parseEnd() {
        advance()
        while (keyword() in END_TRAILERS) advance()
        // A label, or an operator function's name: end function "+";
        if (at(NAMES) || at(T.STRING)) advance()
        if (at(T.SEMICOLON)) advance()
    }

    /** Consumes tokens up to (not including) [word], stopping early at `;`, `end` or end of file. */
    private fun skipUntilKeyword(word: String) {
        while (!eof && !atKeyword(word) && !at(T.SEMICOLON) && !atKeyword("end")) {
            if (at(OPENERS)) skipGroup() else advance()
        }
    }

    /** Consumes [word] if it's next. */
    private fun consumeKeyword(word: String) {
        if (atKeyword(word)) advance()
    }

    // ---- Design units ----

    private fun parseLibraryItem() {
        when (keyword()) {
            "entity" -> parseEntity()
            "architecture" -> parseArchitecture()
            "package" -> parsePackage()
            "configuration" -> parseConfiguration()
            "context" -> if (peekKeyword(2) == "is") parseContext() else parseStatement()
            else -> parseDeclarativeItem()
        }
    }

    /** `entity e is [generic (...);] [port (...);] declarations [begin statements] end [entity] [e];` */
    private fun parseEntity() {
        val marker = mark()
        advance()
        if (at(NAMES)) advance()
        consumeKeyword("is")
        inside(setOf("entity")) {
            parseDeclarations()
            if (atKeyword("begin")) {
                advance()
                parseStatements()
            }
            if (atOwnEnd()) parseEnd()
        }
        marker.done(E.DESIGN_UNIT)
    }

    /** `architecture a of e is declarations begin statements end [architecture] [a];` */
    private fun parseArchitecture() {
        val marker = mark()
        advance()
        if (at(NAMES)) advance()
        consumeKeyword("of")
        if (at(NAMES)) wrapToken(E.UNIT_REFERENCE)
        consumeKeyword("is")
        inside(setOf("architecture")) {
            parseDeclarations()
            consumeKeyword("begin")
            parseStatements()
            if (atOwnEnd()) parseEnd()
        }
        marker.done(E.DESIGN_UNIT)
    }

    /** `package p is ... end [package] [p];`, `package body p is ... end [package body] [p];`, or an instantiation. */
    private fun parsePackage() {
        val marker = mark()
        advance()
        consumeKeyword("body")
        if (at(NAMES)) advance()
        consumeKeyword("is")
        if (atKeyword("new")) {
            // package p is new work.generic_pkg generic map (...);
            skipStatement()
        } else {
            inside(setOf("package")) {
                parseDeclarations()
                if (atOwnEnd()) parseEnd()
            }
        }
        marker.done(E.DESIGN_UNIT)
    }

    /** `configuration c of e is for a ... end for; end [configuration] [c];` */
    private fun parseConfiguration() {
        val marker = mark()
        advance()
        if (at(NAMES)) advance()
        consumeKeyword("of")
        if (at(NAMES)) wrapToken(E.UNIT_REFERENCE)
        consumeKeyword("is")
        inside(setOf("configuration")) {
            parseConfigurationItems()
            if (atOwnEnd()) parseEnd()
        }
        marker.done(E.DESIGN_UNIT)
    }

    private fun parseConfigurationItems() {
        while (!eof && !atClosingEnd()) {
            if (atKeyword("for")) parseBlockConfiguration() else parseDeclarativeItem()
        }
    }

    /** `for rtl ... end for;` or `for u1 : comp use entity work.x; end for;` inside a configuration. */
    private fun parseBlockConfiguration() {
        advance()
        inside(setOf("for")) {
            // The block or component specification has no `;` of its own.
            while (!eof && !atKeyword("use") && !atKeyword("for") && !atKeyword("end") && !at(T.SEMICOLON)) {
                if (at(OPENERS)) skipGroup() else advance()
            }
            parseConfigurationItems()
            if (atOwnEnd()) parseEnd()
        }
    }

    /** `context c is library ...; use ...; end [context] [c];` */
    private fun parseContext() {
        val marker = mark()
        advance()
        if (at(NAMES)) advance()
        consumeKeyword("is")
        inside(setOf("context")) {
            while (!eof && !atClosingEnd()) parseDeclarativeItem()
            if (atOwnEnd()) parseEnd()
        }
        marker.done(E.DESIGN_UNIT)
    }

    // ---- Declarations ----

    /** Declarations up to `begin` or the closing `end`. */
    private fun parseDeclarations() {
        while (!eof && !atKeyword("begin") && !atClosingEnd()) parseDeclarativeItem()
    }

    private fun parseDeclarativeItem() {
        when (keyword()) {
            "end" -> parseEnd() // A stray `end`.
            "type" -> parseTypeDeclaration()
            "component" -> parseComponent()
            in SUBPROGRAM_KEYWORDS -> parseSubprogram()
            "use" -> parseUse()
            "generic", "port" -> if (peekType(1) == T.LPAREN) parseInterfaceClause() else parseSimpleStatement()
            "package" -> parsePackage()
            "for" -> parseSimpleStatement() // A configuration specification: for all : c use entity work.e;
            in SIMPLE_DECLARATIONS -> parseSimpleStatement(mark(), E.DECLARATION)
            else -> parseStatement()
        }
    }

    /** `generic ( ... );` or `port ( ... );`, with one INTERFACE_DECLARATION per `;`-separated entry. */
    private fun parseInterfaceClause() {
        val marker = mark()
        val type = if (atKeyword("generic")) E.GENERIC_CLAUSE else E.PORT_CLAUSE
        advance()
        if (at(T.LPAREN)) {
            advance()
            while (!eof && !at(T.RPAREN) && !atKeyword("end")) {
                val entry = mark()
                var empty = true
                while (!eof && !at(T.SEMICOLON) && !at(T.RPAREN) && !atKeyword("end")) {
                    if (at(OPENERS)) skipGroup() else advance()
                    empty = false
                }
                if (empty) entry.drop() else entry.done(E.INTERFACE_DECLARATION)
                if (at(T.SEMICOLON)) advance()
            }
            if (at(T.RPAREN)) advance()
        }
        if (at(T.SEMICOLON)) advance()
        marker.done(type)
    }

    /** `type t is ...;`, including record, physical (`units`) and protected types, which end with `end`. */
    private fun parseTypeDeclaration() {
        val marker = mark()
        advance()
        inside(setOf("record", "units", "protected")) {
            while (!eof && !at(T.SEMICOLON) && !atKeyword("end")) {
                if (atKeyword("record") || atKeyword("units") || atKeyword("protected")) {
                    advance()
                    consumeKeyword("body")
                    while (!eof && !atClosingEnd()) parseDeclarativeItem()
                    if (atOwnEnd()) parseEnd()
                    marker.done(E.DECLARATION)
                    return@inside
                }
                if (at(OPENERS)) skipGroup() else advance()
            }
            if (at(T.SEMICOLON)) advance()
            marker.done(E.DECLARATION)
        }
    }

    /** `component c [is] [generic (...);] [port (...);] end component [c];` */
    private fun parseComponent() {
        val marker = mark()
        advance()
        if (at(NAMES)) advance()
        consumeKeyword("is")
        inside(setOf("component")) {
            while (!eof && !atClosingEnd()) parseDeclarativeItem()
            if (atOwnEnd()) parseEnd()
        }
        marker.done(E.COMPONENT)
    }

    /** A function or procedure: a declaration ending in `;`, or a body (`is ... begin ... end`). */
    private fun parseSubprogram() {
        val marker = mark()
        if (atKeyword("pure") || atKeyword("impure")) advance()
        advance()
        inside(setOf("function", "procedure")) {
            while (!eof && !at(T.SEMICOLON) && !atKeyword("is") && !atKeyword("end")) {
                if (at(OPENERS)) skipGroup() else advance()
            }
            when {
                at(T.SEMICOLON) -> advance()
                atKeyword("is") -> {
                    advance()
                    if (atKeyword("new")) {
                        // A subprogram instantiation: function f is new g generic map (...);
                        skipStatement()
                    } else {
                        parseDeclarations()
                        consumeKeyword("begin")
                        parseStatements()
                        if (atOwnEnd()) parseEnd()
                    }
                }
            }
        }
        marker.done(E.SUBPROGRAM)
    }

    /** `use lib.pkg.all;` makes `pkg` a reference to a package. */
    private fun parseUse() {
        advance()
        while (!eof && !at(T.SEMICOLON) && !atKeyword("end")) {
            if (at(NAMES) && peekType(1) == T.DOT && peekType(2) in NAMES) {
                advance()
                advance()
                wrapToken(E.UNIT_REFERENCE)
            } else {
                advance()
            }
        }
        if (at(T.SEMICOLON)) advance()
    }

    // ---- Statements ----

    /** Statements up to the closing `end`, or one of [stops] (`elsif`, `else`) at the start of a statement. */
    private fun parseStatements(stops: Set<String> = emptySet()) {
        while (!eof && !atClosingEnd() && keyword() !in stops) parseStatement()
    }

    /** One concurrent or sequential statement, with an optional label. Always consumes at least one token. */
    private fun parseStatement() {
        if (atKeyword("end")) {
            parseEnd() // A stray `end`.
            return
        }
        val marker = mark()
        val labelled = at(NAMES) && peekText(1) == ":"
        if (labelled) {
            advance()
            advance()
        }
        val word = keyword()
        when {
            word == "process" || (word == "postponed" && peekKeyword(1) == "process") -> parseProcess(marker)
            word == "block" -> parseBlock(marker)
            word == "if" || word == "case" || word == "for" ->
                if (headerEnd() == "generate") {
                    parseGenerate(marker)
                } else {
                    when (word) {
                        "if" -> parseIf(marker)
                        "case" -> parseCase(marker)
                        else -> parseLoop(marker)
                    }
                }
            word == "while" || word == "loop" -> parseLoop(marker)
            labelled && (word == "entity" || word == "component" || word == "configuration") -> parseInstance(marker)
            labelled && at(NAMES) && looksLikeInstance() -> parseInstance(marker)
            !labelled && word in DECLARATION_KEYWORDS -> {
                marker.drop()
                parseDeclarativeItem()
            }
            else -> parseSimpleStatement(marker)
        }
    }

    /**
     * Looks past the header of an `if`, `case` or `for` to the keyword that ends it:
     * `generate` for a generate statement, or `then`, `is` or `loop` for a sequential one.
     */
    private fun headerEnd(): String? {
        val ahead = lookahead()
        ahead.next()
        while (ahead.type != null && ahead.type != T.SEMICOLON) {
            if (ahead.type in OPENERS) {
                ahead.skipGroup()
                continue
            }
            if (ahead.keyword in HEADER_ENDS) return ahead.keyword
            ahead.next()
        }
        return null
    }

    /** At a name after a label: true for `lib.comp generic map` or `comp port map`. */
    private fun looksLikeInstance(): Boolean {
        val ahead = lookahead()
        ahead.next()
        while (ahead.type == T.DOT) {
            ahead.next()
            if (ahead.type !in NAMES) return false
            ahead.next()
        }
        return ahead.keyword == "generic" || ahead.keyword == "port"
    }

    /** `[label :] [postponed] process [(sensitivity)] [is] declarations begin statements end [postponed] process [label];` */
    private fun parseProcess(marker: PsiBuilder.Marker) {
        consumeKeyword("postponed")
        advance()
        if (at(T.LPAREN)) skipGroup()
        consumeKeyword("is")
        inside(setOf("process", "postponed")) {
            parseDeclarations()
            consumeKeyword("begin")
            parseStatements()
            if (atOwnEnd()) parseEnd()
        }
        marker.done(E.PROCESS)
    }

    /** `label : block [(guard)] [is] declarations begin statements end block [label];` */
    private fun parseBlock(marker: PsiBuilder.Marker) {
        advance()
        if (at(T.LPAREN)) skipGroup()
        consumeKeyword("is")
        inside(setOf("block")) {
            parseDeclarations()
            consumeKeyword("begin")
            parseStatements()
            if (atOwnEnd()) parseEnd()
        }
        marker.done(E.BLOCK)
    }

    /**
     * `for ... generate`, `if ... generate [elsif ... generate] [else generate]` or
     * `case ... generate when ... =>`, up to `end generate`. Each alternative may have its own
     * declarations, `begin` and `end;`.
     */
    private fun parseGenerate(marker: PsiBuilder.Marker) {
        inside(setOf("generate")) {
            skipUntilKeyword("generate")
            consumeKeyword("generate")
            while (!eof) {
                when {
                    atKeyword("end") -> {
                        val next = peekKeyword(1)
                        when {
                            // `end;` or `end alt_label;` closes an alternative's body.
                            next == null || next !in END_TRAILERS -> parseEnd()
                            openKinds.any { next in it } -> break
                            else -> parseEnd() // A stray `end`.
                        }
                    }
                    atKeyword("elsif") || atKeyword("else") -> {
                        advance()
                        skipUntilKeyword("generate")
                        consumeKeyword("generate")
                    }
                    atKeyword("when") -> {
                        advance()
                        skipUntilText("=>")
                        if (atText("=>")) advance()
                    }
                    atKeyword("begin") -> advance()
                    keyword() in DECLARATION_KEYWORDS -> parseDeclarativeItem()
                    else -> parseStatement()
                }
            }
            if (atOwnEnd()) parseEnd()
        }
        marker.done(E.GENERATE)
    }

    /** `if cond then ... {elsif cond then ...} [else ...] end if [label];` */
    private fun parseIf(marker: PsiBuilder.Marker) {
        advance()
        inside(setOf("if")) {
            skipUntilKeyword("then")
            consumeKeyword("then")
            while (true) {
                parseStatements(setOf("elsif", "else"))
                when {
                    atKeyword("elsif") -> {
                        advance()
                        skipUntilKeyword("then")
                        consumeKeyword("then")
                    }
                    atKeyword("else") -> advance()
                    else -> break
                }
            }
            if (atOwnEnd()) parseEnd()
        }
        marker.done(E.IF)
    }

    /** `case expr is when choices => ... end case [label];` */
    private fun parseCase(marker: PsiBuilder.Marker) {
        advance()
        inside(setOf("case")) {
            skipUntilKeyword("is")
            consumeKeyword("is")
            while (!eof && !atClosingEnd()) {
                if (atKeyword("when")) {
                    advance()
                    skipUntilText("=>")
                    if (atText("=>")) advance()
                } else {
                    parseStatement()
                }
            }
            if (atOwnEnd()) parseEnd()
        }
        marker.done(E.CASE)
    }

    /** `[while cond | for i in range] loop ... end loop [label];` */
    private fun parseLoop(marker: PsiBuilder.Marker) {
        inside(setOf("loop")) {
            skipUntilKeyword("loop")
            consumeKeyword("loop")
            parseStatements()
            if (atOwnEnd()) parseEnd()
        }
        marker.done(E.LOOP)
    }

    /** `label : [entity | component | configuration] [lib.]name [(arch)] [generic map (...)] [port map (...)];` */
    private fun parseInstance(marker: PsiBuilder.Marker) {
        if (atKeyword("entity") || atKeyword("component") || atKeyword("configuration")) advance()
        // The library prefix (work.) stays outside the reference.
        while (at(NAMES) && peekType(1) == T.DOT) {
            advance()
            advance()
        }
        if (at(NAMES)) wrapToken(E.UNIT_REFERENCE)
        skipStatement()
        marker.done(E.INSTANCE)
    }

    /** Consumes tokens up to (not including) the operator [text] at bracket depth 0, stopping early at `;` or `end`. */
    private fun skipUntilText(text: String) {
        while (!eof && !atText(text) && !at(T.SEMICOLON) && !atKeyword("end")) {
            if (at(OPENERS)) skipGroup() else advance()
        }
    }

    /** A statement or declaration that ends at its `;`, wrapped in [type]. [marker] may already cover a label. */
    private fun parseSimpleStatement(marker: PsiBuilder.Marker = mark(), type: IElementType = E.STATEMENT) {
        val start = builder.currentOffset
        skipStatement()
        if (builder.currentOffset == start) marker.drop() else marker.done(type)
    }

    /**
     * Skips tokens up to and including the next `;` outside brackets. It stops early, without
     * consuming, at `end`, which can't appear inside a statement.
     */
    private fun skipStatement() {
        var depth = 0
        var first = true
        while (!eof) {
            if (!first && atKeyword("end")) break
            if (at(T.SEMICOLON) && depth == 0) {
                advance()
                break
            }
            if (at(OPENERS)) depth++
            if (at(CLOSERS) && depth > 0) depth--
            advance()
            first = false
        }
    }

    private companion object {
        val NAMES = TokenSet.create(T.IDENTIFIER, T.EXTENDED_IDENTIFIER)
        val OPENERS = TokenSet.create(T.LPAREN, T.LBRACKET)
        val CLOSERS = TokenSet.create(T.RPAREN, T.RBRACKET)

        // Keyword groups from IEEE 1076-2008.

        /** Keywords that can follow `end`. */
        val END_TRAILERS = setOf(
            "entity", "architecture", "package", "body", "configuration", "context", "component", "record",
            "units", "protected", "process", "postponed", "block", "generate", "if", "case", "loop", "function",
            "procedure", "for",
        )

        /** Keywords that end the header of `if`, `case` and `for`, telling generate statements from sequential ones. */
        val HEADER_ENDS = setOf("generate", "then", "is", "loop")

        val SUBPROGRAM_KEYWORDS = setOf("function", "procedure", "pure", "impure")

        /** Declarations that end at their `;`. */
        val SIMPLE_DECLARATIONS = setOf(
            "signal", "constant", "variable", "shared", "file", "alias", "attribute", "subtype", "group",
            "disconnect",
        )

        /** Keywords that start a declaration rather than a statement. */
        val DECLARATION_KEYWORDS = SIMPLE_DECLARATIONS + SUBPROGRAM_KEYWORDS + setOf("type", "component", "use")
    }
}
