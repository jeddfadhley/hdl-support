package dev.sal.hdl.vhdl

import com.intellij.lang.Commenter

/** Makes Ctrl+/ use `--` and Ctrl+Shift+/ use the VHDL-2008 `/* */` block comment. */
class VhdlCommenter : Commenter {
    override fun getLineCommentPrefix(): String = "--"

    override fun getBlockCommentPrefix(): String = "/*"

    override fun getBlockCommentSuffix(): String = "*/"

    override fun getCommentedBlockCommentPrefix(): String? = null

    override fun getCommentedBlockCommentSuffix(): String? = null
}
