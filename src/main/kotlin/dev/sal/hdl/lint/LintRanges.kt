package dev.sal.hdl.lint

import com.intellij.openapi.util.TextRange

/** Works out which characters to underline for a lint message. */
object LintRanges {
    /**
     * @param text the whole document
     * @param lineStart offset of the first character of the reported line
     * @param lineEnd offset just past the last character of the line (before the line break)
     * @param column the tool's 1-based column, or null
     * @return the range to underline. It's empty only if the line is blank.
     */
    fun highlightRange(text: CharSequence, lineStart: Int, lineEnd: Int, column: Int?): TextRange {
        // Without a column, underline the line minus its leading and trailing whitespace.
        if (column == null) return trimmedLine(text, lineStart, lineEnd)

        var start = (lineStart + column - 1).coerceIn(lineStart, lineEnd)
        // Tools often point just past the end of the line ("missing ';'") or at whitespace.
        // Fall back to the last token before that point.
        if (start == lineEnd || text[start].isWhitespace()) {
            var previous = start - 1
            while (previous >= lineStart && text[previous].isWhitespace()) previous--
            if (previous < lineStart) return trimmedLine(text, lineStart, lineEnd)
            start = previous
            while (start > lineStart && sameTokenKind(text[start - 1], text[previous])) start--
        }

        // Extend to the end of the token: a run of word characters, or a run of punctuation.
        val kind = text[start]
        var end = start + 1
        while (end < lineEnd && sameTokenKind(text[end], kind)) end++
        return TextRange(start, end)
    }

    private fun trimmedLine(text: CharSequence, lineStart: Int, lineEnd: Int): TextRange {
        var start = lineStart
        var end = lineEnd
        while (start < end && text[start].isWhitespace()) start++
        while (end > start && text[end - 1].isWhitespace()) end--
        return TextRange(start, end)
    }

    private fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '_' || c == '$'

    private fun sameTokenKind(c: Char, kind: Char): Boolean = when {
        c.isWhitespace() -> false
        isWordChar(kind) -> isWordChar(c)
        else -> !isWordChar(c)
    }
}
