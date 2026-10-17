package dev.sal.hdl.editor

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType
import com.intellij.lang.Language
import com.intellij.psi.PsiComment
import com.intellij.psi.util.PsiUtilCore
import dev.sal.hdl.verilog.VerilogLanguage
import dev.sal.hdl.verilog.VerilogTokenTypes
import dev.sal.hdl.vhdl.VhdlLanguage
import dev.sal.hdl.vhdl.VhdlTokenTypes

/**
 * Where the live templates in `liveTemplates/Verilog.xml` and `VHDL.xml` may expand: in code of their language, but
 * not inside comments or strings.
 */
abstract class HdlTemplateContext(presentableName: String, private val language: Language) :
    TemplateContextType(presentableName) {

    override fun isInContext(templateActionContext: TemplateActionContext): Boolean {
        val file = templateActionContext.file
        if (!file.language.isKindOf(language)) return false
        // The token the abbreviation starts in. At the end of the file there's none, so look at the one before.
        val offset = templateActionContext.startOffset
        val element = file.findElementAt(offset) ?: file.findElementAt(offset - 1) ?: return true
        val type = PsiUtilCore.getElementType(element)
        return element !is PsiComment && type !in VerilogTokenTypes.STRINGS && type !in VhdlTokenTypes.STRINGS &&
            type !in VerilogTokenTypes.COMMENTS && type !in VhdlTokenTypes.COMMENTS
    }
}

class VerilogTemplateContext : HdlTemplateContext("Verilog/SystemVerilog", VerilogLanguage)

class VhdlTemplateContext : HdlTemplateContext("VHDL", VhdlLanguage)
