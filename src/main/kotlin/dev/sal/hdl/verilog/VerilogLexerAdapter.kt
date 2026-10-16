package dev.sal.hdl.verilog

import com.intellij.lexer.FlexAdapter

/** Wraps the JFlex-generated lexer (from `src/main/grammars/Verilog.flex`) in the platform's `Lexer` interface. */
class VerilogLexerAdapter : FlexAdapter(_VerilogLexer(null))
