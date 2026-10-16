package dev.sal.hdl.vhdl

import dev.sal.hdl.HdlParsingTestCase

class VhdlParserTest : HdlParsingTestCase("vhdl", "vhd", VhdlParserDefinition()) {

    // ---- Whole files, compared against PSI dumps in src/test/testData/vhdl ----

    fun testCounter() = doFileTest()

    fun testPackage() = doFileTest()

    fun testTop() = doFileTest()

    // ---- Edge cases ----

    fun testEmptyFile() = assertTree("", "FILE")

    fun testMixedCaseKeywords() = assertTree(
        "ENTITY e IS END ENTITY e;",
        """
        FILE
          DESIGN_UNIT
            KEYWORD('ENTITY')
            IDENTIFIER('e')
            KEYWORD('IS')
            KEYWORD('END')
            KEYWORD('ENTITY')
            IDENTIFIER('e')
            SEMICOLON(';')
        """,
    )

    fun testMissingEndIfClosesAtEndProcess() = assertTree(
        "process begin if a then b; end process;",
        """
        FILE
          PROCESS
            KEYWORD('process')
            KEYWORD('begin')
            IF
              KEYWORD('if')
              IDENTIFIER('a')
              KEYWORD('then')
              STATEMENT
                IDENTIFIER('b')
                SEMICOLON(';')
            KEYWORD('end')
            KEYWORD('process')
            SEMICOLON(';')
        """,
    )

    fun testStrayEndIsSkipped() = assertTree(
        "architecture a of e is begin end if; x <= y; end;",
        """
        FILE
          DESIGN_UNIT
            KEYWORD('architecture')
            IDENTIFIER('a')
            KEYWORD('of')
            UNIT_REFERENCE
              IDENTIFIER('e')
            KEYWORD('is')
            KEYWORD('begin')
            KEYWORD('end')
            KEYWORD('if')
            SEMICOLON(';')
            STATEMENT
              IDENTIFIER('x')
              OPERATOR('<=')
              IDENTIFIER('y')
              SEMICOLON(';')
            KEYWORD('end')
            SEMICOLON(';')
        """,
    )

    fun testConditionalAssignmentIsNotAnIfBranch() = assertTree(
        "if c then x <= a when s else b; else y <= z; end if;",
        """
        FILE
          IF
            KEYWORD('if')
            IDENTIFIER('c')
            KEYWORD('then')
            STATEMENT
              IDENTIFIER('x')
              OPERATOR('<=')
              IDENTIFIER('a')
              KEYWORD('when')
              IDENTIFIER('s')
              KEYWORD('else')
              IDENTIFIER('b')
              SEMICOLON(';')
            KEYWORD('else')
            STATEMENT
              IDENTIFIER('y')
              OPERATOR('<=')
              IDENTIFIER('z')
              SEMICOLON(';')
            KEYWORD('end')
            KEYWORD('if')
            SEMICOLON(';')
        """,
    )

    fun testFunctionDeclarationAndBody() = assertTree(
        "function f return bit; function f return bit is begin return '0'; end;",
        """
        FILE
          SUBPROGRAM
            KEYWORD('function')
            IDENTIFIER('f')
            KEYWORD('return')
            STD_TYPE('bit')
            SEMICOLON(';')
          SUBPROGRAM
            KEYWORD('function')
            IDENTIFIER('f')
            KEYWORD('return')
            STD_TYPE('bit')
            KEYWORD('is')
            KEYWORD('begin')
            STATEMENT
              KEYWORD('return')
              CHARACTER(''0'')
              SEMICOLON(';')
            KEYWORD('end')
            SEMICOLON(';')
        """,
    )

    fun testRandomInputNeverFails() {
        val alphabet = ("entity architecture is of begin end process if then elsif else case when => loop for " +
            "generate ( ) ; : , a b <= \n -- /* */ \" ' component port generic map")
            .split(' ')
        val random = kotlin.random.Random(11)
        repeat(300) {
            val text = buildString {
                repeat(random.nextInt(0, 60)) { append(alphabet[random.nextInt(alphabet.size)]).append(' ') }
            }
            parse(text)
        }
    }
}
