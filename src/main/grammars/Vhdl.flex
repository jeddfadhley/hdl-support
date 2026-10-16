// JFlex lexer for VHDL (IEEE 1076-2008, which also covers -1993 sources).
//
// Generated into build/generated/lexer by `./gradlew generateLexers`. Don't edit the generated Java.
//
// Rules for this lexer:
//  * VHDL is case-insensitive. %ignorecase covers the quoted strings (keywords, type names),
//    but JFlex doesn't apply it to character classes, so classes list both cases explicitly.
//  * It must never throw. The last rule is a catch-all that returns BAD_CHARACTER.
//  * Unterminated block comments run to EOF. Unterminated strings run to the end of the line.
//  * A tick (') is ambiguous: it can start a character literal ('1') or an attribute (clk'event)
//    or a qualified expression (unsigned'("01")). After an identifier, a type name, ')' or ']'
//    the lexer is in state AFTER_NAME, where a tick is never a character literal. Whitespace and
//    comments don't change the state. Every other token returns the lexer to YYINITIAL. All
//    state lives in yystate(), so the editor can restart the lexer at any token boundary.

package dev.sal.hdl.vhdl;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;
import static dev.sal.hdl.vhdl.VhdlTokenTypes.*;

%%

%public
%class _VhdlLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode
%ignorecase

%state AFTER_NAME

WhiteSpace = [ \t\f\r\n]+

LineComment = "--" [^\r\n]*
// VHDL-2008 block comment. Runs to EOF when there's no closing "*/".
BlockComment = "/*" ( [^*] | "*"+ [^*/] )* ( "*"+ "/" | "*"* )

// A doubled quote ("") inside a string is an escaped quote.
String = \" ( [^\"\r\n] | \"\" )* \"?
CharacterLiteral = "'" [^\r\n] "'"

// Bit string literals, IEEE 1076-2008 15.8: [length] base "digits"
// Bases: B O X D, plus UB UO UX SB SO SX.
BitString = [0-9]* ( [uUsS]? [bBoOxX] | [dD] ) \" [^\"\r\n]* \"?

Identifier = [a-zA-Z] [a-zA-Z0-9_]*
// Backslash-delimited; a doubled backslash inside is an escaped backslash.
ExtendedIdentifier = \\ ( [^\\\r\n] | \\\\ )* \\?

// ---- Numbers (IEEE 1076-2008 15.5) ----
Integer = [0-9] [0-9_]*
Exponent = [eE] [+-]? {Integer}
Real = {Integer} "." {Integer} {Exponent}?
DecimalLiteral = {Integer} {Exponent}?
BasedLiteral = {Integer} "#" [0-9a-fA-F_]+ ( "." [0-9a-fA-F_]+ )? "#" {Exponent}?

// ---- Keywords ----
// IEEE 1076-2008 15.10 reserved words. Matched case-insensitively.
// To add a keyword, append it to this list. Order doesn't matter.
Keyword =
    "abs" | "access" | "after" | "alias" | "all" | "and" | "architecture" | "array" | "assert"
  | "assume" | "assume_guarantee" | "attribute" | "begin" | "block" | "body" | "buffer" | "bus"
  | "case" | "component" | "configuration" | "constant" | "context" | "cover" | "default"
  | "disconnect" | "downto" | "else" | "elsif" | "end" | "entity" | "exit" | "fairness" | "file"
  | "for" | "force" | "function" | "generate" | "generic" | "group" | "guarded" | "if"
  | "impure" | "in" | "inertial" | "inout" | "is" | "label" | "library" | "linkage" | "literal"
  | "loop" | "map" | "mod" | "nand" | "new" | "next" | "nor" | "not" | "null" | "of" | "on"
  | "open" | "or" | "others" | "out" | "package" | "parameter" | "port" | "postponed"
  | "procedure" | "process" | "property" | "protected" | "pure" | "range" | "record"
  | "register" | "reject" | "release" | "rem" | "report" | "restrict" | "restrict_guarantee"
  | "return" | "rol" | "ror" | "select" | "sequence" | "severity" | "shared" | "signal" | "sla"
  | "sll" | "sra" | "srl" | "strong" | "subtype" | "then" | "to" | "transport" | "type"
  | "unaffected" | "units" | "until" | "use" | "variable" | "vmode" | "vprop" | "vunit" | "wait"
  | "when" | "while" | "with" | "xnor" | "xor"
