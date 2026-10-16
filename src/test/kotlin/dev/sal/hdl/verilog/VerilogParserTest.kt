package dev.sal.hdl.verilog

import dev.sal.hdl.HdlParsingTestCase

class VerilogParserTest : HdlParsingTestCase("verilog", "sv", VerilogParserDefinition()) {

    // ---- Whole files, compared against PSI dumps in src/test/testData/verilog ----

    fun testCounter() = doFileTestWithExtension("v")

    fun testFsm() = doFileTest()

    fun testTestbench() = doFileTest()

    fun testGenerate() = doFileTest()

    fun testClasses() = doFileTest()

    private fun doFileTestWithExtension(ext: String) {
        // counter.v is plain Verilog; the parser doesn't care about the extension.
        val name = getTestName(true)
        val text = loadFile("$name.$ext")
        val file = parse(text)
        doCheckResult(myFullDataPath, file, false, "$name.psi", true, false)
    }

    // ---- Edge cases ----

    fun testEmptyFile() = assertTree("", "FILE")

    fun testWhitespaceOnly() = assertTree(" \n\t ", "FILE")

    fun testUnterminatedModule() = assertTree(
        "module m; wire a;",
        """
        FILE
          DESIGN_UNIT
            KEYWORD('module')
            IDENTIFIER('m')
            SEMICOLON(';')
            DECLARATION
              KEYWORD('wire')
              IDENTIFIER('a')
              SEMICOLON(';')
        """,
    )

    fun testMissingEndStopsAtEndmodule() = assertTree(
        "module m; initial begin x = 1; endmodule\nmodule n; endmodule",
        """
        FILE
          DESIGN_UNIT
            KEYWORD('module')
            IDENTIFIER('m')
            SEMICOLON(';')
            PROCESS
              KEYWORD('initial')
              BLOCK
                KEYWORD('begin')
                IDENTIFIER('x')
                OPERATOR('=')
                NUMBER('1')
                SEMICOLON(';')
            KEYWORD('endmodule')
          DESIGN_UNIT
            KEYWORD('module')
            IDENTIFIER('n')
            SEMICOLON(';')
            KEYWORD('endmodule')
        """,
    )

    fun testStrayEndKeywords() = assertTree(
        "end endcase endmodule",
        """
        FILE
          KEYWORD('end')
          KEYWORD('endcase')
          KEYWORD('endmodule')
        """,
    )

    fun testMissingSemicolonBeforeAlways() = assertTree(
        "module m; assign a = b\nalways @* c = d; endmodule",
        """
        FILE
          DESIGN_UNIT
            KEYWORD('module')
            IDENTIFIER('m')
            SEMICOLON(';')
            KEYWORD('assign')
            IDENTIFIER('a')
            OPERATOR('=')
            IDENTIFIER('b')
            PROCESS
              KEYWORD('always')
              OPERATOR('@')
              OPERATOR('*')
              IDENTIFIER('c')
              OPERATOR('=')
              IDENTIFIER('d')
              SEMICOLON(';')
            KEYWORD('endmodule')
        """,
    )

    fun testDefineDoesNotSwallowNextLine() = assertTree(
        "`define W 8\nmodule m; endmodule",
        """
        FILE
          DIRECTIVE('`define')
          IDENTIFIER('W')
          NUMBER('8')
          DESIGN_UNIT
            KEYWORD('module')
            IDENTIFIER('m')
            SEMICOLON(';')
            KEYWORD('endmodule')
        """,
    )

    fun testDefineWithLineContinuation() = assertTree(
        "`define M(x) \\\n  (x + 1)\nwire w;",
        """
        FILE
          DIRECTIVE('`define')
          IDENTIFIER('M')
          LPAREN('(')
          IDENTIFIER('x')
          RPAREN(')')
          BAD_CHARACTER('\')
          LPAREN('(')
          IDENTIFIER('x')
          OPERATOR('+')
          NUMBER('1')
          RPAREN(')')
          DECLARATION
            KEYWORD('wire')
            IDENTIFIER('w')
            SEMICOLON(';')
        """,
    )

    fun testConstraintBlockNeedsNoSemicolon() = assertTree(
        "class c; constraint lim { x < 4; } rand int y; endclass",
        """
        FILE
          DESIGN_UNIT
            KEYWORD('class')
            IDENTIFIER('c')
            SEMICOLON(';')
            KEYWORD('constraint')
            IDENTIFIER('lim')
            LBRACE('{')
            IDENTIFIER('x')
            OPERATOR('<')
            NUMBER('4')
            SEMICOLON(';')
            RBRACE('}')
            DECLARATION
              KEYWORD('rand')
              KEYWORD('int')
              IDENTIFIER('y')
              SEMICOLON(';')
            KEYWORD('endclass')
        """,
    )

    fun testRandomInputNeverFails() {
        val alphabet = "module endmodule begin end case endcase ( ) [ ] { } ; : , # @ a b `define \n /* */ // \" '"
            .split(' ')
        val random = kotlin.random.Random(7)
        repeat(300) {
            val text = buildString {
                repeat(random.nextInt(0, 60)) { append(alphabet[random.nextInt(alphabet.size)]).append(' ') }
            }
            parse(text)
        }
    }
}
