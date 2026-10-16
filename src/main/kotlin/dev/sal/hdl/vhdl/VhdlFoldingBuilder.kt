package dev.sal.hdl.vhdl

import com.intellij.psi.tree.TokenSet
import dev.sal.hdl.folding.HdlFoldingBuilder
import dev.sal.hdl.vhdl.psi.VhdlElementTypes

/** Folds design units, processes, generate and other keyword blocks, generic and port clauses, and block comments. */
class VhdlFoldingBuilder : HdlFoldingBuilder(
    TokenSet.create(VhdlElementTypes.GENERIC_CLAUSE, VhdlElementTypes.PORT_CLAUSE),
    VhdlTokenTypes.BLOCK_COMMENT,
)
