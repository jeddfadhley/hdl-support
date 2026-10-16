package dev.sal.hdl.verilog

import com.intellij.lang.Commenter

/** Makes Ctrl+/ use `//` and Ctrl+Shift+/ use `/* */`. */
class VerilogCommenter : Commenter {
    override fun getLineCommentPrefix(): String = "//"

    override fun getBlockCommentPrefix(): String = "/*"

    override fun getBlockCommentSuffix(): String = "*/"

    override fun getCommentedBlockCommentPrefix(): String? = null

    override fun getCommentedBlockCommentSuffix(): String? = null
}
