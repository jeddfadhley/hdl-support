package dev.sal.hdl.vhdl

import com.intellij.openapi.fileTypes.LanguageFileType
import dev.sal.hdl.HdlIcons
import javax.swing.Icon

/** File type for `.vhd` and `.vhdl` files. The extensions are registered in plugin.xml. */
object VhdlFileType : LanguageFileType(VhdlLanguage) {
    override fun getName(): String = "VHDL"

    override fun getDescription(): String = "VHDL source file"

    override fun getDefaultExtension(): String = "vhd"

    override fun getIcon(): Icon = HdlIcons.VHDL
}
