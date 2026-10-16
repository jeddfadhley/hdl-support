package dev.sal.hdl.verilog

import com.intellij.lexer.Lexer
import dev.sal.hdl.HdlLexerTestCase

class VerilogLexerTest : HdlLexerTestCase() {
    override fun createLexer(): Lexer = VerilogLexerAdapter()

    override fun getDirPath(): String = "src/test/testData/verilog"

    // ---- Whole files, compared against token dumps in src/test/testData/verilog ----

    fun testCounter() = doFileTest("v")

    fun testFsm() = doFileTest("sv")

    fun testTestbench() = doFileTest("sv")

    // ---- Edge cases ----

    fun testEmptyFile() = assertTokens("", "")

    // The token dump escapes newlines but prints tabs as they are.
    fun testWhitespaceOnly() = assertTokens(" \t\n\n  ", "WHITE_SPACE (' \t\\n\\n  ')")

    fun testUnterminatedBlockCommentAtEof() = assertTokens(
        "a /* never closed\nmodule",
        """
        IDENTIFIER ('a')
        WHITE_SPACE (' ')
        BLOCK_COMMENT ('/* never closed\nmodule')
        """,
    )

    fun testUnterminatedBlockCommentEndingInStars() = assertTokens("/* x **", "BLOCK_COMMENT ('/* x **')")

    fun testBlockCommentWithStars() = assertTokens(
        "/** a * b **/x",
        """
        BLOCK_COMMENT ('/** a * b **/')
        IDENTIFIER ('x')
        """,
    )

    fun testBlockCommentDoesNotNest() = assertTokens(
        "/* a /* b */ c",
        """
        BLOCK_COMMENT ('/* a /* b */')
        WHITE_SPACE (' ')
        IDENTIFIER ('c')
        """,
    )

    fun testUnterminatedStringAtEof() = assertTokens("\$display(\"oops", """
        SYSTEM_TASK ('${'$'}display')
        LPAREN ('(')
        STRING ('"oops')
        """)

    fun testUnterminatedStringStopsAtEndOfLine() = assertTokens(
        "\"abc\nwire",
        """
        STRING ('"abc')
        WHITE_SPACE ('\n')
        KEYWORD ('wire')
        """,
    )

    fun testStringEscapes() = assertTokens(
        """"a\"b\\" x""",
        """
        STRING ('"a\"b\\"')
        WHITE_SPACE (' ')
        IDENTIFIER ('x')
        """,
    )

    fun testStringLineContinuation() = assertTokens("\"a\\\nb\"", "STRING ('\"a\\\\nb\"')")

    fun testBackslashAtEndOfFileInString() = assertTokens("\"a\\", "STRING ('\"a\\')")

    fun testNonAsciiInComments() = assertTokens(
        "// café — 日本語 ✓\n/* naïve Ω */",
        """
        LINE_COMMENT ('// café — 日本語 ✓')
        WHITE_SPACE ('\n')
        BLOCK_COMMENT ('/* naïve Ω */')
        """,
    )

    fun testNonAsciiOutsideCommentsIsBadCharacter() = assertTokens(
        "a é",
        """
        IDENTIFIER ('a')
        WHITE_SPACE (' ')
        BAD_CHARACTER ('é')
        """,
    )

    fun testVeryLongLine() {
        val line = "assign x = " + (1..20_000).joinToString(" + ") { "a$it" } + ";"
        assertLexesCompletely(line)
        assertLexesCompletely("// " + "x".repeat(200_000))
        assertLexesCompletely("\"" + "y".repeat(200_000))
    }

    fun testEscapedIdentifiers() = assertTokens(
        "\\foo+bar  \\bus[3] \\a.b,c ;",
        """
        ESCAPED_IDENTIFIER ('\foo+bar')
        WHITE_SPACE ('  ')
        ESCAPED_IDENTIFIER ('\bus[3]')
        WHITE_SPACE (' ')
        ESCAPED_IDENTIFIER ('\a.b,c')
        WHITE_SPACE (' ')
        SEMICOLON (';')
        """,
    )

    fun testLoneBackslashIsBadCharacter() = assertTokens("\\", "BAD_CHARACTER ('\\')")

    fun testSizedNumbers() = assertTokens(
        "8'hFF 4'b10x1 4'bz_1?0 32'd1_000 12'o7_7 8'sb1010 'hDEAD 16'h dead 'b0",
        """
        NUMBER ('8'hFF')
        WHITE_SPACE (' ')
        NUMBER ('4'b10x1')
        WHITE_SPACE (' ')
        NUMBER ('4'bz_1?0')
        WHITE_SPACE (' ')
        NUMBER ('32'd1_000')
        WHITE_SPACE (' ')
        NUMBER ('12'o7_7')
        WHITE_SPACE (' ')
        NUMBER ('8'sb1010')
        WHITE_SPACE (' ')
        NUMBER (''hDEAD')
        WHITE_SPACE (' ')
        NUMBER ('16'h dead')
        WHITE_SPACE (' ')
        NUMBER (''b0')
        """,
    )

    fun testUppercaseBaseAndDigits() = assertTokens(
        "8'HXZ 4'B1X0Z 8'D255",
        """
        NUMBER ('8'HXZ')
        WHITE_SPACE (' ')
        NUMBER ('4'B1X0Z')
        WHITE_SPACE (' ')
        NUMBER ('8'D255')
        """,
    )

