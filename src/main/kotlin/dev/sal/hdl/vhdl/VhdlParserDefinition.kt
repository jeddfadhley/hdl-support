package dev.sal.hdl.vhdl

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
import dev.sal.hdl.vhdl.psi.VhdlElementTypes
import dev.sal.hdl.vhdl.psi.VhdlParser

/** Builds the VHDL PSI tree used by the structure view, folding, navigation and keyword matching. */
class VhdlParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?): Lexer = VhdlLexerAdapter()

    override fun createParser(project: Project?): PsiParser = VhdlParser()

    override fun getFileNodeType(): IFileElementType = VhdlTokenTypes.FILE

    override fun getCommentTokens(): TokenSet = VhdlTokenTypes.COMMENTS

    override fun getStringLiteralElements(): TokenSet = VhdlTokenTypes.STRINGS

    override fun createElement(node: ASTNode): PsiElement = VhdlElementTypes.createElement(node)

    override fun createFile(viewProvider: FileViewProvider): PsiFile = VhdlFile(viewProvider)
}
