package dev.sal.hdl.verilog

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider

/** PSI root of a Verilog/SystemVerilog file. */
class VerilogFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, VerilogLanguage) {
    override fun getFileType(): FileType = VerilogFileType

    override fun toString(): String = "Verilog File"
}