    fun testUnbasedUnsizedNumbers() = assertTokens(
        "'0 '1 'x 'Z",
        """
        NUMBER (''0')
        WHITE_SPACE (' ')
        NUMBER (''1')
        WHITE_SPACE (' ')
        NUMBER (''x')
        WHITE_SPACE (' ')
        NUMBER (''Z')
        """,
    )

    fun testPlainRealAndTimeNumbers() = assertTokens(
        "42 1_000 3.14 1e9 2.5E-3 10ns 1.5ps 1step",
        """
        NUMBER ('42')
        WHITE_SPACE (' ')
        NUMBER ('1_000')
        WHITE_SPACE (' ')
        NUMBER ('3.14')
        WHITE_SPACE (' ')
        NUMBER ('1e9')
        WHITE_SPACE (' ')
        NUMBER ('2.5E-3')
        WHITE_SPACE (' ')
        NUMBER ('10ns')
        WHITE_SPACE (' ')
        NUMBER ('1.5ps')
        WHITE_SPACE (' ')
        NUMBER ('1step')
        """,
    )

    fun testCastAndAssignmentPattern() = assertTokens(
        "int'(x) '{0, 1}",
        """
        KEYWORD ('int')
        OPERATOR (''')
        LPAREN ('(')
        IDENTIFIER ('x')
        RPAREN (')')
        WHITE_SPACE (' ')
        OPERATOR (''')
        LBRACE ('{')
        NUMBER ('0')
        COMMA (',')
        WHITE_SPACE (' ')
        NUMBER ('1')
        RBRACE ('}')
        """,
    )

    fun testSystemTasks() = assertTokens(
        "\$display \$finish \$clog2(\$) \$",
        """
        SYSTEM_TASK ('${'$'}display')
        WHITE_SPACE (' ')
        SYSTEM_TASK ('${'$'}finish')
        WHITE_SPACE (' ')
        SYSTEM_TASK ('${'$'}clog2')
        LPAREN ('(')
        OPERATOR ('${'$'}')
        RPAREN (')')
        WHITE_SPACE (' ')
        OPERATOR ('${'$'}')
        """,
    )

    fun testDirectives() = assertTokens(
        "`define W 8\n`include \"defs.vh\"\n`timescale 1ns/1ps\n`ifdef SIM `W `endif",
        """
        DIRECTIVE ('`define')
        WHITE_SPACE (' ')
        IDENTIFIER ('W')
        WHITE_SPACE (' ')
        NUMBER ('8')
        WHITE_SPACE ('\n')
        DIRECTIVE ('`include')
        WHITE_SPACE (' ')
        STRING ('"defs.vh"')
        WHITE_SPACE ('\n')
        DIRECTIVE ('`timescale')
        WHITE_SPACE (' ')
        NUMBER ('1ns')
        OPERATOR ('/')
        NUMBER ('1ps')
        WHITE_SPACE ('\n')
        DIRECTIVE ('`ifdef')
        WHITE_SPACE (' ')
        IDENTIFIER ('SIM')
        WHITE_SPACE (' ')
        DIRECTIVE ('`W')
        WHITE_SPACE (' ')
        DIRECTIVE ('`endif')
        """,
    )

    fun testKeywordsAreWholeWordsAndCaseSensitive() = assertTokens(
        "module modules Module endmodule always_ff always_ffx",
        """
        KEYWORD ('module')
        WHITE_SPACE (' ')
        IDENTIFIER ('modules')
        WHITE_SPACE (' ')
        IDENTIFIER ('Module')
        WHITE_SPACE (' ')
        KEYWORD ('endmodule')
        WHITE_SPACE (' ')
        KEYWORD ('always_ff')
        WHITE_SPACE (' ')
        IDENTIFIER ('always_ffx')
        """,
    )

    fun testIdentifiersWithDollar() = assertTokens("a\$b _x1", """
        IDENTIFIER ('a${'$'}b')
        WHITE_SPACE (' ')
        IDENTIFIER ('_x1')
        """)

    fun testOperators() = assertTokens(
        "a <= b === c !== d <<< 2 |-> e ##1 f",
        """
        IDENTIFIER ('a')
        WHITE_SPACE (' ')
        OPERATOR ('<=')
        WHITE_SPACE (' ')
        IDENTIFIER ('b')
        WHITE_SPACE (' ')
        OPERATOR ('===')
        WHITE_SPACE (' ')
        IDENTIFIER ('c')
        WHITE_SPACE (' ')
        OPERATOR ('!==')
        WHITE_SPACE (' ')
        IDENTIFIER ('d')
        WHITE_SPACE (' ')
        OPERATOR ('<<<')
        WHITE_SPACE (' ')
        NUMBER ('2')
        WHITE_SPACE (' ')
        OPERATOR ('|->')
        WHITE_SPACE (' ')
        IDENTIFIER ('e')
        WHITE_SPACE (' ')
        OPERATOR ('##')
        NUMBER ('1')
        WHITE_SPACE (' ')
        IDENTIFIER ('f')
        """,
    )

    fun testPunctuation() = assertTokens(
        "u.a[0]{};,",
        """
        IDENTIFIER ('u')
        DOT ('.')
        IDENTIFIER ('a')
        LBRACKET ('[')
        NUMBER ('0')
        RBRACKET (']')
        LBRACE ('{')
        RBRACE ('}')
        SEMICOLON (';')
        COMMA (',')
        """,
    )

    fun testFuzzNeverThrows() = fuzz("abz019'\"\\/*\$`_ \n\t;:.,()[]{}<>=!~&|^+-%#@?sShHbBxXzZé日\r")
}
