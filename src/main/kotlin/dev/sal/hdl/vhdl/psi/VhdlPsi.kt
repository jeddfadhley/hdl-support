package dev.sal.hdl.vhdl.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.icons.AllIcons
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiReference
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.util.IncorrectOperationException
import dev.sal.hdl.navigation.HdlUnitReference
import dev.sal.hdl.navigation.HdlUnits
import dev.sal.hdl.psi.HdlKeywordBlock
import dev.sal.hdl.psi.HdlPsiUtil
import dev.sal.hdl.psi.HdlPsiUtil.collapsedText
import dev.sal.hdl.psi.HdlPsiUtil.presentation
import dev.sal.hdl.verilog.psi.VerilogDesignUnit
import javax.swing.Icon
import dev.sal.hdl.vhdl.VhdlTokenTypes as T

// PSI classes for the nodes in VhdlElementTypes. They work out names and other details from
// their tokens on demand, because the parser doesn't wrap names in nodes of their own.
// VHDL is case-insensitive: keywords are compared in lower case, and names keep their source spelling.

private val NAMES = TokenSet.create(T.IDENTIFIER, T.EXTENDED_IDENTIFIER)
private val OPENERS = TokenSet.create(T.LPAREN, T.LBRACKET)
private val CLOSERS = TokenSet.create(T.RPAREN, T.RBRACKET)

/** Keywords that can follow `end`. Must match the parser's list. */
private val END_TRAILERS = setOf(
    "entity", "architecture", "package", "body", "configuration", "context", "component", "record", "units",
    "protected", "process", "postponed", "block", "generate", "if", "case", "loop", "function", "procedure", "for",
)

private fun PsiElement.isKeyword(): Boolean = elementType == T.KEYWORD

private fun PsiElement.isKeyword(word: String): Boolean = isKeyword() && text.equals(word, ignoreCase = true)

private fun PsiElement.isName(): Boolean = elementType in NAMES

private fun PsiElement.keywordText(): String? = if (isKeyword()) text.lowercase() else null

private fun topLevel(element: PsiElement) = HdlPsiUtil.topLevelChildren(element, OPENERS, CLOSERS)

/** The label of a labelled statement: `tick` in `tick : process (clk)`. */
private fun labelOf(element: PsiElement): PsiElement? {
    val children = HdlPsiUtil.significantChildren(element)
    return children.firstOrNull()?.takeIf { it.isName() && children.getOrNull(1)?.text == ":" }
}

/**
 * The keywords to highlight together for a construct: its first keyword, any direct-child keywords
 * in [middles] (such as `begin` or `elsif`), and the closing `end` with the keywords after it.
 */
private fun vhdlMarkers(element: PsiElement, middles: Set<String>): List<PsiElement> {
    val children = HdlPsiUtil.significantChildren(element)
    val end = children.indexOfLast { it.isKeyword("end") }
    if (end < 0) return emptyList()
    val opener = children.indexOfFirst { it.isKeyword() }
    val result = ArrayList<PsiElement>()
    for ((index, child) in children.withIndex()) {
        val word = child.keywordText() ?: continue
        when {
            index == opener -> result.add(child)
            index < end && word in middles -> result.add(child)
            index == end -> result.add(child)
        }
    }
    // The keywords that say what the end closes: end if, end package body.
    var next = end + 1
    while (next < children.size && children[next].keywordText() in END_TRAILERS) {
        result.add(children[next])
        next++
    }
    return result
}

private fun closingEnd(element: PsiElement): PsiElement? =
    HdlPsiUtil.significantChildren(element).lastOrNull { it.isKeyword("end") }

/** Base class for named elements. Renaming isn't supported. */
abstract class VhdlNamedElement(node: ASTNode) : ASTWrapperPsiElement(node), PsiNameIdentifierOwner {
    override fun getName(): String? = nameIdentifier?.text

    override fun setName(name: String): PsiElement = throw IncorrectOperationException("Renaming isn't supported")

    /** Navigation lands on the name rather than the first keyword. */
    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: super.getTextOffset()
}

