package dev.sal.hdl.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.FileBasedIndex
import dev.sal.hdl.navigation.HdlUnitIndex
import dev.sal.hdl.verilog.psi.VerilogDesignUnit
import dev.sal.hdl.vhdl.psi.VhdlDesignUnit
import javax.swing.Icon

/**
 * Keyword lists for completion. The build extracts them from the keyword blocks in `Verilog.flex`
 * and `Vhdl.flex` (see `generateKeywordLists` in build.gradle.kts), so the lexer stays the one
 * place where each language's keywords are listed.
 */
object HdlKeywords {
    val VERILOG: List<String> by lazy { load("verilog-keywords.txt") }
    val VHDL: List<String> by lazy { load("vhdl-keywords.txt") }
    val VHDL_STD_TYPES: List<String> by lazy { load("vhdl-std-types.txt") }

    private fun load(name: String): List<String> =
        HdlKeywords::class.java.getResourceAsStream(name)
            ?.bufferedReader()
            ?.use { reader -> reader.readLines().map { it.trim() }.filter { it.isNotEmpty() } }
            .orEmpty()
}

/** Helpers shared by the Verilog and VHDL completion contributors. */
object HdlCompletion {
    // Ranking: names declared in the file, then design units, then keywords.
    const val NAME_PRIORITY = 30.0
    const val UNIT_PRIORITY = 20.0
    const val KEYWORD_PRIORITY = 10.0

    /** For the ports of the instantiated module, which rank above everything else. */
    const val CONNECTION_PRIORITY = 40.0

    /**
     * The text typed so far: from the start of the token at the caret up to the caret. The platform's
     * default prefix stops at characters such as `` ` `` and `'`, so the contributors set their own.
     */
    fun typedPrefix(parameters: CompletionParameters): String {
        val position = parameters.position
        val length = (parameters.offset - position.textRange.startOffset).coerceIn(0, position.textLength)
        return position.text.substring(0, length)
    }

    fun withPriority(element: LookupElement, priority: Double): LookupElement =
        PrioritizedLookupElement.withPriority(element, priority)

    /** A lookup item for a declared name, showing its type or kind on the right. */
    fun nameLookup(
        name: String,
        typeText: String?,
        icon: Icon?,
        ignoreCase: Boolean,
        priority: Double = NAME_PRIORITY,
    ): LookupElement {
        val builder = LookupElementBuilder.create(name)
            .withIcon(icon)
            .withTypeText(typeText?.takeIf { it.isNotBlank() }, true)
            .withCaseSensitivity(!ignoreCase)
        return withPriority(builder, priority)
    }

    /** The type text and icon a PSI element shows in the structure view, reused for its lookup item. */
    fun presentationOf(element: PsiElement): Pair<String?, Icon?> {
        val presentation = (element as? NavigationItem)?.presentation
        return presentation?.locationString to element.getIcon(0)
    }

    /** Adds each lookup once, keeping the first item for each name. */
    fun addUnique(result: CompletionResultSet, lookups: Sequence<LookupElement>, ignoreCase: Boolean) {
        val seen = HashSet<String>()
        for (lookup in lookups) {
            val key = if (ignoreCase) lookup.lookupString.lowercase() else lookup.lookupString
            if (seen.add(key)) result.addElement(lookup)
        }
    }

    /**
     * Lookups for the Verilog and VHDL design units in the project whose names could match [prefix].
     * The index only stores lower-case names, so the real names come from the parsed files.
     * Units in the file being edited come from its PSI, which is always up to date.
     */
    fun unitLookups(parameters: CompletionParameters, prefix: String): Sequence<LookupElement> {
        val project = parameters.position.project
        val original = parameters.originalFile
        val matcher = CamelHumpMatcher(prefix, false)
        val index = FileBasedIndex.getInstance()
        val scope = GlobalSearchScope.allScope(project)

        val files = LinkedHashSet<VirtualFile>()
        for (key in index.getAllKeys(HdlUnitIndex.NAME, project)) {
            if (matcher.prefixMatches(key)) files.addAll(index.getContainingFiles(HdlUnitIndex.NAME, key, scope))
        }
        original.virtualFile?.let { files.remove(it) }

        val psiManager = PsiManager.getInstance(project)
        val psiFiles = sequenceOf(original) + files.asSequence().mapNotNull { psiManager.findFile(it) }
        return psiFiles.flatMap { file ->
            PsiTreeUtil.findChildrenOfAnyType(file, VerilogDesignUnit::class.java, VhdlDesignUnit::class.java)
                .asSequence()
                .mapNotNull { unit -> unitLookup(unit, file.name) }
        }
    }

    private fun unitLookup(unit: PsiElement, fileName: String): LookupElement? {
        val (name, kind, icon) = when (unit) {
            is VerilogDesignUnit -> Triple(unit.name, unit.kind.keyword, unit.kind.icon)
            // An architecture or package body isn't something you refer to by name.
            is VhdlDesignUnit -> if (unit.kind == VhdlDesignUnit.Kind.ARCHITECTURE ||
                unit.kind == VhdlDesignUnit.Kind.PACKAGE_BODY
            ) {
                return null
            } else {
                Triple(unit.name, unit.kind.label, unit.kind.icon)
            }
            else -> return null
        }
        if (name == null) return null
        val builder = LookupElementBuilder.create(name)
            .withIcon(icon)
            .withTailText(" ($fileName)", true)
            .withTypeText(kind, true)
            // Verilog names are case-sensitive; VHDL names aren't.
            .withCaseSensitivity(unit is VerilogDesignUnit)
        return withPriority(builder, UNIT_PRIORITY)
    }
}
