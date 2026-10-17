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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.util.ProcessingContext
import dev.sal.hdl.completion.HdlCompletion.CONNECTION_PRIORITY
import dev.sal.hdl.completion.HdlCompletion.KEYWORD_PRIORITY
import dev.sal.hdl.completion.HdlCompletion.nameLookup
import dev.sal.hdl.completion.HdlCompletion.presentationOf
import dev.sal.hdl.completion.HdlCompletion.withPriority
import dev.sal.hdl.verilog.VerilogLanguage
import dev.sal.hdl.verilog.psi.VerilogDeclaration
import dev.sal.hdl.verilog.psi.VerilogDesignUnit
import dev.sal.hdl.verilog.psi.VerilogInstance
import dev.sal.hdl.verilog.psi.VerilogNamedElement
import dev.sal.hdl.verilog.psi.VerilogPort
import dev.sal.hdl.verilog.psi.VerilogSubprogram
import dev.sal.hdl.vhdl.psi.VhdlDesignUnit
import dev.sal.hdl.verilog.VerilogTokenTypes as T

/**
 * Completion for Verilog and SystemVerilog:
 *  - `$...` completes system tasks and functions;
 *  - `` `... `` completes compiler directives and the macros defined in the file;
 *  - `.` inside an instance completes the ports and parameters of the instantiated module;
 *  - anywhere else, the names declared in the file, design units from the whole project, and keywords.
 * Nothing is offered inside comments, strings or numbers.
 */
class VerilogCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement().withLanguage(VerilogLanguage), Provider)
    }

    private object Provider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            resultSet: CompletionResultSet,
        ) {
            val position = parameters.position
            val prefix = HdlCompletion.typedPrefix(parameters)
            val result = resultSet.withPrefixMatcher(prefix)
            when (position.elementType) {
                T.SYSTEM_TASK -> SYSTEM_TASKS.forEach { result.addElement(systemTaskLookup(it)) }
                T.DIRECTIVE -> {
                    DIRECTIVES.forEach { result.addElement(LookupElementBuilder.create(it).bold()) }
                    macroNames(parameters.originalFile).forEach {
                        result.addElement(nameLookup("`$it", "macro", AllIcons.Nodes.Constant, ignoreCase = false))
                    }
                }
                T.IDENTIFIER -> {
                    if (PsiTreeUtil.prevCodeLeaf(position)?.elementType == T.DOT) {
                        val instance = PsiTreeUtil.getParentOfType(position, VerilogInstance::class.java)
                        if (instance != null) {
                            // `.clk(`: only the instantiated module's ports and parameters make sense here.
                            HdlCompletion.addUnique(result, connectionLookups(instance), ignoreCase = false)
                            return
                        }
                        // A hierarchical name or struct member. Offer the file's names, but no keywords.
                        HdlCompletion.addUnique(result, declaredNames(parameters.originalFile), ignoreCase = false)
                        return
                    }
                    val lookups = declaredNames(parameters.originalFile) +
                        HdlCompletion.unitLookups(parameters, prefix) +
                        HdlKeywords.VERILOG.asSequence().map { keywordLookup(it) }
                    HdlCompletion.addUnique(result, lookups, ignoreCase = false)
                }
            }
        }
    }

    private companion object {
        // ---- System tasks and functions ----
        // A selection of the IEEE 1800-2017 clause 20 and 21 tasks and functions, plus the VCD dump tasks.
        val SYSTEM_TASKS = listOf(
            // Display and simulation control (20.2, 21.2)
            "\$display", "\$displayb", "\$displayh", "\$displayo", "\$write", "\$strobe", "\$monitor",
            "\$monitoron", "\$monitoroff", "\$finish", "\$stop", "\$exit",
            // Time (20.3, 20.4)
            "\$time", "\$stime", "\$realtime", "\$timeformat", "\$printtimescale",
            // Conversion and data queries (20.5 to 20.7)
            "\$signed", "\$unsigned", "\$cast", "\$bits", "\$typename", "\$isunbounded", "\$rtoi", "\$itor",
            "\$realtobits", "\$bitstoreal", "\$shortrealtobits", "\$bitstoshortreal",
            "\$left", "\$right", "\$low", "\$high", "\$increment", "\$size", "\$dimensions",
            "\$unpacked_dimensions",
            // Maths (20.8)
            "\$clog2", "\$ln", "\$log10", "\$exp", "\$sqrt", "\$pow", "\$floor", "\$ceil", "\$sin", "\$cos",
            "\$tan", "\$asin", "\$acos", "\$atan", "\$atan2", "\$hypot", "\$sinh", "\$cosh", "\$tanh",
            "\$asinh", "\$acosh", "\$atanh",
            // Bit vectors (20.9)
            "\$countbits", "\$countones", "\$onehot", "\$onehot0", "\$isunknown",
            // Severity (20.10)
            "\$fatal", "\$error", "\$warning", "\$info",
            // Assertions (20.12, 16.9)
            "\$asserton", "\$assertoff", "\$assertkill", "\$sampled", "\$rose", "\$fell", "\$stable",
            "\$changed", "\$past",
            // Random numbers (18.13, 20.15)
            "\$random", "\$urandom", "\$urandom_range", "\$dist_uniform", "\$dist_normal",
            "\$dist_exponential", "\$dist_poisson",
            // Files and strings (21.3)
            "\$fopen", "\$fclose", "\$fdisplay", "\$fwrite", "\$fstrobe", "\$fmonitor", "\$fgetc", "\$fgets",
            "\$fscanf", "\$sscanf", "\$fread", "\$feof", "\$ferror", "\$fflush", "\$sformat", "\$sformatf",
            "\$swrite",
            // Memories (21.4)
            "\$readmemb", "\$readmemh", "\$writememb", "\$writememh",
            // Command line (21.6)
            "\$test\$plusargs", "\$value\$plusargs",
            // VCD (21.7)
            "\$dumpfile", "\$dumpvars", "\$dumpon", "\$dumpoff", "\$dumpall", "\$dumplimit", "\$dumpflush",
            "\$dumpports",
        )

        // ---- Compiler directives ----
        // IEEE 1800-2017 clause 22.
        val DIRECTIVES = listOf(
            "`__FILE__", "`__LINE__", "`begin_keywords", "`celldefine", "`default_nettype", "`define", "`else",
            "`elsif", "`end_keywords", "`endcelldefine", "`endif", "`ifdef", "`ifndef", "`include", "`line",
            "`nounconnected_drive", "`pragma", "`resetall", "`timescale", "`unconnected_drive", "`undef",
            "`undefineall",
        )

        fun keywordLookup(keyword: String): LookupElement =
            withPriority(LookupElementBuilder.create(keyword).bold(), KEYWORD_PRIORITY)

        fun systemTaskLookup(name: String): LookupElement =
            LookupElementBuilder.create(name).withIcon(AllIcons.Nodes.Function)

        /** Names declared anywhere in [file]: ports, parameters, signals, types, functions and tasks. */
        fun declaredNames(file: PsiFile): Sequence<LookupElement> =
            PsiTreeUtil.findChildrenOfAnyType(
                file, VerilogPort::class.java, VerilogDeclaration::class.java, VerilogSubprogram::class.java,
            ).asSequence().flatMap { element ->
                when (element) {
                    is VerilogDeclaration -> element.names.asSequence().map {
                        nameLookup(it.text, element.typeText, element.icon, ignoreCase = false)
                    }
                    is VerilogNamedElement -> {
                        val (typeText, icon) = presentationOf(element)
                        listOfNotNull(element.name?.let { nameLookup(it, typeText, icon, ignoreCase = false) })
                            .asSequence()
                    }
                    else -> emptySequence()
                }
            }

        /** The ports and parameters of the module (or VHDL entity) that [instance] instantiates. */
        fun connectionLookups(instance: VerilogInstance): Sequence<LookupElement> {
            val reference = instance.unitReference?.reference as? PsiPolyVariantReference ?: return emptySequence()
            return reference.multiResolve(false).asSequence().mapNotNull { it.element }.flatMap { unit ->
                when (unit) {
                    is VerilogDesignUnit -> (unit.ports + unit.parameters).asSequence().mapNotNull { port ->
                        val (typeText, icon) = presentationOf(port)
                        port.name?.let { nameLookup(it, typeText, icon, ignoreCase = false, CONNECTION_PRIORITY) }
                    }
                    is VhdlDesignUnit -> (unit.ports + unit.generics).asSequence().flatMap { declaration ->
                        declaration.names.asSequence().map {
                            nameLookup(it.text, declaration.typeText, declaration.icon, true, CONNECTION_PRIORITY)
                        }
                    }
                    else -> emptySequence<LookupElement>()
                }
            }
        }

        /** Macros defined in [file] with `` `define NAME ``. */
        fun macroNames(file: PsiFile): List<String> =
            SyntaxTraverser.psiTraverser(file)
                .filter { it.elementType == T.DIRECTIVE && it.text == "`define" }
                .mapNotNull { define: PsiElement ->
                    PsiTreeUtil.nextCodeLeaf(define)?.takeIf { it.elementType == T.IDENTIFIER }?.text
                }
                .toList()
                .distinct()
    }
}
