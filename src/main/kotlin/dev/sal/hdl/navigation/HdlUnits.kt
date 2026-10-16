package dev.sal.hdl.navigation

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.FileBasedIndex
import dev.sal.hdl.verilog.psi.VerilogDesignUnit
import dev.sal.hdl.vhdl.VhdlLanguage
import dev.sal.hdl.vhdl.psi.VhdlDesignUnit

/** Finds design units (modules, entities, packages, ...) by name across the project. */
object HdlUnits {
    /**
     * Every Verilog and VHDL design unit called [name]. Verilog names match exactly; VHDL names, and
     * any name looked up from VHDL, match ignoring case.
     */
    fun find(context: PsiElement, name: String): List<PsiElement> {
        val project = context.project
        val ignoreCase = context.language == VhdlLanguage
        val files = FileBasedIndex.getInstance()
            .getContainingFiles(HdlUnitIndex.NAME, name.lowercase(), GlobalSearchScope.allScope(project))
            .toMutableSet()
        // The current file may not be indexed yet (a new or unsaved file), so always search it too.
        context.containingFile?.originalFile?.virtualFile?.let { files.add(it) }

        val psiManager = PsiManager.getInstance(project)
        return files.mapNotNull { psiManager.findFile(it) }.flatMap { file ->
            PsiTreeUtil.findChildrenOfAnyType(file, VerilogDesignUnit::class.java, VhdlDesignUnit::class.java)
                .filter { unit ->
                    val unitName = (unit as? VerilogDesignUnit)?.name ?: (unit as? VhdlDesignUnit)?.name
                    unitName != null && unitName.equals(name, ignoreCase = ignoreCase || unit is VhdlDesignUnit)
                }
        }
    }
}

/**
 * A reference from a design unit name to its declaration. Ctrl+click shows a chooser if several
 * files declare the same name.
 *
 * @param targets finds the declarations for the element's name, most suitable first
 */
class HdlUnitReference(
    element: PsiElement,
    private val targets: (PsiElement) -> List<PsiElement>,
) : PsiPolyVariantReferenceBase<PsiElement>(element, TextRange(0, element.textLength)) {

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> =
        ResolveCache.getInstance(element.project).resolveWithCaching(this, RESOLVER, false, incompleteCode)

    private companion object {
        val RESOLVER = ResolveCache.PolyVariantResolver<HdlUnitReference> { reference, _ ->
            PsiElementResolveResult.createResults(reference.targets(reference.element))
        }
    }
}
