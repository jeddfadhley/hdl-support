package dev.sal.hdl.verilog

import com.intellij.openapi.fileTypes.LanguageFileType
import dev.sal.hdl.HdlIcons
import javax.swing.Icon

/** File type for `.v`, `.vh`, `.sv` and `.svh` files. The extensions are registered in plugin.xml. */
object VerilogFileType : LanguageFileType(VerilogLanguage) {
    override fun getName(): String = "Verilog/SystemVerilog"

    override fun getDescription(): String = "Verilog / SystemVerilog source file"

    override fun getDefaultExtension(): String = "sv"

    override fun getIcon(): Icon = HdlIcons.VERILOG
}
