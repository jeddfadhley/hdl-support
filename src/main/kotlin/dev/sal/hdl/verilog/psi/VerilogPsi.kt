package dev.sal.hdl.verilog.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.icons.AllIcons
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.util.IncorrectOperationException
import dev.sal.hdl.psi.HdlKeywordBlock
import dev.sal.hdl.psi.HdlPsiUtil
import dev.sal.hdl.psi.HdlPsiUtil.collapsedText
import dev.sal.hdl.psi.HdlPsiUtil.presentation
import javax.swing.Icon
import dev.sal.hdl.verilog.VerilogTokenTypes as T

// PSI classes for the nodes in VerilogElementTypes. They work out names and other details from
// their tokens on demand, because the parser doesn't wrap names in nodes of their own.

private val NAMES = TokenSet.create(T.IDENTIFIER, T.ESCAPED_IDENTIFIER)
private val OPENERS = TokenSet.create(T.LPAREN, T.LBRACKET, T.LBRACE)
private val CLOSERS = TokenSet.create(T.RPAREN, T.RBRACKET, T.RBRACE)

private fun PsiElement.isKeyword(): Boolean = elementType == T.KEYWORD

private fun PsiElement.isName(): Boolean = elementType in NAMES

private fun topLevel(element: PsiElement) = HdlPsiUtil.topLevelChildren(element, OPENERS, CLOSERS)

/** Direct keyword children whose text is in [words]. */
private fun keywordChildren(element: PsiElement, words: Set<String>): List<PsiElement> =
    HdlPsiUtil.significantChildren(element).filter { it.isKeyword() && it.text in words }

/** Base class for named elements. Renaming isn't supported. */
abstract class VerilogNamedElement(node: ASTNode) : ASTWrapperPsiElement(node), PsiNameIdentifierOwner {
    override fun getName(): String? = nameIdentifier?.text

    override fun setName(name: String): PsiElement = throw IncorrectOperationException("Renaming isn't supported")

    /** Navigation lands on the name rather than the first keyword. */
    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: super.getTextOffset()
}

/** `module`, `interface`, `program`, `package`, `class`, `primitive`, `checker` or `config`. */
class VerilogDesignUnit(node: ASTNode) : VerilogNamedElement(node), HdlKeywordBlock {
    enum class Kind(val keyword: String, val icon: Icon) {
        MODULE("module", AllIcons.Nodes.Module),
        INTERFACE("interface", AllIcons.Nodes.Interface),
        PROGRAM("program", AllIcons.Nodes.Module),
        PACKAGE("package", AllIcons.Nodes.Package),
        CLASS("class", AllIcons.Nodes.Class),
        PRIMITIVE("primitive", AllIcons.Nodes.Function),
        CHECKER("checker", AllIcons.Nodes.Module),
        CONFIG("config", AllIcons.Nodes.ConfigFolder),
    }

    /** The keywords before the name, e.g. `virtual class` or `interface class`. */
    private fun leadingKeywords(): List<String> =
        HdlPsiUtil.significantChildren(this).takeWhile { it.isKeyword() }.map { it.text }

    val kind: Kind
        get() {
            val words = leadingKeywords()
            if ("class" in words) return Kind.CLASS
            return when (words.firstOrNull { it in UNIT_KEYWORDS }) {
                "interface" -> Kind.INTERFACE
                "program" -> Kind.PROGRAM
                "package" -> Kind.PACKAGE
                "primitive" -> Kind.PRIMITIVE
                "checker" -> Kind.CHECKER
                "config" -> Kind.CONFIG
                else -> Kind.MODULE
            }
        }

    override fun getNameIdentifier(): PsiElement? =
        HdlPsiUtil.significantChildren(this).firstOrNull { it.isName() }

    val parameters: List<VerilogPort>
        get() = findChildByType<PsiElement>(VerilogElementTypes.PARAMETER_LIST)
            ?.let { PsiTreeUtil.getChildrenOfTypeAsList(it, VerilogPort::class.java) }
            .orEmpty()

    val ports: List<VerilogPort>
        get() = findChildByType<PsiElement>(VerilogElementTypes.PORT_LIST)
            ?.let { PsiTreeUtil.getChildrenOfTypeAsList(it, VerilogPort::class.java) }
            .orEmpty()

    override fun markerKeywords(): List<PsiElement> = keywordChildren(this, UNIT_MARKERS)

    override fun closingKeyword(): PsiElement? = keywordChildren(this, UNIT_ENDS).lastOrNull()

    override fun getPresentation(): ItemPresentation = presentation(name, kind.keyword, kind.icon)

    override fun getIcon(flags: Int): Icon = kind.icon

