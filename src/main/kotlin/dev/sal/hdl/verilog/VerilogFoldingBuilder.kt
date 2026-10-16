package dev.sal.hdl.verilog

import com.intellij.psi.tree.TokenSet
import dev.sal.hdl.folding.HdlFoldingBuilder
import dev.sal.hdl.verilog.psi.VerilogElementTypes

/** Folds modules, `begin`/`end` and other keyword blocks, port and parameter lists, and block comments. */
class VerilogFoldingBuilder : HdlFoldingBuilder(
    TokenSet.create(VerilogElementTypes.PORT_LIST, VerilogElementTypes.PARAMETER_LIST),
    VerilogTokenTypes.BLOCK_COMMENT,
)
