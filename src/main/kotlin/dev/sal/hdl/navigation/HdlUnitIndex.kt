package dev.sal.hdl.navigation

import com.intellij.lexer.Lexer
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.indexing.ScalarIndexExtension
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import dev.sal.hdl.verilog.VerilogFileType
import dev.sal.hdl.verilog.VerilogLexerAdapter
import dev.sal.hdl.verilog.VerilogTokenTypes
import dev.sal.hdl.vhdl.VhdlFileType
import dev.sal.hdl.vhdl.VhdlLexerAdapter
import dev.sal.hdl.vhdl.VhdlTokenTypes

/**
 * Maps design unit names to the files that declare them, so go-to-definition can find a module or
 * entity anywhere in the project without parsing every file.
 *
 * Keys are lower case for both languages, so a VHDL instance can find a Verilog module. The index
 * only narrows the search to a few files: [HdlUnits] then checks the parsed design units, so an
 * occasional false positive here does no harm.
 *
 * Bump [getVersion] whenever the indexing logic changes.
 */
class HdlUnitIndex : ScalarIndexExtension<String>() {
    override fun getName(): ID<String, Void> = NAME

    override fun getIndexer(): DataIndexer<String, Void, FileContent> = DataIndexer { content ->
        val names = when (content.fileType) {
            VerilogFileType -> verilogUnitNames(content.contentAsText)
            VhdlFileType -> vhdlUnitNames(content.contentAsText)
            else -> emptyList()
        }
        names.associate { it.lowercase() to null }
    }

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getVersion(): Int = 1

    override fun getInputFilter(): FileBasedIndex.InputFilter =
        DefaultFileTypeSpecificInputFilter(VerilogFileType, VhdlFileType)

    override fun dependsOnFileContent(): Boolean = true

    companion object {
        @JvmField
        val NAME: ID<String, Void> = ID.create("dev.sal.hdl.units")

        private val VERILOG_UNIT_KEYWORDS = setOf(
            "module", "macromodule", "interface", "program", "package", "class", "primitive", "checker",
        )

        /** `module m`, `package p`, `virtual class c`, ... but not `virtual interface` or `typedef class`. */
        fun verilogUnitNames(text: CharSequence): List<String> {
            val tokens = significantTokens(VerilogLexerAdapter(), text, VerilogTokenTypes.COMMENTS.types.toSet())
            val names = ArrayList<String>()
            for (i in tokens.indices) {
                val (type, word) = tokens[i]
                if (type != VerilogTokenTypes.KEYWORD || word !in VERILOG_UNIT_KEYWORDS) continue
                val previous = tokens.getOrNull(i - 1)?.second
                if (previous == "typedef" || (previous == "virtual" && word == "interface")) continue
                // Skip lifetimes and `interface class`: module automatic m, interface class c.
                var j = i + 1
                while (tokens.getOrNull(j)?.first == VerilogTokenTypes.KEYWORD) j++
                val name = tokens.getOrNull(j) ?: continue
                if (name.first == VerilogTokenTypes.IDENTIFIER || name.first == VerilogTokenTypes.ESCAPED_IDENTIFIER) {
                    names.add(name.second)
                }
            }
            return names
        }

        /** `entity e is` and `package p is`, but not `package body p is` or `u : entity work.e`. */
        fun vhdlUnitNames(text: CharSequence): List<String> {
            val tokens = significantTokens(VhdlLexerAdapter(), text, VhdlTokenTypes.COMMENTS.types.toSet())
            val names = ArrayList<String>()
            for (i in tokens.indices) {
                val (type, word) = tokens[i]
                if (type != VhdlTokenTypes.KEYWORD) continue
                if (!word.equals("entity", ignoreCase = true) && !word.equals("package", ignoreCase = true)) continue
                val name = tokens.getOrNull(i + 1) ?: continue
                val isWord = tokens.getOrNull(i + 2)?.second
                if ((name.first == VhdlTokenTypes.IDENTIFIER || name.first == VhdlTokenTypes.EXTENDED_IDENTIFIER) &&
                    isWord.equals("is", ignoreCase = true)
                ) {
                    names.add(name.second)
                }
            }
            return names
        }

        /** The tokens of [text] without whitespace and comments, as (type, text) pairs. */
        private fun significantTokens(
            lexer: Lexer,
            text: CharSequence,
            comments: Set<IElementType>,
        ): List<Pair<IElementType, String>> {
            val result = ArrayList<Pair<IElementType, String>>()
            lexer.start(text)
            while (true) {
                val type = lexer.tokenType ?: break
                if (type != TokenType.WHITE_SPACE && type !in comments) {
                    result.add(type to text.subSequence(lexer.tokenStart, lexer.tokenEnd).toString())
                }
                lexer.advance()
            }
            return result
        }
    }
}
