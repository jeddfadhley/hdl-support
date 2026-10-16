package dev.sal.hdl.lint

import com.intellij.openapi.util.TextRange
import org.junit.Assert.assertEquals
import org.junit.Test

class LintRangesTest {
    /** Returns the underlined text for a single-line document. */
    private fun underline(line: String, column: Int?): String {
        val range = LintRanges.highlightRange(line, 0, line.length, column)
        return range.substring(line)
    }

    @Test
    fun columnAtIdentifierCoversWholeIdentifier() {
        assertEquals("spare", underline("    wire spare;", 10))
    }

    @Test
    fun columnAtKeyword() {
        assertEquals("else", underline("        else count <= count + 1;", 9))
    }

    @Test
    fun columnAtOperatorCoversOperator() {
        assertEquals("<=", underline("a <= b;", 3))
    }

    @Test
    fun columnPastEndOfLineFallsBackToLastToken() {
        // GHDL reports a missing ';' just past the end of the line.
        assertEquals("std_logic", underline("  signal s : std_logic", 23))
        assertEquals("std_logic", underline("  signal s : std_logic", 99))
    }

    @Test
    fun columnOnWhitespaceFallsBackToPreviousToken() {
        assertEquals("process", underline("  end process   ", 15))
    }

    @Test
    fun noColumnCoversTrimmedLine() {
        assertEquals("wire spare;", underline("    wire spare;  ", null))
    }

    @Test
    fun blankLineGivesEmptyRange() {
        assertEquals(TextRange(3, 3), LintRanges.highlightRange("   ", 0, 3, 2))
        assertEquals("", underline("", 1))
        assertEquals("", underline("    ", null))
    }

    @Test
    fun worksOnLaterLinesOfADocument() {
        val text = "module m;\n  wire x;\nendmodule\n"
        val start = text.indexOf("  wire")
        val end = text.indexOf('\n', start)
        assertEquals("x", LintRanges.highlightRange(text, start, end, 8).substring(text))
    }
}
