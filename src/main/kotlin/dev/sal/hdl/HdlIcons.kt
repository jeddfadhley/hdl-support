package dev.sal.hdl

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/** Icons shown in the project tree, editor tabs and the colour settings pages. */
object HdlIcons {
    @JvmField
    val VERILOG: Icon = IconLoader.getIcon("/icons/verilog.svg", HdlIcons::class.java)

    @JvmField
    val VHDL: Icon = IconLoader.getIcon("/icons/vhdl.svg", HdlIcons::class.java)
}