    private companion object {
        val UNIT_KEYWORDS = setOf(
            "module", "macromodule", "interface", "program", "package", "class", "primitive", "checker", "config",
        )
        val UNIT_ENDS = setOf(
            "endmodule", "endinterface", "endprogram", "endpackage", "endclass", "endprimitive", "endchecker",
            "endconfig",
        )
        val UNIT_MARKERS = UNIT_KEYWORDS + UNIT_ENDS
    }
}

/** A port in a module header, or a parameter in its `#( ... )` list. */
class VerilogPort(node: ASTNode) : VerilogNamedElement(node) {
    val isParameter: Boolean get() = node.elementType == VerilogElementTypes.PARAMETER

    /** The tokens before any `=` default value. */
    private fun beforeDefault(): List<PsiElement> = topLevel(this).takeWhile { it.text != "=" }

    /** The last name before `=`: `clk` in `input wire clk`, `a` in `.a(x)`. */
    override fun getNameIdentifier(): PsiElement? = beforeDefault().lastOrNull { it.isName() }

    /** The previous port in the list. ANSI ports inherit their direction and type from it: `input a, b`. */
    private val previousPort: VerilogPort? get() = PsiTreeUtil.getPrevSiblingOfType(this, VerilogPort::class.java)

    /** `input`, `output`, `inout` or `ref`, or null for a non-ANSI port (`module m(a, b);`). */
    val direction: String?
        get() = keywordChildren(this, DIRECTIONS).firstOrNull()?.text
            ?: if (ownType().isEmpty()) previousPort?.direction else null

    /** Everything before the name, such as `input logic [7:0]`. */
    private fun ownType(): String {
        val name = nameIdentifier ?: return ""
        return collapsedText(this, textRange.startOffset, name.textRange.startOffset)
    }

    /** The port's direction and type, or the parameter's default value (`= 8`). */
    val description: String
        get() {
            if (isParameter) {
                val equals = topLevel(this).firstOrNull { it.text == "=" } ?: return ownType()
                return "= " + collapsedText(this, equals.textRange.endOffset, textRange.endOffset)
            }
            return ownType().ifEmpty { previousPort?.description.orEmpty() }
        }

    private val icon: Icon get() = if (isParameter) AllIcons.Nodes.Constant else AllIcons.Nodes.Parameter

    override fun getPresentation(): ItemPresentation = presentation(name, description, icon)

    override fun getIcon(flags: Int): Icon = icon

    private companion object {
        val DIRECTIONS = setOf("input", "output", "inout", "ref")
    }
}

/** `wire [7:0] a, b;`, `parameter N = 4;`, `typedef enum {...} state_t;`, `light_t light;` */
class VerilogDeclaration(node: ASTNode) : ASTWrapperPsiElement(node) {
    /** The first keyword, such as `wire` or `localparam`. Null for a user-defined type (`state_t s;`). */
    val keyword: String?
        get() = HdlPsiUtil.significantChildren(this).firstOrNull()?.takeIf { it.isKeyword() }?.text

    /**
     * The declared names. Each comma-separated part declares one name: the last name before any `=`.
     * Names inside brackets and braces (`[N-1:0]`, enum members) don't count.
     */
    val names: List<PsiElement>
        get() = HdlPsiUtil.split(topLevel(this), T.COMMA).mapNotNull { part ->
            part.takeWhile { it.text != "=" }.lastOrNull { it.isName() }
        }

    /** The type, which is everything before the first name: `logic [7:0]`. */
    val typeText: String
        get() {
            val first = names.firstOrNull() ?: return ""
            return collapsedText(this, textRange.startOffset, first.textRange.startOffset)
        }

    val icon: Icon
        get() = when (keyword) {
            "parameter", "localparam", "specparam" -> AllIcons.Nodes.Constant
            "typedef" -> AllIcons.Nodes.Type
            else -> AllIcons.Nodes.Variable
        }
}

/** `counter #(.W(8)) u_counter (...);` */
class VerilogInstance(node: ASTNode) : ASTWrapperPsiElement(node) {
    val unitReference: VerilogUnitReference? get() = findChildByClass(VerilogUnitReference::class.java)

    /** The instance names. One statement can create several: `inv a(...), b(...);` */
    val instanceNames: List<PsiElement> get() = topLevel(this).filter { it.isName() }

    override fun getPresentation(): ItemPresentation =
        presentation(instanceNames.joinToString(", ") { it.text }, unitReference?.text, AllIcons.Nodes.Plugin)
}

/** A design unit name used elsewhere: an instantiated module, a base class, or `pkg` in `pkg::name`. */
class VerilogUnitReference(node: ASTNode) : ASTWrapperPsiElement(node) {
    override fun getName(): String = text
}

/** `always`, `always_ff`, `always_comb`, `always_latch`, `initial` or `final`. */
class VerilogProcess(node: ASTNode) : ASTWrapperPsiElement(node) {
    private val body: VerilogBlock? get() = findChildByClass(VerilogBlock::class.java)

