package dev.sal.hdl.verilog.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import dev.sal.hdl.verilog.VerilogLanguage

/** A composite node built by [VerilogParser]. */
class VerilogElementType(debugName: String) : IElementType(debugName, VerilogLanguage)

/** The composite nodes the Verilog parser builds. Everything else is a token directly under its parent. */
object VerilogElementTypes {
    /** `module`, `interface`, `program`, `package`, `class`, `primitive`, `checker` or `config`, up to its end keyword. */
    @JvmField val DESIGN_UNIT = VerilogElementType("DESIGN_UNIT")
    /** `#( ... )` in a design unit header. */
    @JvmField val PARAMETER_LIST = VerilogElementType("PARAMETER_LIST")
    @JvmField val PARAMETER = VerilogElementType("PARAMETER")
    /** `( ... )` in a design unit header. */
    @JvmField val PORT_LIST = VerilogElementType("PORT_LIST")
    @JvmField val PORT = VerilogElementType("PORT")
    /** Nets, variables, parameters, typedefs and genvars: `wire [7:0] a, b;` */
    @JvmField val DECLARATION = VerilogElementType("DECLARATION")
    /** `counter #(.W(8)) u_counter (...);` */
    @JvmField val INSTANCE = VerilogElementType("INSTANCE")
    /** A design unit name used somewhere else: an instantiated module, or `pkg` in `pkg::*`. */
    @JvmField val UNIT_REFERENCE = VerilogElementType("UNIT_REFERENCE")
    /** `always`, `always_ff`, `always_comb`, `always_latch`, `initial` and `final`, with their statement. */
    @JvmField val PROCESS = VerilogElementType("PROCESS")
    @JvmField val SUBPROGRAM = VerilogElementType("SUBPROGRAM")
    /** `generate ... endgenerate` */
    @JvmField val GENERATE = VerilogElementType("GENERATE")
    /** `begin ... end` and `fork ... join` */
    @JvmField val BLOCK = VerilogElementType("BLOCK")
    @JvmField val CASE = VerilogElementType("CASE")
    /** Other keyword pairs: `covergroup`, `property`, `sequence`, `clocking`, `specify`, `table`, `randsequence`. */
    @JvmField val CONSTRUCT = VerilogElementType("CONSTRUCT")

    /** Elements that start with an opening keyword and finish with a matching end keyword. */
    @JvmField val KEYWORD_BLOCKS = TokenSet.create(DESIGN_UNIT, SUBPROGRAM, GENERATE, BLOCK, CASE, CONSTRUCT)

    fun createElement(node: ASTNode): PsiElement = when (node.elementType) {
        DESIGN_UNIT -> VerilogDesignUnit(node)
        PARAMETER, PORT -> VerilogPort(node)
        DECLARATION -> VerilogDeclaration(node)
        INSTANCE -> VerilogInstance(node)
        UNIT_REFERENCE -> VerilogUnitReference(node)
        PROCESS -> VerilogProcess(node)
        SUBPROGRAM -> VerilogSubprogram(node)
        GENERATE, BLOCK, CASE, CONSTRUCT -> VerilogBlock(node)
        else -> ASTWrapperPsiElement(node)
    }
}
