package dev.sal.hdl.lint

enum class LintSeverity { ERROR, WARNING }

/**
 * One diagnostic reported by an external lint tool.
 *
 * @property file the path exactly as the tool printed it
 * @property line 1-based line number
 * @property column 1-based column, or null when the tool didn't give one
 * @property code the tool's own warning ID, e.g. Verilator's `UNUSEDSIGNAL` or GHDL's `missing-wait`
 */
data class LintMessage(
    val file: String,
    val line: Int,
    val column: Int?,
    val severity: LintSeverity,
    val message: String,
    val code: String? = null,
)