/** `entity`, `architecture`, `package`, `package body`, `configuration` or `context`. */
class VhdlDesignUnit(node: ASTNode) : VhdlNamedElement(node), HdlKeywordBlock {
    enum class Kind(val label: String, val icon: Icon) {
        ENTITY("entity", AllIcons.Nodes.Interface),
        ARCHITECTURE("architecture", AllIcons.Nodes.Class),
        PACKAGE("package", AllIcons.Nodes.Package),
        PACKAGE_BODY("package body", AllIcons.Nodes.Package),
        CONFIGURATION("configuration", AllIcons.Nodes.ConfigFolder),
        CONTEXT("context", AllIcons.Nodes.ConfigFolder),
    }

    val kind: Kind
        get() {
            val children = HdlPsiUtil.significantChildren(this)
            return when (children.firstOrNull()?.keywordText()) {
                "architecture" -> Kind.ARCHITECTURE
                "package" -> if (children.getOrNull(1)?.isKeyword("body") == true) Kind.PACKAGE_BODY else Kind.PACKAGE
                "configuration" -> Kind.CONFIGURATION
                "context" -> Kind.CONTEXT
                else -> Kind.ENTITY
            }
        }

    override fun getNameIdentifier(): PsiElement? = HdlPsiUtil.significantChildren(this).firstOrNull { it.isName() }

    /** The entity an architecture or configuration belongs to. */
    val entityReference: VhdlUnitReference? get() = findChildByClass(VhdlUnitReference::class.java)

    val generics: List<VhdlInterfaceDeclaration> get() = interfaceDeclarations(this, VhdlElementTypes.GENERIC_CLAUSE)

    val ports: List<VhdlInterfaceDeclaration> get() = interfaceDeclarations(this, VhdlElementTypes.PORT_CLAUSE)

    override fun markerKeywords(): List<PsiElement> = vhdlMarkers(this, setOf("body", "begin"))

    override fun closingKeyword(): PsiElement? = closingEnd(this)

    /** `of Counter` for an architecture, otherwise the kind of unit. */
    val description: String
        get() = entityReference?.let { "of ${it.text}" } ?: kind.label

    override fun getPresentation(): ItemPresentation = presentation(name, description, kind.icon)

    override fun getIcon(flags: Int): Icon = kind.icon
}

private fun interfaceDeclarations(owner: PsiElement, clause: VhdlElementType): List<VhdlInterfaceDeclaration> =
    owner.node.getChildren(TokenSet.create(clause)).flatMap {
        PsiTreeUtil.getChildrenOfTypeAsList(it.psi, VhdlInterfaceDeclaration::class.java)
    }

/** One entry in a generic or port clause: `clk, rst : in std_logic` or `WIDTH : positive := 8`. */
class VhdlInterfaceDeclaration(node: ASTNode) : ASTWrapperPsiElement(node) {
    val isGeneric: Boolean get() = node.treeParent?.elementType == VhdlElementTypes.GENERIC_CLAUSE

    private fun colon(): PsiElement? = topLevel(this).firstOrNull { it.text == ":" }

    /** The names before the `:`. A leading `signal` or `constant` isn't a name. */
    val names: List<PsiElement> get() = topLevel(this).takeWhile { it.text != ":" }.filter { it.isName() }

    /** `in`, `out`, `inout`, `buffer` or `linkage`, if given. */
    val mode: String?
        get() = topLevel(this).dropWhile { it.text != ":" }.drop(1).firstOrNull()
            ?.keywordText()?.takeIf { it in MODES }

    /** Everything after the `:` up to any default value: `in std_logic_vector(7 downto 0)`. */
    val typeText: String
        get() {
            val colon = colon() ?: return ""
            val default = topLevel(this).firstOrNull { it.text == ":=" }
            val end = default?.textRange?.startOffset ?: textRange.endOffset
            return collapsedText(this, colon.textRange.endOffset, end)
        }

    val icon: Icon get() = if (isGeneric) AllIcons.Nodes.Constant else AllIcons.Nodes.Parameter

    private companion object {
        val MODES = setOf("in", "out", "inout", "buffer", "linkage")
    }
}

/** `signal`, `constant`, `variable`, `type`, `subtype`, `alias`, `file`, `attribute` and `group` declarations. */
class VhdlDeclaration(node: ASTNode) : ASTWrapperPsiElement(node), HdlKeywordBlock {
    /** The first keyword in lower case: `signal`, `type`, ... (`shared` for a shared variable). */
    val keyword: String? get() = HdlPsiUtil.significantChildren(this).firstOrNull()?.keywordText()

    /** Declarations that name one thing straight after the keyword: `type t is ...`. */
    private val namesOneThing: Boolean get() = keyword in setOf("type", "subtype", "alias", "attribute", "group")

