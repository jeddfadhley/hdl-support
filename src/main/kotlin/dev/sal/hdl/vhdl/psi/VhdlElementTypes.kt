package dev.sal.hdl.vhdl.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import dev.sal.hdl.vhdl.VhdlLanguage

/** A composite node built by [VhdlParser]. */
class VhdlElementType(debugName: String) : IElementType(debugName, VhdlLanguage)

/** The composite nodes the VHDL parser builds. Everything else is a token directly under its parent. */
object VhdlElementTypes {
    /** `entity`, `architecture`, `package`, `package body`, `configuration` or `context`. */
    @JvmField val DESIGN_UNIT = VhdlElementType("DESIGN_UNIT")
    /** `generic ( ... );` in an entity, component or package. */
    @JvmField val GENERIC_CLAUSE = VhdlElementType("GENERIC_CLAUSE")
    /** `port ( ... );` in an entity or component. */
    @JvmField val PORT_CLAUSE = VhdlElementType("PORT_CLAUSE")
    /** One `;`-separated entry in a generic or port clause: `clk, rst : in std_logic` */
    @JvmField val INTERFACE_DECLARATION = VhdlElementType("INTERFACE_DECLARATION")
    /** `signal`, `constant`, `variable`, `type`, `subtype`, `alias`, `file` and `attribute` declarations. */
    @JvmField val DECLARATION = VhdlElementType("DECLARATION")
    @JvmField val COMPONENT = VhdlElementType("COMPONENT")
    /** A function or procedure, with or without a body. */
    @JvmField val SUBPROGRAM = VhdlElementType("SUBPROGRAM")
    @JvmField val PROCESS = VhdlElementType("PROCESS")
    /** `label : block ... end block;` */
    @JvmField val BLOCK = VhdlElementType("BLOCK")
    /** `for ... generate`, `if ... generate` or `case ... generate`, up to `end generate`. */
    @JvmField val GENERATE = VhdlElementType("GENERATE")
    /** `u1 : entity work.counter port map (...);` */
    @JvmField val INSTANCE = VhdlElementType("INSTANCE")
    @JvmField val IF = VhdlElementType("IF")
    @JvmField val CASE = VhdlElementType("CASE")
    /** `loop`, `while ... loop` or `for ... loop` */
    @JvmField val LOOP = VhdlElementType("LOOP")
    /** Any other statement, up to its `;`. */
    @JvmField val STATEMENT = VhdlElementType("STATEMENT")
    /** A design unit name used elsewhere: the entity of an architecture or instance, or a package in a use clause. */
    @JvmField val UNIT_REFERENCE = VhdlElementType("UNIT_REFERENCE")

    /** Elements that start with a keyword and finish with `end`. */
    @JvmField val KEYWORD_BLOCKS = TokenSet.create(
        DESIGN_UNIT, COMPONENT, SUBPROGRAM, PROCESS, BLOCK, GENERATE, IF, CASE, LOOP, DECLARATION,
    )

    fun createElement(node: ASTNode): PsiElement = when (node.elementType) {
        DESIGN_UNIT -> VhdlDesignUnit(node)
        INTERFACE_DECLARATION -> VhdlInterfaceDeclaration(node)
        DECLARATION -> VhdlDeclaration(node)
        COMPONENT -> VhdlComponent(node)
        SUBPROGRAM -> VhdlSubprogram(node)
        PROCESS, BLOCK, GENERATE, IF, CASE, LOOP -> VhdlStatement(node)
        INSTANCE -> VhdlInstance(node)
        UNIT_REFERENCE -> VhdlUnitReference(node)
        else -> ASTWrapperPsiElement(node)
    }
}
