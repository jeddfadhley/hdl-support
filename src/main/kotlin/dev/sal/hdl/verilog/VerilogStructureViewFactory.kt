package dev.sal.hdl.verilog

import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.elementType
import dev.sal.hdl.psi.HdlPsiUtil
import dev.sal.hdl.structure.HdlStructureSpec
import dev.sal.hdl.structure.HdlStructureViewFactory
import dev.sal.hdl.verilog.psi.VerilogBlock
import dev.sal.hdl.verilog.psi.VerilogDeclaration
import dev.sal.hdl.verilog.psi.VerilogDesignUnit
import dev.sal.hdl.verilog.psi.VerilogElementTypes
import dev.sal.hdl.verilog.psi.VerilogInstance
import dev.sal.hdl.verilog.psi.VerilogPort
import dev.sal.hdl.verilog.psi.VerilogProcess
import dev.sal.hdl.verilog.psi.VerilogSubprogram

/** The structure view for Verilog/SystemVerilog files. */
class VerilogStructureViewFactory : HdlStructureViewFactory(VerilogStructureSpec)

/**
 * Shows design units with their parameters, ports, declarations, instances, processes, functions,
 * tasks and named blocks. Unnamed `begin`/`end` blocks, `generate` regions and generate `case`
 * statements are flattened into their parent. Processes, functions and tasks are leaves.
 */
object VerilogStructureSpec : HdlStructureSpec {
    override fun children(element: PsiElement): List<PsiElement> {
        val isContainer = element is PsiFile || element is VerilogDesignUnit || (element is VerilogBlock && isNamedBlock(element))
        if (!isContainer) return emptyList()
        return ArrayList<PsiElement>().also { collect(element, it) }
    }

    private fun collect(container: PsiElement, result: MutableList<PsiElement>) {
        for (child in generateSequence(container.firstChild) { it.nextSibling }) {
            when (child) {
                is VerilogDesignUnit, is VerilogSubprogram, is VerilogProcess, is VerilogInstance -> result.add(child)
                is VerilogPort -> if (child.nameIdentifier != null) result.add(child)
                is VerilogDeclaration -> result.addAll(child.names)
                is VerilogBlock -> if (isNamedBlock(child)) result.add(child) else collect(child, result)
                else -> if (child.elementType == VerilogElementTypes.PORT_LIST ||
                    child.elementType == VerilogElementTypes.PARAMETER_LIST
                ) {
                    collect(child, result)
                }
            }
        }
    }

    /** A labelled `begin`/`fork` block, or a named `covergroup`, `property` and so on. */
    private fun isNamedBlock(block: VerilogBlock): Boolean = block.label != null

    override fun isShown(element: PsiElement): Boolean = when (element) {
        is VerilogDesignUnit, is VerilogSubprogram, is VerilogProcess, is VerilogInstance -> true
        is VerilogPort -> element.nameIdentifier != null
        is VerilogBlock -> isNamedBlock(element)
        else -> isDeclaredName(element)
    }

    private fun isDeclaredName(element: PsiElement): Boolean {
        val declaration = element.parent as? VerilogDeclaration ?: return false
        return element in declaration.names
    }

    override fun presentation(element: PsiElement): ItemPresentation? {
        if (isDeclaredName(element)) {
            val declaration = element.parent as VerilogDeclaration
            return HdlPsiUtil.presentation(element.text, declaration.typeText, declaration.icon)
        }
        return super.presentation(element)
    }
}
