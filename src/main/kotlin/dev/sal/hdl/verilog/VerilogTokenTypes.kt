package dev.sal.hdl.verilog

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

/** A token produced by the Verilog lexer. */
class VerilogTokenType(debugName: String) : IElementType(debugName, VerilogLanguage)

/**
 * Every token the Verilog lexer can return, apart from the platform's
 * `TokenType.WHITE_SPACE` and `TokenType.BAD_CHARACTER`.
 *
 * Fields are `@JvmField` so the generated Java lexer can import them statically.
 */
object VerilogTokenTypes {
    @JvmField val KEYWORD = VerilogTokenType("KEYWORD")
    @JvmField val IDENTIFIER = VerilogTokenType("IDENTIFIER")
    /** `\foo+bar ` style identifiers. */
    @JvmField val ESCAPED_IDENTIFIER = VerilogTokenType("ESCAPED_IDENTIFIER")
    @JvmField val LINE_COMMENT = VerilogTokenType("LINE_COMMENT")
    @JvmField val BLOCK_COMMENT = VerilogTokenType("BLOCK_COMMENT")
    @JvmField val STRING = VerilogTokenType("STRING")
    /** Plain, real, sized/based (`8'hFF`) and unbased (`'0`) numbers, and time literals (`10ns`). */
    @JvmField val NUMBER = VerilogTokenType("NUMBER")
    /** `$display`, `$clog2`, ... */
    @JvmField val SYSTEM_TASK = VerilogTokenType("SYSTEM_TASK")
    /** `` `define ``, `` `include ``, and macro uses such as `` `WIDTH ``. */
    @JvmField val DIRECTIVE = VerilogTokenType("DIRECTIVE")
    @JvmField val OPERATOR = VerilogTokenType("OPERATOR")
    @JvmField val LPAREN = VerilogTokenType("LPAREN")
    @JvmField val RPAREN = VerilogTokenType("RPAREN")
    @JvmField val LBRACKET = VerilogTokenType("LBRACKET")
    @JvmField val RBRACKET = VerilogTokenType("RBRACKET")
    @JvmField val LBRACE = VerilogTokenType("LBRACE")
    @JvmField val RBRACE = VerilogTokenType("RBRACE")
    @JvmField val SEMICOLON = VerilogTokenType("SEMICOLON")
    @JvmField val COMMA = VerilogTokenType("COMMA")
    @JvmField val DOT = VerilogTokenType("DOT")

    @JvmField val FILE = IFileElementType(VerilogLanguage)

    @JvmField val COMMENTS = TokenSet.create(LINE_COMMENT, BLOCK_COMMENT)
    @JvmField val STRINGS = TokenSet.create(STRING)
}
