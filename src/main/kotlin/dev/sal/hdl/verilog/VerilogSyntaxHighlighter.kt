package dev.sal.hdl.verilog

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as Default
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import dev.sal.hdl.verilog.VerilogTokenTypes as T

/**
 * Maps Verilog tokens to colours. Every key falls back to a `DefaultLanguageHighlighterColors`
 * entry, so the plugin follows the user's colour scheme unless they override it under
 * Settings → Editor → Color Scheme → Verilog/SystemVerilog.
 */
class VerilogSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer = VerilogLexerAdapter()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> =
        pack(ATTRIBUTES[tokenType])

    companion object {
        val KEYWORD = createTextAttributesKey("VERILOG_KEYWORD", Default.KEYWORD)
        val IDENTIFIER = createTextAttributesKey("VERILOG_IDENTIFIER", Default.IDENTIFIER)
        val LINE_COMMENT = createTextAttributesKey("VERILOG_LINE_COMMENT", Default.LINE_COMMENT)
        val BLOCK_COMMENT = createTextAttributesKey("VERILOG_BLOCK_COMMENT", Default.BLOCK_COMMENT)
        val STRING = createTextAttributesKey("VERILOG_STRING", Default.STRING)
        val NUMBER = createTextAttributesKey("VERILOG_NUMBER", Default.NUMBER)
        val SYSTEM_TASK = createTextAttributesKey("VERILOG_SYSTEM_TASK", Default.STATIC_METHOD)
        val DIRECTIVE = createTextAttributesKey("VERILOG_DIRECTIVE", Default.METADATA)
        val OPERATOR = createTextAttributesKey("VERILOG_OPERATOR", Default.OPERATION_SIGN)
        val PARENTHESES = createTextAttributesKey("VERILOG_PARENTHESES", Default.PARENTHESES)
        val BRACKETS = createTextAttributesKey("VERILOG_BRACKETS", Default.BRACKETS)
        val BRACES = createTextAttributesKey("VERILOG_BRACES", Default.BRACES)
        val SEMICOLON = createTextAttributesKey("VERILOG_SEMICOLON", Default.SEMICOLON)
        val COMMA = createTextAttributesKey("VERILOG_COMMA", Default.COMMA)
        val DOT = createTextAttributesKey("VERILOG_DOT", Default.DOT)
        val BAD_CHARACTER = createTextAttributesKey("VERILOG_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER)

        private val ATTRIBUTES: Map<IElementType, TextAttributesKey> = mapOf(
            T.KEYWORD to KEYWORD,
            T.IDENTIFIER to IDENTIFIER,
            T.ESCAPED_IDENTIFIER to IDENTIFIER,
            T.LINE_COMMENT to LINE_COMMENT,
            T.BLOCK_COMMENT to BLOCK_COMMENT,
            T.STRING to STRING,
            T.NUMBER to NUMBER,
            T.SYSTEM_TASK to SYSTEM_TASK,
            T.DIRECTIVE to DIRECTIVE,
            T.OPERATOR to OPERATOR,
            T.LPAREN to PARENTHESES,
            T.RPAREN to PARENTHESES,
            T.LBRACKET to BRACKETS,
            T.RBRACKET to BRACKETS,
            T.LBRACE to BRACES,
            T.RBRACE to BRACES,
            T.SEMICOLON to SEMICOLON,
            T.COMMA to COMMA,
            T.DOT to DOT,
            TokenType.BAD_CHARACTER to BAD_CHARACTER,
        )
    }
}
