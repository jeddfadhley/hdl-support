package dev.sal.hdl.structure

import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement
import com.intellij.ide.util.treeView.smartTree.Sorter
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import javax.swing.Icon

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

class HdlStructureViewElement(element: PsiElement, private val spec: HdlStructureSpec) :
    PsiTreeElementBase<PsiElement>(element), SortableTreeElement {

    // Not named `presentation`: PsiTreeElementBase already has getPresentation(), which returns this element.
    private fun specPresentation(): ItemPresentation? = element?.let(spec::presentation)

    override fun getChildrenBase(): Collection<StructureViewTreeElement> =
        element?.let { spec.children(it).map { child -> HdlStructureViewElement(child, spec) } }.orEmpty()

    override fun getPresentableText(): String? = specPresentation()?.presentableText

    override fun getLocationString(): String? = specPresentation()?.locationString

    override fun getIcon(open: Boolean): Icon? = specPresentation()?.getIcon(open)

    override fun getAlphaSortKey(): String = presentableText.orEmpty()
}
