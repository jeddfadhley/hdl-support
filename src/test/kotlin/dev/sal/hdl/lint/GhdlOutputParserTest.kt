package dev.sal.hdl.lint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Uses output captured from GHDL 6.0.0; the tool doesn't need to be installed. */
class GhdlOutputParserTest {
    @Test
    fun parsesError() {
        val output = """
            bad.vhd:7:23:error: missing ";" at end of object declaration
              signal s : std_logic
                                  ^
        """.trimIndent()

        assertEquals(
            listOf(LintMessage("bad.vhd", 7, 23, LintSeverity.ERROR, "missing \";\" at end of object declaration")),
            GhdlOutputParser.parse(output),
        )
    }

    @Test
    fun joinsFollowUpLineAtSamePosition() {
        val output = """
            w.vhd:9:14:error: ';' expected at end of process
              end process
                         ^
            w.vhd:9:14:error: (found: 'end')
        """.trimIndent()

        assertEquals(
            listOf(LintMessage("w.vhd", 9, 14, LintSeverity.ERROR, "';' expected at end of process (found: 'end')")),
            GhdlOutputParser.parse(output),
        )
    }

    @Test
    fun parsesWarningAndOptionCode() {
        val output = "z.vhd:4:3:warning: infinite loop for this process without a wait statement [-Wmissing-wait]"

        assertEquals(
            listOf(
                LintMessage(
                    "z.vhd", 4, 3, LintSeverity.WARNING,
                    "infinite loop for this process without a wait statement", "missing-wait",
                ),
            ),
            GhdlOutputParser.parse(output),
        )
    }

    @Test
    fun olderFormatWithoutSeverityIsAnError() {
        val message = GhdlOutputParser.parseLine("/home/me/top.vhd:12:5: missing \";\"")!!
        assertEquals("/home/me/top.vhd", message.file)
        assertEquals(LintSeverity.ERROR, message.severity)
        assertEquals("missing \";\"", message.message)
    }

    @Test
    fun ignoresNonDiagnosticLines() {
        assertNull(GhdlOutputParser.parseLine("ghdl: compilation error"))
        assertNull(GhdlOutputParser.parseLine("  signal s : std_logic"))
        assertNull(GhdlOutputParser.parseLine("                      ^"))
    }
}
