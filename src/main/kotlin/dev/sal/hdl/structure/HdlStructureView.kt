package dev.sal.hdl.structure

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement
import com.intellij.ide.util.treeView.smartTree.Sorter
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.editor.Editor
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.util.PsiNavigateUtil

/**
 * What the structure view shows for one language. The tree's values are PSI elements: design units,
 * processes and so on, plus the name tokens of declarations, which can declare several names at once.
 */
interface HdlStructureSpec {
    /** The elements shown directly under [element] (a file, design unit, generate block, ...). */
    fun children(element: PsiElement): List<PsiElement>

    /** True if [element] appears in the tree. The view selects it when the caret is inside it. */
    fun isShown(element: PsiElement): Boolean

    /** The text, grey location string and icon for [element]. */
    fun presentation(element: PsiElement): ItemPresentation? = (element as? NavigationItem)?.presentation
}

/** Registered per language as `lang.psiStructureViewFactory`. */
abstract class HdlStructureViewFactory(private val spec: HdlStructureSpec) : PsiStructureViewFactory {
    override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder =
        object : TreeBasedStructureViewBuilder() {
            override fun createStructureViewModel(editor: Editor?): StructureViewModel =
                HdlStructureViewModel(psiFile, editor, spec)

            // Show only the root's children, not a single file node.
            override fun isRootNodeShown(): Boolean = false
        }
}

class HdlStructureViewModel(file: PsiFile, editor: Editor?, private val spec: HdlStructureSpec) :
    StructureViewModelBase(file, editor, HdlStructureViewElement(file, spec)),
    StructureViewModel.ElementInfoProvider {

    init {
        withSorters(Sorter.ALPHA_SORTER)
    }

    override fun isSuitable(element: PsiElement?): Boolean = element != null && spec.isShown(element)

    override fun isAlwaysShowsPlus(element: StructureViewTreeElement): Boolean = false

    override fun isAlwaysLeaf(element: StructureViewTreeElement): Boolean = false
}

/**
 * One node of the tree. It implements the tree interfaces directly rather than extending
 * PsiTreeElementBase, which plugins can't use in IDE versions from 2026.2.
 */
class HdlStructureViewElement(element: PsiElement, private val spec: HdlStructureSpec) :
    StructureViewTreeElement, SortableTreeElement {

    // A smart pointer survives reparsing, and returns null once the element is gone.
    private val pointer = SmartPointerManager.createPointer(element)

    private val element: PsiElement? get() = pointer.element

    override fun getValue(): Any? = element

    override fun getPresentation(): ItemPresentation = element?.let(spec::presentation) ?: PresentationData()

    override fun getChildren(): Array<TreeElement> =
        element?.let { spec.children(it).map { child -> HdlStructureViewElement(child, spec) } }
            .orEmpty()
            .toTypedArray()

    override fun navigate(requestFocus: Boolean) {
        element?.let { PsiNavigateUtil.navigate(it, requestFocus) }
    }

    override fun canNavigate(): Boolean = (element as? Navigatable)?.canNavigate() == true

    override fun canNavigateToSource(): Boolean = (element as? Navigatable)?.canNavigateToSource() == true

    override fun getAlphaSortKey(): String = presentation.presentableText.orEmpty()

    // The tree matches nodes across updates by equality, so compare the elements they show.
    override fun equals(other: Any?): Boolean = other is HdlStructureViewElement && other.element == element

    override fun hashCode(): Int = element?.hashCode() ?: 0
}