    val names: List<PsiElement>
        get() {
            val tokens = topLevel(this)
            if (namesOneThing) return listOfNotNull(tokens.firstOrNull { it.isName() })
            return tokens.takeWhile { it.text != ":" }.filter { it.isName() }
        }

    /** `record`, `units` or `protected`, for the types that have a body ending in `end`. */
    private fun bodyKeyword(): PsiElement? = HdlPsiUtil.significantChildren(this)
        .firstOrNull { it.keywordText() in setOf("record", "units", "protected") }

    /**
     * The type of an object (`unsigned(7 downto 0)`), or the definition of a type (`(IDLE, BUSY, DONE)`).
     * A record, physical or protected type is described by its keyword alone.
     */
    val typeText: String
        get() {
            bodyKeyword()?.let { return it.text.lowercase() }
            val tokens = topLevel(this)
            val start = if (namesOneThing) {
                tokens.firstOrNull { it.isKeyword("is") || it.text == ":" }
            } else {
                tokens.firstOrNull { it.text == ":" }
            } ?: return ""
            val stop = tokens.firstOrNull {
                it.textRange.startOffset > start.textRange.startOffset &&
                    (it.text == ":=" || it.elementType == T.SEMICOLON || it.isKeyword("is"))
            }
            val text = collapsedText(this, start.textRange.endOffset, stop?.textRange?.startOffset ?: textRange.endOffset)
            return HdlPsiUtil.abbreviate(text, 40)
        }

    val icon: Icon
        get() = when (keyword) {
            "constant" -> AllIcons.Nodes.Constant
            "type", "subtype" -> AllIcons.Nodes.Type
            "alias" -> AllIcons.Nodes.Alias
            "attribute", "group" -> AllIcons.Nodes.Property
            "signal" -> AllIcons.Nodes.Field
            else -> AllIcons.Nodes.Variable
        }

    /** Only record, physical and protected types have keyword markers: `record` ... `end record`. */
    override fun markerKeywords(): List<PsiElement> {
        val body = bodyKeyword()
        if (body == null || closingEnd(this) == null) return emptyList()
        // vhdlMarkers starts with the first keyword, `type`, which isn't part of the pair.
        return listOf(body) + vhdlMarkers(this, emptySet()).drop(1)
    }

    override fun closingKeyword(): PsiElement? = closingEnd(this)
}

/** `component c is generic (...); port (...); end component;` */
class VhdlComponent(node: ASTNode) : VhdlNamedElement(node), HdlKeywordBlock {
    override fun getNameIdentifier(): PsiElement? = HdlPsiUtil.significantChildren(this).firstOrNull { it.isName() }

    val generics: List<VhdlInterfaceDeclaration> get() = interfaceDeclarations(this, VhdlElementTypes.GENERIC_CLAUSE)

    val ports: List<VhdlInterfaceDeclaration> get() = interfaceDeclarations(this, VhdlElementTypes.PORT_CLAUSE)

    override fun markerKeywords(): List<PsiElement> = vhdlMarkers(this, emptySet())

    override fun closingKeyword(): PsiElement? = closingEnd(this)

    override fun getPresentation(): ItemPresentation = presentation(name, "component", AllIcons.Nodes.Plugin)
}

/** A function or procedure declaration or body. */
class VhdlSubprogram(node: ASTNode) : VhdlNamedElement(node), HdlKeywordBlock {
    val isFunction: Boolean
        get() = HdlPsiUtil.significantChildren(this).any { it.isKeyword("function") }

    /** The name after `function` or `procedure`. Operator functions are named by a string: `"+"`. */
    override fun getNameIdentifier(): PsiElement? = HdlPsiUtil.significantChildren(this)
        .dropWhile { !it.isKeyword("function") && !it.isKeyword("procedure") }
        .drop(1)
        .firstOrNull()
        ?.takeIf { it.isName() || it.elementType == T.STRING }

    /** True for a body (`is ... begin ... end`), false for a declaration ending in `;`. */
    val hasBody: Boolean get() = closingEnd(this) != null

    override fun markerKeywords(): List<PsiElement> = vhdlMarkers(this, setOf("begin"))

    override fun closingKeyword(): PsiElement? = closingEnd(this)

    private val icon: Icon get() = if (isFunction) AllIcons.Nodes.Function else AllIcons.Nodes.Method

