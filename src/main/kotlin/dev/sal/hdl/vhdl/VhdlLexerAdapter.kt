package dev.sal.hdl.vhdl

import com.intellij.lexer.FlexAdapter

/** Wraps the JFlex-generated lexer (from `src/main/grammars/Vhdl.flex`) in the platform's `Lexer` interface. */
class VhdlLexerAdapter : FlexAdapter(_VhdlLexer(null))
