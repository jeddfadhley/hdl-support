package dev.sal.hdl.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.util.ProcessingContext
import dev.sal.hdl.completion.HdlCompletion.KEYWORD_PRIORITY
import dev.sal.hdl.completion.HdlCompletion.CONNECTION_PRIORITY
import dev.sal.hdl.completion.HdlCompletion.nameLookup
import dev.sal.hdl.completion.HdlCompletion.presentationOf
import dev.sal.hdl.completion.HdlCompletion.withPriority
import dev.sal.hdl.verilog.psi.VerilogDesignUnit
import dev.sal.hdl.vhdl.VhdlLanguage
import dev.sal.hdl.vhdl.psi.VhdlComponent
import dev.sal.hdl.vhdl.psi.VhdlDeclaration
import dev.sal.hdl.vhdl.psi.VhdlDesignUnit
import dev.sal.hdl.vhdl.psi.VhdlInstance
import dev.sal.hdl.vhdl.psi.VhdlInterfaceDeclaration
import dev.sal.hdl.vhdl.psi.VhdlSubprogram
import javax.swing.Icon
import dev.sal.hdl.vhdl.VhdlTokenTypes as T

/**
 * Completion for VHDL:
 *  - after a tick (`clk'`), the predefined attributes;
 *  - after a dot (`work.`), design units and the file's names, but no keywords;
 *  - anywhere else, the names declared in the file, design units from the whole project, keywords
 *    and the standard types. Inside an instance, the instantiated entity's ports and generics come first.
 * Matching ignores case. Keywords and types are inserted in upper case if that's how the prefix was typed.
 * Nothing is offered inside comments, strings or literals.
 */
class VhdlCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement().withLanguage(VhdlLanguage), Provider)
    }

    private object Provider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            resultSet: CompletionResultSet,
        ) {
            val position = parameters.position
            val prefix = HdlCompletion.typedPrefix(parameters)
            when (position.elementType) {
                // The token includes the tick, which stays in the text.
                T.ATTRIBUTE -> {
                    val name = prefix.removePrefix("'")
                    val result = resultSet.withPrefixMatcher(name)
                    ATTRIBUTES.forEach { result.addElement(wordLookup(it, name, AllIcons.Nodes.Property)) }
                }
                T.IDENTIFIER -> {
                    val result = resultSet.withPrefixMatcher(prefix)
                    val names = declaredNames(parameters.originalFile) + HdlCompletion.unitLookups(parameters, prefix)
                    if (PsiTreeUtil.prevCodeLeaf(position)?.elementType == T.DOT) {
                        HdlCompletion.addUnique(result, names, ignoreCase = true)
                        return
                    }
                    val instance = PsiTreeUtil.getParentOfType(position, VhdlInstance::class.java)
                    val connections = instance?.let { connectionLookups(it) }.orEmpty()
                    val words = HdlKeywords.VHDL.asSequence().map { wordLookup(it, prefix, null, bold = true) } +
                        HdlKeywords.VHDL_STD_TYPES.asSequence().map { wordLookup(it, prefix, AllIcons.Nodes.Type) }
                    HdlCompletion.addUnique(result, connections + names + words, ignoreCase = true)
                }
            }
        }
    }

    private companion object {
        // ---- Predefined attributes ----
        // IEEE 1076-2008 16.2.
        val ATTRIBUTES = listOf(
            // Types and subtypes (16.2.2)
            "base", "left", "right", "high", "low", "ascending", "image", "value", "pos", "val", "succ",
            "pred", "leftof", "rightof", "subtype", "element",
            // Arrays (16.2.3)
            "range", "reverse_range", "length",
            // Signals (16.2.4)
            "delayed", "stable", "quiet", "transaction", "event", "active", "last_event", "last_active",
            "last_value", "driving", "driving_value",
            // Named entities (16.2.5)
            "simple_name", "instance_name", "path_name",
        )

        /** Upper case if every letter typed so far is upper case (`ENT` → `ENTITY`), otherwise as listed. */
        fun matchCase(word: String, prefix: String): String =
            if (prefix.any { it.isLetter() } && prefix.none { it.isLowerCase() }) word.uppercase() else word

        fun wordLookup(word: String, prefix: String, icon: Icon?, bold: Boolean = false): LookupElement {
            var builder = LookupElementBuilder.create(matchCase(word, prefix)).withIcon(icon).withCaseSensitivity(false)
            if (bold) builder = builder.bold()
            return withPriority(builder, KEYWORD_PRIORITY)
        }

        /** Names declared anywhere in [file]: generics, ports, signals, constants, types, components and subprograms. */
        fun declaredNames(file: PsiFile): Sequence<LookupElement> =
            PsiTreeUtil.findChildrenOfAnyType(
                file,
                VhdlInterfaceDeclaration::class.java,
                VhdlDeclaration::class.java,
                VhdlComponent::class.java,
                VhdlSubprogram::class.java,
            ).asSequence().flatMap { element ->
                when (element) {
                    is VhdlInterfaceDeclaration -> element.names.asSequence().map {
                        nameLookup(it.text, element.typeText, element.icon, ignoreCase = true)
                    }
                    is VhdlDeclaration -> element.names.asSequence().map {
                        nameLookup(it.text, element.typeText, element.icon, ignoreCase = true)
                    }
                    is VhdlComponent, is VhdlSubprogram -> {
                        val (typeText, icon) = presentationOf(element)
                        listOfNotNull(element.name?.let { nameLookup(it, typeText, icon, ignoreCase = true) })
                            .asSequence()
                    }
                    else -> emptySequence()
                }
            }

        /** The ports and generics of the entity (or Verilog module) that [instance] instantiates. */
        fun connectionLookups(instance: VhdlInstance): Sequence<LookupElement> {
            val reference = instance.unitReference?.reference as? PsiPolyVariantReference ?: return emptySequence()
            return reference.multiResolve(false).asSequence().mapNotNull { it.element }.flatMap { unit ->
                when (unit) {
                    is VhdlDesignUnit -> interfaceLookups(unit.ports + unit.generics)
                    is VhdlComponent -> interfaceLookups(unit.ports + unit.generics)
                    is VerilogDesignUnit -> (unit.ports + unit.parameters).asSequence().mapNotNull { port ->
                        val (typeText, icon) = presentationOf(port)
                        port.name?.let { nameLookup(it, typeText, icon, ignoreCase = true, CONNECTION_PRIORITY) }
                    }
                    else -> emptySequence()
                }
            }
        }

        private fun interfaceLookups(declarations: List<VhdlInterfaceDeclaration>): Sequence<LookupElement> =
            declarations.asSequence().flatMap { declaration ->
                declaration.names.asSequence().map {
                    nameLookup(it.text, declaration.typeText, declaration.icon, ignoreCase = true, CONNECTION_PRIORITY)
                }
            }
    }
}
