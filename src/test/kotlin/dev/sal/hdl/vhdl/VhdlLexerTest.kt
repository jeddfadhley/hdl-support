package dev.sal.hdl.vhdl

import com.intellij.lexer.Lexer
import dev.sal.hdl.HdlLexerTestCase

class VhdlLexerTest : HdlLexerTestCase() {
    override fun createLexer(): Lexer = VhdlLexerAdapter()

    override fun getDirPath(): String = "src/test/testData/vhdl"

    // ---- Whole files, compared against token dumps in src/test/testData/vhdl ----

    fun testCounter() = doFileTest("vhd")

    fun testPackage() = doFileTest("vhd")

    // ---- Edge cases ----

    fun testEmptyFile() = assertTokens("", "")

    fun testWhitespaceOnly() = assertTokens("  \n\t ", "WHITE_SPACE ('  \\n\t ')")

    fun testAttributeVersusCharacterLiteral() = assertTokens(
        "clk'event and clk = '1'",
        """
        IDENTIFIER ('clk')
        ATTRIBUTE (''event')
        WHITE_SPACE (' ')
        KEYWORD ('and')
        WHITE_SPACE (' ')
        IDENTIFIER ('clk')
        WHITE_SPACE (' ')
        OPERATOR ('=')
        WHITE_SPACE (' ')
        CHARACTER (''1'')
        """,
    )

    fun testAttributeAfterClosingParenAndBracket() = assertTokens(
        "f(x)'length a]'high",
        """
        IDENTIFIER ('f')
        LPAREN ('(')
        IDENTIFIER ('x')
        RPAREN (')')
        ATTRIBUTE (''length')
        WHITE_SPACE (' ')
        IDENTIFIER ('a')
        RBRACKET (']')
        ATTRIBUTE (''high')
        """,
    )

    fun testAttributeThatIsAKeyword() = assertTokens(
        "v'range v'REVERSE_RANGE",
        """
        IDENTIFIER ('v')
        ATTRIBUTE (''range')
        WHITE_SPACE (' ')
        IDENTIFIER ('v')
        ATTRIBUTE (''REVERSE_RANGE')
        """,
    )

    fun testChainedAttributes() = assertTokens(
        "t'base'left",
        """
        IDENTIFIER ('t')
        ATTRIBUTE (''base')
        ATTRIBUTE (''left')
        """,
    )

    fun testQualifiedExpression() = assertTokens(
        "unsigned'(\"01\") std_logic'('1')",
        """
        STD_TYPE ('unsigned')
        OPERATOR (''')
        LPAREN ('(')
        STRING ('"01"')
        RPAREN (')')
        WHITE_SPACE (' ')
        STD_TYPE ('std_logic')
        OPERATOR (''')
        LPAREN ('(')
        CHARACTER (''1'')
        RPAREN (')')
        """,
    )

    fun testCharacterLiteralsAfterKeywordsAndOperators() = assertTokens(
        "when '0' => '''",
        """
        KEYWORD ('when')
        WHITE_SPACE (' ')
        CHARACTER (''0'')
        WHITE_SPACE (' ')
        OPERATOR ('=>')
        WHITE_SPACE (' ')
        CHARACTER (''''')
        """,
    )

    fun testCharacterLiteralsInEnumeration() = assertTokens(
        "('a','b')",
        """
        LPAREN ('(')
        CHARACTER (''a'')
        COMMA (',')
        CHARACTER (''b'')
        RPAREN (')')
        """,
    )

    fun testIntegerImageAttribute() = assertTokens(
        "integer'image(n)",
        """
        STD_TYPE ('integer')
        ATTRIBUTE (''image')
        LPAREN ('(')
        IDENTIFIER ('n')
        RPAREN (')')
        """,
    )

    fun testMixedCaseKeywords() = assertTokens(
        "ENTITY Entity entity eNtItY",
        """
        KEYWORD ('ENTITY')
        WHITE_SPACE (' ')
        KEYWORD ('Entity')
        WHITE_SPACE (' ')
        KEYWORD ('entity')
        WHITE_SPACE (' ')
        KEYWORD ('eNtItY')
        """,
    )

    fun testMixedCaseStdTypes() = assertTokens(
        "STD_LOGIC Std_Logic_Vector natural",
        """
        STD_TYPE ('STD_LOGIC')
        WHITE_SPACE (' ')
        STD_TYPE ('Std_Logic_Vector')
        WHITE_SPACE (' ')
        STD_TYPE ('natural')
        """,
    )

    fun testKeywordPrefixIsIdentifier() = assertTokens(
        "entity_name signals",
        """
        IDENTIFIER ('entity_name')
        WHITE_SPACE (' ')
        IDENTIFIER ('signals')
        """,
    )

    fun testBitStrings() = assertTokens(
        "x\"FF\" b\"1010\" X\"0f\" o\"17\" 12UX\"F0\" 8sb\"1\" d\"42\"",
        """
        BIT_STRING ('x"FF"')
        WHITE_SPACE (' ')
        BIT_STRING ('b"1010"')
        WHITE_SPACE (' ')
        BIT_STRING ('X"0f"')
        WHITE_SPACE (' ')
        BIT_STRING ('o"17"')
        WHITE_SPACE (' ')
        BIT_STRING ('12UX"F0"')
        WHITE_SPACE (' ')
        BIT_STRING ('8sb"1"')
        WHITE_SPACE (' ')
        BIT_STRING ('d"42"')
        """,
    )

