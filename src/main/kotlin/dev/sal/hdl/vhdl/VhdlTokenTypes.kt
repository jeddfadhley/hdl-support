package dev.sal.hdl.vhdl

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

/** A token produced by the VHDL lexer. */
class VhdlTokenType(debugName: String) : IElementType(debugName, VhdlLanguage)

/**
 * Every token the VHDL lexer can return, apart from the platform's
 * `TokenType.WHITE_SPACE` and `TokenType.BAD_CHARACTER`.
 *
 * Fields are `@JvmField` so the generated Java lexer can import them statically.
 */
object VhdlTokenTypes {
    @JvmField val KEYWORD = VhdlTokenType("KEYWORD")
    @JvmField val IDENTIFIER = VhdlTokenType("IDENTIFIER")
    /** `\extended identifier\` */
    @JvmField val EXTENDED_IDENTIFIER = VhdlTokenType("EXTENDED_IDENTIFIER")
    /** Common predefined and IEEE types: `std_logic`, `unsigned`, `integer`, ... */
    @JvmField val STD_TYPE = VhdlTokenType("STD_TYPE")
    /** An attribute name including its tick: `'event`, `'length`. */
    @JvmField val ATTRIBUTE = VhdlTokenType("ATTRIBUTE")
    @JvmField val LINE_COMMENT = VhdlTokenType("LINE_COMMENT")
    @JvmField val BLOCK_COMMENT = VhdlTokenType("BLOCK_COMMENT")
    @JvmField val STRING = VhdlTokenType("STRING")
    /** `'0'`, `'Z'` */
    @JvmField val CHARACTER = VhdlTokenType("CHARACTER")
    /** `x"FF"`, `b"1010"`, `12UX"F0"` */
    @JvmField val BIT_STRING = VhdlTokenType("BIT_STRING")
    /** Decimal, real and based (`16#FF#`) literals. */
    @JvmField val NUMBER = VhdlTokenType("NUMBER")
    @JvmField val OPERATOR = VhdlTokenType("OPERATOR")
    @JvmField val LPAREN = VhdlTokenType("LPAREN")
    @JvmField val RPAREN = VhdlTokenType("RPAREN")
    @JvmField val LBRACKET = VhdlTokenType("LBRACKET")
    @JvmField val RBRACKET = VhdlTokenType("RBRACKET")
    @JvmField val SEMICOLON = VhdlTokenType("SEMICOLON")
    @JvmField val COMMA = VhdlTokenType("COMMA")
    @JvmField val DOT = VhdlTokenType("DOT")

    @JvmField val FILE = IFileElementType(VhdlLanguage)

    @JvmField val COMMENTS = TokenSet.create(LINE_COMMENT, BLOCK_COMMENT)
    @JvmField val STRINGS = TokenSet.create(STRING, BIT_STRING)
}
