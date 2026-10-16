// JFlex lexer for Verilog (IEEE 1364-2005) and SystemVerilog (IEEE 1800-2017).
//
// Generated into build/generated/lexer by `./gradlew generateLexers`. Don't edit the generated Java.
//
// Rules for this lexer:
//  * It must never throw. Every rule set ends with a catch-all that returns BAD_CHARACTER.
//  * Unterminated block comments and strings run to the end of the file or line and are
//    still returned as a single comment or string token.
//  * It has a single state, so the editor can restart it at any token boundary.

package dev.sal.hdl.verilog;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;
import static dev.sal.hdl.verilog.VerilogTokenTypes.*;

%%

%public
%class _VerilogLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

WhiteSpace = [ \t\f\r\n]+

LineComment = "//" [^\r\n]*
// Runs to EOF when there's no closing "*/".
BlockComment = "/*" ( [^*] | "*"+ [^*/] )* ( "*"+ "/" | "*"* )

// A backslash escapes any character, including a newline (line continuation).
// With no closing quote, the string ends at the end of the line.
String = \" ( [^\"\\\r\n] | \\ ( \r\n | [^] ) )* ( \" | \\ )?

Identifier = [a-zA-Z_] [a-zA-Z0-9_$]*
// IEEE 1364-2005 3.7.1: a backslash, then any printable characters up to whitespace.
EscapedIdentifier = \\ [^ \t\f\r\n]+
SystemTask = "$" [a-zA-Z0-9_$]+
Directive = "`" [a-zA-Z_] [a-zA-Z0-9_$]*

// ---- Numbers (IEEE 1364-2005 3.5.1, IEEE 1800-2017 5.7) ----
Decimal = [0-9] [0-9_]*
Size = {Decimal}
Signed = [sS]?
BinaryValue = [01xXzZ?] [01xXzZ?_]*
OctalValue = [0-7xXzZ?] [0-7xXzZ?_]*
DecimalValue = [0-9xXzZ?] [0-9xXzZ?_]*
HexValue = [0-9a-fA-FxXzZ?] [0-9a-fA-FxXzZ?_]*
// Optional spaces/tabs between the base and the digits are legal (8'h FF).
BaseGap = [ \t]*
BasedNumber = {Size}? "'" {Signed} ( [bB] {BaseGap} {BinaryValue}
                                   | [oO] {BaseGap} {OctalValue}
                                   | [dD] {BaseGap} {DecimalValue}
                                   | [hH] {BaseGap} {HexValue} )
// SystemVerilog unbased unsized literals: '0 '1 'x 'z
UnbasedUnsized = "'" [01xXzZ]
Exponent = [eE] [+-]? {Decimal}
Real = {Decimal} "." {Decimal} {Exponent}? | {Decimal} {Exponent}
// SystemVerilog time literals: 10ns, 2.5ps, 1step
TimeLiteral = ( {Decimal} | {Decimal} "." {Decimal} ) ( "s" | "ms" | "us" | "ns" | "ps" | "fs" | "step" )

// ---- Keywords ----
// IEEE 1364-2005 Annex B (Verilog) plus IEEE 1800-2017 Annex B (SystemVerilog additions).
// To add a keyword, append it to the appropriate list. Order doesn't matter.
Verilog2005Keyword =
    "always" | "and" | "assign" | "automatic" | "begin" | "buf" | "bufif0" | "bufif1"
  | "case" | "casex" | "casez" | "cell" | "cmos" | "config" | "deassign" | "default"
  | "defparam" | "design" | "disable" | "edge" | "else" | "end" | "endcase" | "endconfig"
  | "endfunction" | "endgenerate" | "endmodule" | "endprimitive" | "endspecify" | "endtable"
  | "endtask" | "event" | "for" | "force" | "forever" | "fork" | "function" | "generate"
  | "genvar" | "highz0" | "highz1" | "if" | "ifnone" | "incdir" | "include" | "initial"
  | "inout" | "input" | "instance" | "integer" | "join" | "large" | "liblist" | "library"
  | "localparam" | "macromodule" | "medium" | "module" | "nand" | "negedge" | "nmos" | "nor"
  | "noshowcancelled" | "not" | "notif0" | "notif1" | "or" | "output" | "parameter" | "pmos"
  | "posedge" | "primitive" | "pull0" | "pull1" | "pulldown" | "pullup"
  | "pulsestyle_ondetect" | "pulsestyle_onevent" | "rcmos" | "real" | "realtime" | "reg"
  | "release" | "repeat" | "rnmos" | "rpmos" | "rtran" | "rtranif0" | "rtranif1" | "scalared"
  | "showcancelled" | "signed" | "small" | "specify" | "specparam" | "strong0" | "strong1"
  | "supply0" | "supply1" | "table" | "task" | "time" | "tran" | "tranif0" | "tranif1" | "tri"
  | "tri0" | "tri1" | "triand" | "trior" | "trireg" | "unsigned" | "use" | "uwire" | "vectored"
  | "wait" | "wand" | "weak0" | "weak1" | "while" | "wire" | "wor" | "xnor" | "xor"

