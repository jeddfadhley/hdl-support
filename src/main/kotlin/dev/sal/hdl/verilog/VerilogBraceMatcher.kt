package dev.sal.hdl.verilog

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType

/** Matches `()`, `[]` and `{}`. Keyword pairs such as `begin`/`end` are handled by HdlCodeBlockSupportHandler. */
class VerilogBraceMatcher : PairedBraceMatcher {
    override fun getPairs(): Array<BracePair> = PAIRS

    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean = true

    override fun getCodeConstructStart(file: PsiFile, openingBraceOffset: Int): Int = openingBraceOffset

    private companion object {
        val PAIRS = arrayOf(
            BracePair(VerilogTokenTypes.LPAREN, VerilogTokenTypes.RPAREN, false),
            BracePair(VerilogTokenTypes.LBRACKET, VerilogTokenTypes.RBRACKET, false),
            BracePair(VerilogTokenTypes.LBRACE, VerilogTokenTypes.RBRACE, false),
        )
    }
}
