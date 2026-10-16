package dev.sal.hdl.lint

/**
 * Parses `ghdl -s` output. GHDL writes diagnostics like:
 *
 * ```
 * bad.vhd:7:23:error: missing ";" at end of object declaration
 * w.vhd:9:14:error: (found: 'end')
 * z.vhd:4:3:warning: infinite loop for this process without a wait statement [-Wmissing-wait]
 * ```
 *
 * Older GHDL versions leave out the `error:` prefix. A line is a warning if it contains "warning";
 * anything else is an error. A follow-up line in parentheses at the same position, such as
 * `(found: 'end')`, is joined onto the message before it. Source excerpts and caret lines are skipped.
 */
object GhdlOutputParser {
    private val DIAGNOSTIC = Regex("""^(.+?):(\d+):(\d+):\s*(.*)$""")
    private val SEVERITY_PREFIX = Regex("""^(warning|error|note)\s*:\s*""", RegexOption.IGNORE_CASE)
    private val WARNING_OPTION = Regex("""\s*\[-W([A-Za-z0-9_-]+)]\s*$""")

    fun parse(output: String): List<LintMessage> {
        val messages = mutableListOf<LintMessage>()
        for (line in output.lineSequence()) {
            val message = parseLine(line) ?: continue
            val previous = messages.lastOrNull()
            if (previous != null && message.message.startsWith("(") &&
                previous.file == message.file && previous.line == message.line && previous.column == message.column
            ) {
                messages[messages.lastIndex] = previous.copy(message = "${previous.message} ${message.message}")
            } else {
                messages += message
            }
        }
        return messages.distinct()
    }

    fun parseLine(line: String): LintMessage? {
        val match = DIAGNOSTIC.matchEntire(line.trimEnd()) ?: return null
        val (file, lineNumber, column, rest) = match.destructured
        val isWarning = rest.contains("warning", ignoreCase = true)
        var message = rest.replaceFirst(SEVERITY_PREFIX, "")
        val code = WARNING_OPTION.find(message)?.groupValues?.get(1)
        message = message.replaceFirst(WARNING_OPTION, "")
        return LintMessage(
            file = file,
            line = lineNumber.toInt(),
            column = column.toInt(),
            severity = if (isWarning) LintSeverity.WARNING else LintSeverity.ERROR,
            message = message.trim(),
            code = code,
        )
    }
}
