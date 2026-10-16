package dev.sal.hdl.verilog

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import dev.sal.hdl.verilog.psi.VerilogElementTypes
import dev.sal.hdl.verilog.psi.VerilogParser

/** Builds the Verilog PSI tree used by the structure view, folding, navigation and keyword matching. */
class VerilogParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?): Lexer = VerilogLexerAdapter()

    override fun createParser(project: Project?): PsiParser = VerilogParser()

    override fun getFileNodeType(): IFileElementType = VerilogTokenTypes.FILE

    override fun getCommentTokens(): TokenSet = VerilogTokenTypes.COMMENTS

    override fun getStringLiteralElements(): TokenSet = VerilogTokenTypes.STRINGS

    override fun createElement(node: ASTNode): PsiElement = VerilogElementTypes.createElement(node)

    override fun createFile(viewProvider: FileViewProvider): PsiFile = VerilogFile(viewProvider)
}
