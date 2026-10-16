package dev.sal.hdl.vhdl

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as Default
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import dev.sal.hdl.vhdl.VhdlTokenTypes as T

/**
 * Maps VHDL tokens to colours. Every key falls back to a `DefaultLanguageHighlighterColors`
 * entry, so the plugin follows the user's colour scheme unless they override it under
 * Settings → Editor → Color Scheme → VHDL.
 */
class VhdlSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer = VhdlLexerAdapter()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> =
        pack(ATTRIBUTES[tokenType])

    companion object {
        val KEYWORD = createTextAttributesKey("VHDL_KEYWORD", Default.KEYWORD)
        val IDENTIFIER = createTextAttributesKey("VHDL_IDENTIFIER", Default.IDENTIFIER)
        val STD_TYPE = createTextAttributesKey("VHDL_STD_TYPE", Default.CLASS_REFERENCE)
        val ATTRIBUTE = createTextAttributesKey("VHDL_ATTRIBUTE", Default.METADATA)
        val LINE_COMMENT = createTextAttributesKey("VHDL_LINE_COMMENT", Default.LINE_COMMENT)
        val BLOCK_COMMENT = createTextAttributesKey("VHDL_BLOCK_COMMENT", Default.BLOCK_COMMENT)
        val STRING = createTextAttributesKey("VHDL_STRING", Default.STRING)
        val CHARACTER = createTextAttributesKey("VHDL_CHARACTER", Default.STRING)
        val BIT_STRING = createTextAttributesKey("VHDL_BIT_STRING", Default.NUMBER)
        val NUMBER = createTextAttributesKey("VHDL_NUMBER", Default.NUMBER)
        val OPERATOR = createTextAttributesKey("VHDL_OPERATOR", Default.OPERATION_SIGN)
        val PARENTHESES = createTextAttributesKey("VHDL_PARENTHESES", Default.PARENTHESES)
        val BRACKETS = createTextAttributesKey("VHDL_BRACKETS", Default.BRACKETS)
        val SEMICOLON = createTextAttributesKey("VHDL_SEMICOLON", Default.SEMICOLON)
        val COMMA = createTextAttributesKey("VHDL_COMMA", Default.COMMA)
        val DOT = createTextAttributesKey("VHDL_DOT", Default.DOT)
        val BAD_CHARACTER = createTextAttributesKey("VHDL_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER)

        private val ATTRIBUTES: Map<IElementType, TextAttributesKey> = mapOf(
            T.KEYWORD to KEYWORD,
            T.IDENTIFIER to IDENTIFIER,
            T.EXTENDED_IDENTIFIER to IDENTIFIER,
            T.STD_TYPE to STD_TYPE,
            T.ATTRIBUTE to ATTRIBUTE,
            T.LINE_COMMENT to LINE_COMMENT,
            T.BLOCK_COMMENT to BLOCK_COMMENT,
            T.STRING to STRING,
            T.CHARACTER to CHARACTER,
            T.BIT_STRING to BIT_STRING,
            T.NUMBER to NUMBER,
            T.OPERATOR to OPERATOR,
            T.LPAREN to PARENTHESES,
            T.RPAREN to PARENTHESES,
            T.LBRACKET to BRACKETS,
            T.RBRACKET to BRACKETS,
            T.SEMICOLON to SEMICOLON,
            T.COMMA to COMMA,
            T.DOT to DOT,
            TokenType.BAD_CHARACTER to BAD_CHARACTER,
        )
    }
}