    override fun getPresentation(): ItemPresentation =
        presentation(name, if (isFunction) "function" else "procedure", icon)

    override fun getIcon(flags: Int): Icon = icon
}

/** A process, block, generate, `if`, `case` or loop statement. */
class VhdlStatement(node: ASTNode) : ASTWrapperPsiElement(node), HdlKeywordBlock {
    val label: String? get() = labelOf(this)?.text

    /** The first keyword in lower case: `process`, `for`, `if`, ... */
    val keyword: String? get() = HdlPsiUtil.significantChildren(this).firstOrNull { it.isKeyword() }?.keywordText()

    /**
     * The statement's header after its label, on one line: `process (clk)` or `for i in 0 to 3 generate`.
     * It ends at the first `is`, `begin`, `then` or `loop`, or the end of the first line.
     */
    val header: String
        get() {
            val children = HdlPsiUtil.significantChildren(this)
            val first = children.firstOrNull { it.isKeyword() } ?: return ""
            val stop = children.firstOrNull {
                it.textRange.startOffset > first.textRange.startOffset && it.keywordText() in HEADER_STOPS
            }
            val end = stop?.let {
                if (it.isKeyword("generate") || it.isKeyword("loop")) it.textRange.endOffset else it.textRange.startOffset
            } ?: textRange.endOffset
            val text = containingFile.text.substring(first.textRange.startOffset, end).lineSequence().first()
            return HdlPsiUtil.abbreviate(text.replace(Regex("\\s+"), " ").trim())
        }

    override fun markerKeywords(): List<PsiElement> = vhdlMarkers(this, MIDDLES[node.elementType].orEmpty())

    override fun closingKeyword(): PsiElement? = closingEnd(this)

    val icon: Icon
        get() = when (node.elementType) {
            VhdlElementTypes.PROCESS -> AllIcons.Nodes.Lambda
            else -> AllIcons.Nodes.Folder
        }

    override fun getPresentation(): ItemPresentation =
        presentation(label ?: header, if (label != null) header else null, icon)

    private companion object {
        val HEADER_STOPS = setOf("is", "begin", "then", "loop", "generate")

        /** Keywords between the opener and `end` that belong to each kind of statement. */
        val MIDDLES = mapOf(
            VhdlElementTypes.PROCESS to setOf("begin"),
            VhdlElementTypes.BLOCK to setOf("begin"),
            VhdlElementTypes.GENERATE to setOf("generate", "elsif", "else", "when"),
            VhdlElementTypes.IF to setOf("then", "elsif", "else"),
            VhdlElementTypes.CASE to setOf("is", "when"),
            VhdlElementTypes.LOOP to setOf("loop"),
        )
    }
}

/** `u1 : entity work.counter port map (...);` */
class VhdlInstance(node: ASTNode) : VhdlNamedElement(node) {
    /** The instance label is its name. */
    override fun getNameIdentifier(): PsiElement? = labelOf(this)

    val unitReference: VhdlUnitReference? get() = findChildByClass(VhdlUnitReference::class.java)

    override fun getPresentation(): ItemPresentation =
        presentation(name, unitReference?.text, AllIcons.Nodes.Plugin)
}

/** A design unit name used elsewhere: the entity of an architecture or instance, or a package in a use clause. */
class VhdlUnitReference(node: ASTNode) : ASTWrapperPsiElement(node) {
    override fun getName(): String = text

    override fun getReference(): PsiReference = HdlUnitReference(this) { element ->
        val units = HdlUnits.find(element, element.text)
        when (element.parent) {
            // An instance can name a Verilog module too. If no entity exists, use the component declaration.
            is VhdlInstance -> units.filter { it is VerilogDesignUnit || (it as VhdlDesignUnit).kind == VhdlDesignUnit.Kind.ENTITY }
                .ifEmpty { localComponents(element) }
            is VhdlDesignUnit -> units.filter { it is VhdlDesignUnit && it.kind == VhdlDesignUnit.Kind.ENTITY }
            // A use clause names a package.
            else -> units.filter { it is VhdlDesignUnit && it.kind == VhdlDesignUnit.Kind.PACKAGE }
        }
    }

    private fun localComponents(element: PsiElement): List<PsiElement> =
        PsiTreeUtil.findChildrenOfType(element.containingFile, VhdlComponent::class.java)
            .filter { it.name.equals(element.text, ignoreCase = true) }
}