    /** The keyword and any timing controls after it: `always_ff @(posedge clk)`, `always #5`, `initial`. */
    val header: String
        get() {
            val children = HdlPsiUtil.significantChildren(this)
            var end = 0 // Index just past the header.
            if (children.firstOrNull()?.isKeyword() == true) end = 1
            while (end < children.size && children[end].text in TIMING) {
                end++
                if (end < children.size && children[end].elementType in OPENERS) {
                    var depth = 0
                    do {
                        when (children[end].elementType) {
                            in OPENERS -> depth++
                            in CLOSERS -> depth--
                        }
                        end++
                    } while (end < children.size && depth > 0)
                } else if (end < children.size) {
                    end++ // @*, @ev, #5, #`DELAY
                }
            }
            if (end == 0) return ""
            val text = collapsedText(this, textRange.startOffset, children[end - 1].textRange.endOffset)
            return HdlPsiUtil.abbreviate(text)
        }

    /** The label of the body: `seq` in `always_ff @(posedge clk) begin : seq`. */
    val label: String? get() = body?.label

    override fun getPresentation(): ItemPresentation = presentation(header, label, AllIcons.Nodes.Lambda)

    private companion object {
        val TIMING = setOf("@", "#", "##")
    }
}

/** `function ... endfunction` or `task ... endtask` */
class VerilogSubprogram(node: ASTNode) : VerilogNamedElement(node), HdlKeywordBlock {
    val isTask: Boolean
        get() = HdlPsiUtil.significantChildren(this).firstOrNull { it.isKeyword() && it.text in KEYWORDS }?.text == "task"

    /** The last name before the argument list: `f` in `function automatic logic [7:0] f(...)`, or `new`. */
    override fun getNameIdentifier(): PsiElement? = HdlPsiUtil.significantChildren(this)
        .takeWhile { it.elementType != T.LPAREN && it.elementType != T.SEMICOLON }
        .lastOrNull { it.isName() || (it.isKeyword() && it.text == "new") }

    override fun markerKeywords(): List<PsiElement> = keywordChildren(this, MARKERS)

    override fun closingKeyword(): PsiElement? = keywordChildren(this, setOf("endfunction", "endtask")).lastOrNull()

    private val icon: Icon get() = if (isTask) AllIcons.Nodes.Method else AllIcons.Nodes.Function

    override fun getPresentation(): ItemPresentation = presentation(name, if (isTask) "task" else "function", icon)

    override fun getIcon(flags: Int): Icon = icon

    private companion object {
        val KEYWORDS = setOf("function", "task")
        val MARKERS = setOf("function", "task", "endfunction", "endtask")
    }
}

/** `begin`/`end`, `fork`/`join`, `case`/`endcase`, `generate`/`endgenerate`, and other keyword pairs. */
class VerilogBlock(node: ASTNode) : ASTWrapperPsiElement(node), HdlKeywordBlock {
    /** The keyword that opens the block. */
    val keyword: String? get() = HdlPsiUtil.significantChildren(this).firstOrNull()?.takeIf { it.isKeyword() }?.text

    /**
     * The block's name: the label after `begin :` or `fork :`, or the name after
     * `covergroup`, `property`, `sequence` or `clocking`.
     */
    val label: String?
        get() {
            val children = HdlPsiUtil.significantChildren(this)
            return when (node.elementType) {
                VerilogElementTypes.BLOCK ->
                    if (children.getOrNull(1)?.text == ":") children.getOrNull(2)?.takeIf { it.isName() }?.text else null
                VerilogElementTypes.CONSTRUCT -> children.getOrNull(1)?.takeIf { it.isName() }?.text
                else -> null
            }
        }

    override fun markerKeywords(): List<PsiElement> = keywordChildren(this, MARKERS)

    override fun closingKeyword(): PsiElement? = keywordChildren(this, ENDS).lastOrNull()

    /** Named blocks appear in the structure view: `g_lane` (begin), `cg` (covergroup). */
    override fun getPresentation(): ItemPresentation {
        val icon = if (node.elementType == VerilogElementTypes.CONSTRUCT) AllIcons.Nodes.Property else AllIcons.Nodes.Folder
        return presentation(label ?: keyword, keyword, icon)
    }

    private companion object {
        val ENDS = setOf(
            "end", "join", "join_any", "join_none", "endcase", "endgenerate", "endgroup", "endproperty",
            "endsequence", "endclocking", "endspecify", "endtable",
        )
        val MARKERS = ENDS + setOf(
            "begin", "fork", "case", "casex", "casez", "randcase", "generate", "covergroup", "property",
            "sequence", "clocking", "specify", "table", "randsequence",
        )
    }
}
