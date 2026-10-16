package dev.sal.hdl

import com.intellij.lexer.Lexer
import com.intellij.testFramework.LexerTestCase
import kotlin.random.Random

/**
 * Base class for the lexer tests.
 *
 * File tests (`doFileTest("v")`) lex `src/test/testData/<dir>/<testName>.<ext>` and compare the
 * result with `<testName>.txt` in the same directory. If the `.txt` file is missing, the test
 * creates it and fails, so check the new dump by hand before committing it.
 */
abstract class HdlLexerTestCase : LexerTestCase() {
    override fun getPathToTestDataFile(extension: String): String =
        "$dirPath/${getTestName(true)}$extension"

    /** Lexes [text] and compares the token dump with [expected], given as a trimmed multi-line string. */
    protected fun assertTokens(text: String, expected: String) {
        doTest(text, expected.trimIndent())
        checkCorrectRestart(text)
    }

    /** Lexes [text] and checks that the tokens cover it exactly, with no gaps or overlaps. */
    protected fun assertLexesCompletely(text: CharSequence, lexer: Lexer = createLexer()) {
        lexer.start(text)
        var expectedStart = 0
        while (lexer.tokenType != null) {
            assertEquals("gap or overlap before offset ${lexer.tokenStart}", expectedStart, lexer.tokenStart)
            assertTrue("empty token at ${lexer.tokenStart}", lexer.tokenEnd > lexer.tokenStart)
            expectedStart = lexer.tokenEnd
            lexer.advance()
        }
        assertEquals(text.length, expectedStart)
    }

    /** Throws random junk built from [alphabet] at the lexer. It must never throw and must cover every character. */
    protected fun fuzz(alphabet: String) {
        val random = Random(42)
        repeat(500) {
            val length = random.nextInt(0, 200)
            val text = buildString { repeat(length) { append(alphabet[random.nextInt(alphabet.length)]) } }
            assertLexesCompletely(text)
        }
    }
}
