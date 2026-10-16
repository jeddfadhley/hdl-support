package dev.sal.hdl

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.ParsingTestCase

/**
 * Base class for the parser tests.
 *
 * File tests (`doFileTest()`) parse `src/test/testData/<dir>/<testName>.<ext>` and compare the PSI
 * tree with `<testName>.psi.txt` in the same directory. As with the lexer dumps, a missing `.psi.txt`
 * is created on the first run, which then fails, so check the new dump by hand before committing it.
 */
abstract class HdlParsingTestCase(
    private val dir: String,
    private val extension: String,
    definition: ParserDefinition,
) : ParsingTestCase(dir, extension, definition) {

    override fun getTestDataPath(): String = "src/test/testData"

    /** Parses the test's sample file and compares the tree with its `.psi.txt` dump. */
    protected fun doFileTest() {
        val name = getTestName(true)
        val text = loadFile("$name.$extension")
        val file = createPsiFile(name, text)
        assertCoversText(file, text)
        doCheckResult(myFullDataPath, file, false, "$name.psi", true, false)
    }

    /** Parses [text] and returns the file. The parser must never report errors or lose text. */
    protected fun parse(text: String): PsiFile {
        val file = createPsiFile("a", text)
        assertCoversText(file, text)
        return file
    }

    /**
     * Parses [text] and compares the tree with [expected], given as a trimmed multi-line string.
     * The tree is printed compactly: `FILE` for the root, `TYPE` for a node and `TYPE('text')` for a
     * token, indented two spaces per level, without whitespace.
     */
    protected fun assertTree(text: String, expected: String) {
        val file = parse(text)
        assertEquals(expected.trimIndent(), compactTree(file.node))
    }

    private fun compactTree(root: ASTNode): String = buildString {
        fun print(node: ASTNode, indent: Int) {
            if (node.elementType == TokenType.WHITE_SPACE) return
            if (isNotEmpty()) append('\n')
            append("  ".repeat(indent))
            when {
                node == root -> append("FILE")
                node.firstChildNode == null -> append(node.elementType).append("('").append(node.text).append("')")
                else -> append(node.elementType)
            }
            for (child in node.getChildren(null)) print(child, indent + 1)
        }
        print(root, 0)
    }

    private fun assertCoversText(file: PsiFile, text: String) {
        assertEquals("the tree must cover the whole text", text, file.text)
        val errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)
        assertTrue("the outline parser must not report errors: ${errors.map { it.errorDescription }}", errors.isEmpty())
    }
}
