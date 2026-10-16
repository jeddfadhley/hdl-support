package dev.sal.hdl.vhdl

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider

/** PSI root of a VHDL file. */
class VhdlFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, VhdlLanguage) {
    override fun getFileType(): FileType = VhdlFileType

    override fun toString(): String = "VHDL File"
}