SystemVerilog2017Keyword =
    "accept_on" | "alias" | "always_comb" | "always_ff" | "always_latch" | "assert" | "assume"
  | "before" | "bind" | "bins" | "binsof" | "bit" | "break" | "byte" | "chandle" | "checker"
  | "class" | "clocking" | "const" | "constraint" | "context" | "continue" | "cover"
  | "covergroup" | "coverpoint" | "cross" | "dist" | "do" | "endchecker" | "endclass"
  | "endclocking" | "endgroup" | "endinterface" | "endpackage" | "endprogram" | "endproperty"
  | "endsequence" | "enum" | "eventually" | "expect" | "export" | "extends" | "extern"
  | "final" | "first_match" | "foreach" | "forkjoin" | "global" | "iff" | "ignore_bins"
  | "illegal_bins" | "implements" | "implies" | "import" | "inside" | "int" | "interconnect"
  | "interface" | "intersect" | "join_any" | "join_none" | "let" | "local" | "logic"
  | "longint" | "matches" | "modport" | "nettype" | "new" | "nexttime" | "null" | "package"
  | "packed" | "priority" | "program" | "property" | "protected" | "pure" | "rand" | "randc"
  | "randcase" | "randsequence" | "ref" | "reject_on" | "restrict" | "return" | "s_always"
  | "s_eventually" | "s_nexttime" | "s_until" | "s_until_with" | "sequence" | "shortint"
  | "shortreal" | "soft" | "solve" | "static" | "string" | "strong" | "struct" | "super"
  | "sync_accept_on" | "sync_reject_on" | "tagged" | "this" | "throughout" | "timeprecision"
  | "timeunit" | "type" | "typedef" | "union" | "unique" | "unique0" | "until" | "until_with"
  | "untyped" | "var" | "virtual" | "void" | "wait_order" | "weak" | "wildcard" | "with"
  | "within"
// ---- End of keywords ----

// Longest operators first is not required (JFlex always takes the longest match), but it reads better.
Operator =
    "<<<=" | ">>>=" | "===" | "!==" | "==?" | "!=?" | "<<<" | ">>>" | "<<=" | ">>=" | "->>"
  | "<->" | "|->" | "|=>" | "&&&" | "##" | "**" | "&&" | "||" | "==" | "!=" | "<=" | ">="
  | "<<" | ">>" | "~&" | "~|" | "~^" | "^~" | "+:" | "-:" | "::" | "++" | "--" | "+=" | "-="
  | "*=" | "/=" | "%=" | "&=" | "|=" | "^=" | "->" | ":=" | ":/" | "@@" | ".*"
  | [+\-*/%<>=!~&|\^?:@#'$`]

%%

<YYINITIAL> {
  {WhiteSpace}              { return WHITE_SPACE; }

  {LineComment}             { return LINE_COMMENT; }
  {BlockComment}            { return BLOCK_COMMENT; }
  {String}                  { return STRING; }

  {BasedNumber}             { return NUMBER; }
  {UnbasedUnsized}          { return NUMBER; }
  {TimeLiteral}             { return NUMBER; }
  {Real}                    { return NUMBER; }
  {Decimal}                 { return NUMBER; }

  {Verilog2005Keyword}      { return KEYWORD; }
  {SystemVerilog2017Keyword} { return KEYWORD; }

  {SystemTask}              { return SYSTEM_TASK; }
  {Directive}               { return DIRECTIVE; }
  {Identifier}              { return IDENTIFIER; }
  {EscapedIdentifier}       { return ESCAPED_IDENTIFIER; }

  "("                       { return LPAREN; }
  ")"                       { return RPAREN; }
  "["                       { return LBRACKET; }
  "]"                       { return RBRACKET; }
  "{"                       { return LBRACE; }
  "}"                       { return RBRACE; }
  ";"                       { return SEMICOLON; }
  ","                       { return COMMA; }
  "."                       { return DOT; }
  {Operator}                { return OPERATOR; }
}

// Anything else, in any state. This rule makes it impossible for the lexer to fail.
[^]                         { return BAD_CHARACTER; }
