package dev.sal.hdl.yosys

import java.nio.file.Path

/**
 * Builds the Yosys script for "Show Synthesis Stats".
 *
 * Yosys strips quotes from file names, but not from other arguments: `hierarchy -libdir "a b"` and
 * `read_verilog -I "a b"` look for a directory whose name includes the quotes. So the script runs
 * in the file's directory and uses `-libdir .`, and include directories are passed unquoted.
 * Include directories containing whitespace can't be passed at all; see [unsupportedIncludeDirs].
 */
object YosysScript {
    /**
     * Reads [file], loads any missing submodules from files named after them in the same directory
     * (`hierarchy -libdir`, like Verilator's `-y`), synthesises [top] and prints its statistics.
     * Yosys must run with [file]'s directory as its working directory.
     *
     * @param synthCommand e.g. `synth` or `synth_ice40`; blank means `synth`
     */
    fun build(file: Path, top: String, includeDirs: List<String>, synthCommand: String): String {
        val includes = includeDirs.filterNot(::hasWhitespace).joinToString("") { " -I$it" }
        val synth = synthCommand.trim().ifEmpty { "synth" }
        return listOf(
            "read_verilog -sv$includes ${quote(file.toAbsolutePath().toString())}",
            "hierarchy -check -top $top -libdir .",
            "$synth -top $top",
            "stat",
        ).joinToString("; ")
    }

    /** The include directories that [build] leaves out because Yosys can't take them. */
    fun unsupportedIncludeDirs(includeDirs: List<String>): List<String> = includeDirs.filter(::hasWhitespace)

    private fun hasWhitespace(text: String): Boolean = text.any { it.isWhitespace() }

    /** File names may be quoted, which allows spaces. */
    private fun quote(text: String): String = "\"" + text.replace("\"", "\\\"") + "\""
}
