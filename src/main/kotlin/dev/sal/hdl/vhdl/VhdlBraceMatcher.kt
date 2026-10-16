package dev.sal.hdl.vhdl

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType

/** Matches `()` and `[]`. Keyword pairs such as `process`/`end process` are handled by HdlCodeBlockSupportHandler. */
class VhdlBraceMatcher : PairedBraceMatcher {
    override fun getPairs(): Array<BracePair> = PAIRS

    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean = true

    override fun getCodeConstructStart(file: PsiFile, openingBraceOffset: Int): Int = openingBraceOffset

    private companion object {
        val PAIRS = arrayOf(
            BracePair(VhdlTokenTypes.LPAREN, VhdlTokenTypes.RPAREN, false),
            BracePair(VhdlTokenTypes.LBRACKET, VhdlTokenTypes.RBRACKET, false),
        )
    }
}
