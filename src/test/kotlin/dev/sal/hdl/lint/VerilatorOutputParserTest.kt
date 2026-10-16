package dev.sal.hdl.lint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Uses output captured from Verilator 5.052; the tool doesn't need to be installed. */
class VerilatorOutputParserTest {
    @Test
    fun parsesSyntaxError() {
        val output = """
            %Error: counter.v:10:9: syntax error, unexpected else
               10 |         else count <= count + 1;
                  |         ^~~~
                    ... See the manual at https://verilator.org/verilator_doc.html?v=5.052 for more assistance.
            %Error: Exiting due to 1 error(s)
        """.trimIndent()

        assertEquals(
            listOf(LintMessage("counter.v", 10, 9, LintSeverity.ERROR, "syntax error, unexpected else", null)),
            VerilatorOutputParser.parse(output),
        )
    }

    @Test
    fun parsesWarningWithCode() {
        val output = """
            %Warning-UNUSEDSIGNAL: warn.v:6:10: Signal is not driven, nor used: 'spare'
                                              : ... note: In instance 'warn'
                6 |     wire spare;
                  |          ^~~~~
                                   ... For warning description see https://verilator.org/warn/UNUSEDSIGNAL?v=5.052
                                   ... Use "/* verilator lint_off UNUSEDSIGNAL */" and lint_on around source to disable this message.
            %Error: Exiting due to 1 warning(s)
        """.trimIndent()

        assertEquals(
            listOf(
                LintMessage(
                    "warn.v", 6, 10, LintSeverity.WARNING,
                    "Signal is not driven, nor used: 'spare'", "UNUSEDSIGNAL",
                ),
            ),
            VerilatorOutputParser.parse(output),
        )
    }

    @Test
    fun columnIsOptional() {
        val message = VerilatorOutputParser.parseLine("%Warning-DECLFILENAME: /tmp/x/top.v:1: Filename 'top' does not match MODULE name: 'other'")!!
        assertEquals("/tmp/x/top.v", message.file)
        assertEquals(1, message.line)
        assertNull(message.column)
        assertEquals("DECLFILENAME", message.code)
    }

    @Test
    fun errorWithCode() {
        val message = VerilatorOutputParser.parseLine("%Error-PINNOTFOUND: /a/b/top.sv:12:5: Pin not found: 'foo'")!!
        assertEquals(LintSeverity.ERROR, message.severity)
        assertEquals("PINNOTFOUND", message.code)
        assertEquals(12, message.line)
        assertEquals(5, message.column)
    }

    @Test
    fun ignoresLinesWithoutLocation() {
        assertNull(VerilatorOutputParser.parseLine("%Error: Exiting due to 3 error(s)"))
        assertNull(VerilatorOutputParser.parseLine("%Error: Cannot find file containing module: 'sub'"))
        assertNull(VerilatorOutputParser.parseLine("   10 |         else count <= count + 1;"))
        assertNull(VerilatorOutputParser.parseLine(""))
    }

    @Test
    fun dropsDuplicates() {
        val line = "%Warning-WIDTH: a.v:3:4: Operator ASSIGN expects 8 bits"
        assertEquals(1, VerilatorOutputParser.parse("$line\n$line\n").size)
    }
}
