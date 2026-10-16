package dev.sal.hdl.lint

/**
 * Parses `verilator --lint-only` output. Verilator writes diagnostics like:
 *
 * ```
 * %Warning-UNUSEDSIGNAL: warn.v:6:10: Signal is not driven, nor used: 'spare'
 * %Error: counter.v:10:9: syntax error, unexpected else
 * ```
 *
 * The code (`-UNUSEDSIGNAL`) and the column are optional. Source excerpts, `... note:` lines and
 * summaries such as `%Error: Exiting due to 1 error(s)` have no `file:line:` part and are skipped.
 */
object VerilatorOutputParser {
    private val DIAGNOSTIC = Regex("""^%(Warning|Error)(?:-([A-Za-z0-9_]+))?: (.+?):(\d+):(?:(\d+):)? (.*)$""")

    fun parse(output: String): List<LintMessage> =
        output.lineSequence().mapNotNull(::parseLine).distinct().toList()

    fun parseLine(line: String): LintMessage? {
        val match = DIAGNOSTIC.matchEntire(line.trimEnd()) ?: return null
        val (kind, code, file, lineNumber, column, message) = match.destructured
        return LintMessage(
            file = file,
            line = lineNumber.toInt(),
            column = column.toIntOrNull(),
            severity = if (kind == "Error") LintSeverity.ERROR else LintSeverity.WARNING,
            message = message.trim(),
            code = code.ifEmpty { null },
        )
    }
}