// ---- End of keywords ----

// ---- Standard types ----
// Types from package STANDARD (IEEE 1076-2008 16.3), ieee.std_logic_1164, ieee.numeric_std,
// ieee.fixed_pkg and ieee.float_pkg. These are ordinary identifiers to the language, but
// they're highlighted separately.
StdType =
    "std_logic" | "std_ulogic" | "std_logic_vector" | "std_ulogic_vector" | "unsigned" | "signed"
  | "ufixed" | "sfixed" | "float" | "float32" | "float64" | "float128"
  | "bit" | "bit_vector" | "boolean" | "boolean_vector" | "integer" | "integer_vector"
  | "natural" | "positive" | "real" | "real_vector" | "time" | "time_vector" | "delay_length"
  | "character" | "string" | "severity_level" | "file_open_kind" | "file_open_status"
// ---- End of standard types ----

Operator =
    "**" | "=>" | ":=" | "/=" | ">=" | "<=" | "<>" | "??" | "?=" | "?/=" | "?<" | "?<=" | "?>"
  | "?>=" | "<<" | ">>"
  | [&*+\-/<=>|!?@:\^`]

%%

<YYINITIAL, AFTER_NAME> {
  // Whitespace and comments keep the current state, so "clk 'event" still lexes as an attribute.
  {WhiteSpace}           { return WHITE_SPACE; }
  {LineComment}          { return LINE_COMMENT; }
  {BlockComment}         { return BLOCK_COMMENT; }

  {String}               { yybegin(YYINITIAL); return STRING; }
  {BitString}            { yybegin(YYINITIAL); return BIT_STRING; }

  {BasedLiteral}         { yybegin(YYINITIAL); return NUMBER; }
  {Real}                 { yybegin(YYINITIAL); return NUMBER; }
  {DecimalLiteral}       { yybegin(YYINITIAL); return NUMBER; }

  {Keyword}              { yybegin(YYINITIAL); return KEYWORD; }
  {StdType}              { yybegin(AFTER_NAME); return STD_TYPE; }
  {Identifier}           { yybegin(AFTER_NAME); return IDENTIFIER; }
  {ExtendedIdentifier}   { yybegin(AFTER_NAME); return EXTENDED_IDENTIFIER; }

  "("                    { yybegin(YYINITIAL); return LPAREN; }
  ")"                    { yybegin(AFTER_NAME); return RPAREN; }
  "["                    { yybegin(YYINITIAL); return LBRACKET; }
  "]"                    { yybegin(AFTER_NAME); return RBRACKET; }
  ";"                    { yybegin(YYINITIAL); return SEMICOLON; }
  ","                    { yybegin(YYINITIAL); return COMMA; }
  "."                    { yybegin(YYINITIAL); return DOT; }
  {Operator}             { yybegin(YYINITIAL); return OPERATOR; }
}

<YYINITIAL> {
  {CharacterLiteral}     { return CHARACTER; }
  "'"                    { return OPERATOR; }
}

<AFTER_NAME> {
  // clk'event, sig'length, arr'range. An attribute can be followed by another tick
  // (t'base'left), so stay in AFTER_NAME.
  "'" {Identifier}       { return ATTRIBUTE; }
  // Qualified expression: unsigned'("0101")
  "'"                    { yybegin(YYINITIAL); return OPERATOR; }
}

// Anything else, in any state. This rule makes it impossible for the lexer to fail.
[^]                      { yybegin(YYINITIAL); return BAD_CHARACTER; }