    fun testBasedAndDecimalLiterals() = assertTokens(
        "16#FF# 2#1010_1010# 16#F.F#E+2 1_000 3.14 1.0E-3 2E6",
        """
        NUMBER ('16#FF#')
        WHITE_SPACE (' ')
        NUMBER ('2#1010_1010#')
        WHITE_SPACE (' ')
        NUMBER ('16#F.F#E+2')
        WHITE_SPACE (' ')
        NUMBER ('1_000')
        WHITE_SPACE (' ')
        NUMBER ('3.14')
        WHITE_SPACE (' ')
        NUMBER ('1.0E-3')
        WHITE_SPACE (' ')
        NUMBER ('2E6')
        """,
    )

    fun testStringsWithDoubledQuotes() = assertTokens(
        "\"say \"\"hi\"\"\" x",
        // Not a raw string: the expected text itself contains three quotes in a row.
        "STRING ('\"say \"\"hi\"\"\"')\nWHITE_SPACE (' ')\nIDENTIFIER ('x')",
    )

    fun testUnterminatedStringStopsAtEndOfLine() = assertTokens(
        "\"abc\nend",
        """
        STRING ('"abc')
        WHITE_SPACE ('\n')
        KEYWORD ('end')
        """,
    )

    fun testUnterminatedStringAtEof() = assertTokens("report \"oops", """
        KEYWORD ('report')
        WHITE_SPACE (' ')
        STRING ('"oops')
        """)

    fun testComments() = assertTokens(
        "a -- line\n/* block\n */ b--x",
        """
        IDENTIFIER ('a')
        WHITE_SPACE (' ')
        LINE_COMMENT ('-- line')
        WHITE_SPACE ('\n')
        BLOCK_COMMENT ('/* block\n */')
        WHITE_SPACE (' ')
        IDENTIFIER ('b')
        LINE_COMMENT ('--x')
        """,
    )

    fun testUnterminatedBlockCommentAtEof() = assertTokens(
        "x /* never\nclosed",
        """
        IDENTIFIER ('x')
        WHITE_SPACE (' ')
        BLOCK_COMMENT ('/* never\nclosed')
        """,
    )

    fun testNonAsciiInComments() = assertTokens(
        "-- Größe ≤ 8 ✓\n/* 日本 */",
        """
        LINE_COMMENT ('-- Größe ≤ 8 ✓')
        WHITE_SPACE ('\n')
        BLOCK_COMMENT ('/* 日本 */')
        """,
    )

    fun testAttributeAcrossWhitespaceAndComment() = assertTokens(
        "clk /* c */ 'event",
        """
        IDENTIFIER ('clk')
        WHITE_SPACE (' ')
        BLOCK_COMMENT ('/* c */')
        WHITE_SPACE (' ')
        ATTRIBUTE (''event')
        """,
    )

    fun testExtendedIdentifiers() = assertTokens(
        "\\my signal\\ \\a\\\\b\\ \\open",
        """
        EXTENDED_IDENTIFIER ('\my signal\')
        WHITE_SPACE (' ')
        EXTENDED_IDENTIFIER ('\a\\b\')
        WHITE_SPACE (' ')
        EXTENDED_IDENTIFIER ('\open')
        """,
    )

    fun testOperators() = assertTokens(
        "a <= b ** 2 /= c ?= d := e => f <> g",
        """
        IDENTIFIER ('a')
        WHITE_SPACE (' ')
        OPERATOR ('<=')
        WHITE_SPACE (' ')
        IDENTIFIER ('b')
        WHITE_SPACE (' ')
        OPERATOR ('**')
        WHITE_SPACE (' ')
        NUMBER ('2')
        WHITE_SPACE (' ')
        OPERATOR ('/=')
        WHITE_SPACE (' ')
        IDENTIFIER ('c')
        WHITE_SPACE (' ')
        OPERATOR ('?=')
        WHITE_SPACE (' ')
        IDENTIFIER ('d')
        WHITE_SPACE (' ')
        OPERATOR (':=')
        WHITE_SPACE (' ')
        IDENTIFIER ('e')
        WHITE_SPACE (' ')
        OPERATOR ('=>')
        WHITE_SPACE (' ')
        IDENTIFIER ('f')
        WHITE_SPACE (' ')
        OPERATOR ('<>')
        WHITE_SPACE (' ')
        IDENTIFIER ('g')
        """,
    )

    fun testVeryLongLine() {
        assertLexesCompletely("s <= " + (1..20_000).joinToString(" and ") { "a$it'event" } + ";")
        assertLexesCompletely("-- " + "x".repeat(200_000))
    }

    fun testFuzzNeverThrows() = fuzz("abxXuUsS019'\"\\/*-#_ \n\t;:.,()[]<>=&|?ée日\r")
}
