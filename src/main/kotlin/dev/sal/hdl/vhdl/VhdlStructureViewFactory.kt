package dev.sal.hdl.vhdl

import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.elementType
import dev.sal.hdl.psi.HdlPsiUtil
import dev.sal.hdl.structure.HdlStructureSpec
import dev.sal.hdl.structure.HdlStructureViewFactory
import dev.sal.hdl.vhdl.psi.VhdlComponent
import dev.sal.hdl.vhdl.psi.VhdlDeclaration
import dev.sal.hdl.vhdl.psi.VhdlDesignUnit
import dev.sal.hdl.vhdl.psi.VhdlElementTypes
import dev.sal.hdl.vhdl.psi.VhdlInstance
import dev.sal.hdl.vhdl.psi.VhdlInterfaceDeclaration
import dev.sal.hdl.vhdl.psi.VhdlStatement
import dev.sal.hdl.vhdl.psi.VhdlSubprogram

/** The structure view for VHDL files. */
class VhdlStructureViewFactory : HdlStructureViewFactory(VhdlStructureSpec)

/**
 * Shows design units with their generics, ports, declarations, components, subprograms, processes,
 * instances, and generate and block statements. Processes and subprograms are leaves.
 */
object VhdlStructureSpec : HdlStructureSpec {
    override fun children(element: PsiElement): List<PsiElement> {
        val isContainer = element is PsiFile || element is VhdlDesignUnit || element is VhdlComponent ||
            (element is VhdlStatement && element.elementType in CONCURRENT_BLOCKS)
        if (!isContainer) return emptyList()
        val result = ArrayList<PsiElement>()
        for (child in generateSequence(element.firstChild) { it.nextSibling }) {
            when {
                child is VhdlDesignUnit || child is VhdlComponent || child is VhdlSubprogram || child is VhdlInstance ->
                    result.add(child)
                child is VhdlStatement && child.elementType in SHOWN_STATEMENTS -> result.add(child)
                child is VhdlDeclaration -> result.addAll(child.names)
                child.elementType == VhdlElementTypes.GENERIC_CLAUSE || child.elementType == VhdlElementTypes.PORT_CLAUSE ->
                    for (entry in child.children) {
                        if (entry is VhdlInterfaceDeclaration) result.addAll(entry.names)
                    }
            }
        }
        return result
    }

    override fun isShown(element: PsiElement): Boolean = when (element) {
        is VhdlDesignUnit, is VhdlComponent, is VhdlSubprogram, is VhdlInstance -> true
        is VhdlStatement -> element.elementType in SHOWN_STATEMENTS
        else -> declarationOf(element) != null
    }

    /** The declaration or interface declaration that declares [element], if [element] is a declared name. */
    private fun declarationOf(element: PsiElement): PsiElement? = when (val parent = element.parent) {
        is VhdlDeclaration -> parent.takeIf { element in it.names }
        is VhdlInterfaceDeclaration -> parent.takeIf { element in it.names }
        else -> null
    }

    override fun presentation(element: PsiElement): ItemPresentation? =
        when (val declaration = declarationOf(element)) {
            is VhdlDeclaration -> HdlPsiUtil.presentation(element.text, declaration.typeText, declaration.icon)
            is VhdlInterfaceDeclaration -> HdlPsiUtil.presentation(element.text, declaration.typeText, declaration.icon)
            else -> super.presentation(element)
        }

    /** Statements that contain concurrent statements and declarations of their own. */
    private val CONCURRENT_BLOCKS = setOf(VhdlElementTypes.GENERATE, VhdlElementTypes.BLOCK)

    private val SHOWN_STATEMENTS = CONCURRENT_BLOCKS + VhdlElementTypes.PROCESS
}
