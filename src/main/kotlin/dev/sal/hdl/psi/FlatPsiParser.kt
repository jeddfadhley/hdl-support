package dev.sal.hdl.psi

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.psi.tree.IElementType

/**
 * A "parser" that puts every token directly under the file node.
 *
 * There is no grammar yet. The platform still needs a PSI tree before it can run
 * commenters and annotators on a file, and a flat list of tokens is enough for those.
 */
class FlatPsiParser : PsiParser {
    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        val marker = builder.mark()
        while (!builder.eof()) {
            builder.advanceLexer()
        }
        marker.done(root)
        return builder.treeBuilt
    }
}
